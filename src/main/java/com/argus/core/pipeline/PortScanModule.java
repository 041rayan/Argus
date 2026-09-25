package com.argus.core.pipeline;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Stage 3 — port-scan (CORE.md): one virtual thread per (host, port) behind a
 * 500-permit semaphore, plain socket connect with a 1500 ms timeout. Scope is
 * checked before every connect. Open ports are queued as PortResult and the
 * Host itself is forwarded, so downstream stages see both payload types.
 */
public final class PortScanModule implements ArgusModule {

    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int MAX_PARALLEL = 500;

    private final BlockingQueue<Object> out;
    private final EventBus events;
    private final CancellationToken token;
    private final List<Integer> ports;
    private final Semaphore permits = new Semaphore(MAX_PARALLEL);
    private final ExecutorService scanner = Executors.newVirtualThreadPerTaskExecutor();
    private final Pump pump;

    public PortScanModule(BlockingQueue<Object> in, BlockingQueue<Object> out,
                          EventBus events, CancellationToken token, List<Integer> ports) {
        this.out = out;
        this.events = events;
        this.token = token;
        this.ports = List.copyOf(ports);
        this.pump = new Pump(in, out, token, 1);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor("port-scan", ScanPhase.RECON, RiskLevel.ACTIVE);
    }

    @Override
    public boolean supports(TargetContext ctx) {
        return ctx != null;
    }

    @Override
    public void execute(TargetContext ctx) {
        pump.run(item -> {
            Host host = Pump.payload(item);
            if (!scannable(ctx, host)) {
                out.add(host);
                return;
            }
            List<Future<?>> probes = new ArrayList<>(ports.size());
            for (int port : ports) {
                probes.add(scanner.submit(() -> probe(host, port)));
            }
            for (Future<?> probe : probes) {
                try {
                    probe.get(); // InterruptedException from pump worker cancels the pump
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    // a failed probe means closed, nothing to record
                }
            }
            out.add(host);
        });
    }

    /** Scope before every network action (CORE.md). */
    private boolean scannable(TargetContext ctx, Host host) {
        return host.alive() && !host.ip().isBlank()
                && (ctx.inScope(host.subdomain()) || ctx.inScope(host.ip()));
    }

    private void probe(Host host, int port) {
        if (token.isCancelled()) {
            return;
        }
        try {
            permits.acquire(); // virtual threads park here when 500 are in flight
            try {
                if (token.isCancelled()) {
                    return;
                }
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host.ip(), port), CONNECT_TIMEOUT_MS);
                    out.add(new PortResult(host.subdomain(), port, "tcp", "", "", "", "", true));
                    events.publish(new ScanEvent.PortFound(host.subdomain(), port, "", ""));
                }
            } finally {
                permits.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // THREAD.md: exit cooperatively
        } catch (IOException e) {
            // refused / timed out / unreachable — port stays closed
        }
    }
}
