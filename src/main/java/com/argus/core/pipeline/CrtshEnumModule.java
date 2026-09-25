package com.argus.core.pipeline;

import com.argus.core.api.CrtshClient;
import com.argus.core.api.ProviderDegradedException;
import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Stage 1 — crtsh-enum (CORE.md pipeline): source module. Runs once, emits
 * in-scope subdomains downstream, then the poison pill. A degraded provider
 * degrades the stage, not the scan (API.md).
 */
public final class CrtshEnumModule implements ArgusModule {

    private final CrtshClient client;
    private final BlockingQueue<Object> out;
    private final EventBus events;
    private final CancellationToken token;

    public CrtshEnumModule(CrtshClient client, BlockingQueue<Object> out,
                           EventBus events, CancellationToken token) {
        this.client = client;
        this.out = out;
        this.events = events;
        this.token = token;
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor("crtsh-enum", ScanPhase.RECON, RiskLevel.PASSIVE);
    }

    @Override
    public boolean supports(TargetContext ctx) {
        return ctx.domain() != null && !ctx.domain().isBlank();
    }

    @Override
    public void execute(TargetContext ctx) throws InterruptedException {
        try {
            List<String> subdomains;
            try {
                subdomains = client.subdomains(ctx.domain());
            } catch (ProviderDegradedException e) {
                events.publish(new ScanEvent.ProviderDegraded("crt.name", e.status()));
                return;
            } catch (IOException e) {
                events.publish(new ScanEvent.ProviderDegraded("crt.name", -1));
                return;
            }
            int emitted = 0;
            for (String sub : subdomains) {
                if (token.isCancelled()) {
                    break;
                }
                if (ctx.inScope(sub)) {
                    out.add(sub);
                    emitted++;
                }
            }
            events.publish(new ScanEvent.StageProgress(descriptor().name(), emitted));
        } finally {
            out.add(Pump.POISON); // the scan continues even when the stage failed
        }
    }
}
