package com.argus.core.pipeline;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.json.Json;
import com.argus.core.kev.KevMatcher;
import com.argus.core.model.Finding;
import com.argus.core.model.KevMatch;
import com.argus.core.model.PortResult;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Stage 5 — kev-analyze (CORE.md): every open port's banner and title go
 * through the matcher; each verdict becomes a KEV_MATCH Finding carried
 * downstream beside its PortResult. Pure in-memory analysis — no network, no
 * scope check needed, nothing to cancel mid-action.
 */
public final class KevAnalyzeModule implements ArgusModule {

    /** One finding detail shape (JSON.md points at CORE.md for these). */
    public record Detail(String host, int port, String cve, String name, String product,
                         String confidence, boolean ransomware) {
    }

    private final BlockingQueue<Object> out;
    private final EventBus events;
    private final KevMatcher matcher;
    private final Pump pump;

    public KevAnalyzeModule(BlockingQueue<Object> in, BlockingQueue<Object> out,
                            EventBus events, CancellationToken token,
                            KevMatcher matcher) {
        this.out = out;
        this.events = events;
        this.matcher = matcher;
        this.pump = new Pump(in, out, token, 1);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor("kev-analyze", ScanPhase.RECON, RiskLevel.PASSIVE);
    }

    @Override
    public boolean supports(TargetContext ctx) {
        return ctx != null;
    }

    @Override
    public void execute(TargetContext ctx) {
        pump.run(item -> {
            if (!(item instanceof PortResult port)) {
                out.add(item); // Host rows pass through untouched
                return;
            }
            out.add(port);
            for (KevMatch match : verdicts(port)) {
                Finding finding = finding(port, match);
                out.add(finding);
                events.publish(new ScanEvent.FindingEmitted(finding.type(), finding.severity()));
            }
        });
    }

    /** Banner first, title second; deduped so one CVE never fires twice. */
    private List<KevMatch> verdicts(PortResult port) {
        Set<KevMatch> found = new LinkedHashSet<>(matcher.match(port.banner()));
        found.addAll(matcher.match(port.title()));
        return List.copyOf(found);
    }

    private Finding finding(PortResult port, KevMatch match) {
        String severity = match.confidence() == KevMatch.Confidence.CONFIRMED
                ? "HIGH" : "MEDIUM"; // decision from issue #10 Q1
        Detail detail = new Detail(port.host(), port.port(), match.cve(), match.name(),
                match.product(), match.confidence().name(), match.ransomware());
        String json;
        try {
            json = Json.MAPPER.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("finding detail not serializable", e);
        }
        return new Finding(0, null, descriptor().name(), "KEV_MATCH", severity, json);
    }
}
