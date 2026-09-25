package com.argus.core.pipeline;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Host;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;

/**
 * Stage 2 — dns-resolve (CORE.md pipeline): pulls subdomains until the pill,
 * resolves them on the stage's worker pool (THREAD.md: fixed pool of 50),
 * emits Host rows downstream. Scope is checked before the resolution action;
 * dead hosts are still emitted with alive=false so the table records them.
 */
public final class DnsResolveModule implements ArgusModule {

    /** Injectable so tests stay offline. */
    public static final Function<String, Optional<String>> SYSTEM_RESOLVER = host -> {
        try {
            return Optional.of(InetAddress.getByName(host).getHostAddress());
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
    };

    private final BlockingQueue<Object> out;
    private final EventBus events;
    private final Function<String, Optional<String>> resolver;
    private final Pump pump;

    public DnsResolveModule(BlockingQueue<Object> in, BlockingQueue<Object> out,
                            EventBus events, CancellationToken token,
                            int workers, Function<String, Optional<String>> resolver) {
        this.out = out;
        this.events = events;
        this.resolver = resolver;
        this.pump = new Pump(in, out, token, workers);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor("dns-resolve", ScanPhase.RECON, RiskLevel.PASSIVE);
    }

    @Override
    public boolean supports(TargetContext ctx) {
        return ctx != null;
    }

    @Override
    public void execute(TargetContext ctx) {
        pump.run(item -> {
            String sub = Pump.payload(item);
            if (!ctx.inScope(sub)) {
                return;
            }
            Optional<String> ip = resolver.apply(sub);
            out.add(new Host(null, -1, sub, ip.orElse(""), ip.isPresent(), "", "", ""));
            events.publish(new ScanEvent.HostFound(sub, ip.orElse(""), ip.isPresent()));
        });
    }
}
