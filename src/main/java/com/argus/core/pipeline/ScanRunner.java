package com.argus.core.pipeline;

import com.argus.core.api.CrtshClient;
import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import com.argus.core.model.ScanSummary;
import com.argus.core.model.Target;
import com.argus.core.scanner.PortList;
import com.argus.db.ApiCacheDAO;
import com.argus.db.Database;
import com.argus.db.ScanDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * One scan run end to end: crtsh-enum → dns-resolve → port-scan →
 * banner-grab → collector, then the
 * results go to the db-writer in one insertScanResults transaction and the
 * event bus reports lifecycle (CORE.md, THREAD.md). UI-free — the controller
 * owns the thread that calls {@link #run()} and the FX hop for events.
 */
public final class ScanRunner implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ScanRunner.class);
    private static final int DNS_WORKERS = 50;
    private static final int BANNER_WORKERS = 16;
    private static final long RUN_CAP_SECONDS = 3600;

    private final Target target;
    private final long operatorId;
    private final EventBus events;
    private final CancellationToken token = new CancellationToken();
    private final BlockingQueue<Object> subdomains = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> hosts = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> openPorts = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> results = new LinkedBlockingQueue<>();
    private final List<Host> collected = new CopyOnWriteArrayList<>();
    private final List<PortResult> scanned = new CopyOnWriteArrayList<>();
    private final Pipeline pipeline;
    private final ScanDAO scanDao;
    private final ExecutorService dbWriter = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "db-writer");
        t.setDaemon(true);
        return t;
    });

    public ScanRunner(Target target, long operatorId, EventBus events) {
        this(target, operatorId, events,
                new CrtshClient(new ApiCacheDAO(Database.inUserHome())),
                DnsResolveModule.SYSTEM_RESOLVER,
                Database.inUserHome(),
                PortList.forProfile(target.profile()));
    }

    /** Test seam: injected client, resolver, database and port list keep tests offline. */
    ScanRunner(Target target, long operatorId, EventBus events,
               CrtshClient client, Function<String, Optional<String>> resolver, Database db,
               List<Integer> ports) {
        this.target = target;
        this.operatorId = operatorId;
        this.events = events;
        this.scanDao = new ScanDAO(db);

        TargetContext ctx = TargetContext.of(target);
        CrtshEnumModule enumModule = new CrtshEnumModule(client, subdomains, events, token);
        DnsResolveModule dns = new DnsResolveModule(subdomains, hosts, events, token,
                DNS_WORKERS, resolver);
        PortScanModule portScan = new PortScanModule(hosts, openPorts, events, token, ports);
        BannerGrabModule banner = new BannerGrabModule(openPorts, results, token,
                BANNER_WORKERS, resolver);
        Pump collect = new Pump(results, null, token, 1);

        Stage enumStage = Stage.runner(Stage.executor("stage-enum", 1), 1,
                () -> {
                    if (enumModule.supports(ctx)) {
                        enumModule.execute(ctx);
                    }
                }, subdomains);
        Stage dnsStage = Stage.runner(Stage.executor("stage-dns", DNS_WORKERS), DNS_WORKERS,
                () -> {
                    if (dns.supports(ctx)) {
                        dns.execute(ctx);
                    }
                }, subdomains, hosts);
        Stage portStage = Stage.runner(Stage.executor("stage-portscan", 1), 1,
                () -> {
                    if (portScan.supports(ctx)) {
                        portScan.execute(ctx);
                    }
                }, hosts, openPorts);
        Stage bannerStage = Stage.runner(Stage.executor("stage-banner", BANNER_WORKERS),
                BANNER_WORKERS,
                () -> {
                    if (banner.supports(ctx)) {
                        banner.execute(ctx);
                    }
                }, openPorts, results);
        Stage collectStage = Stage.runner(Stage.executor("stage-collect", 1), 1,
                () -> collect.run(item -> {
                    Object payload = Pump.payload(item);
                    if (payload instanceof PortResult p) {
                        scanned.add(p);
                    } else {
                        collected.add((Host) payload);
                    }
                }), results);

        this.pipeline = new Pipeline(token, enumStage, dnsStage, portStage, bannerStage,
                collectStage);
    }

    /** Blocking: run it on a worker thread, not the FX thread. */
    public void run() {
        Instant startedAt = Instant.now();
        events.publish(new ScanEvent.ScanStarted(target.domain()));
        ScanSummary.Status status;
        try {
            pipeline.start();
            boolean drained = pipeline.await(RUN_CAP_SECONDS);
            if (!drained && !token.isCancelled()) {
                pipeline.cancel(); // run cap hit: stop the stuck stage, keep partials
                status = ScanSummary.Status.FAILED;
            } else if (token.isCancelled()) {
                status = ScanSummary.Status.CANCELLED;
            } else {
                status = ScanSummary.Status.COMPLETED;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pipeline.cancel();
            status = ScanSummary.Status.CANCELLED;
        } catch (RuntimeException e) {
            LOG.warn("scan failed: {}", e.getMessage());
            status = ScanSummary.Status.FAILED;
        }

        List<Host> snapshot = List.copyOf(collected);
        List<PortResult> ports = List.copyOf(scanned);
        ScanSummary summary = new ScanSummary(null, operatorId, target.domain(),
                target.profile(), status, startedAt, Instant.now());
        dbWriter.execute(() -> {
            try {
                scanDao.insertScanResults(summary, snapshot, ports, List.of());
            } catch (SQLException e) {
                LOG.warn("scan results not persisted: {}", e.getMessage());
            }
        });
        // persist is queued before the event, so close() from the handler can't drop it
        events.publish(new ScanEvent.ScanFinished(status.name(), snapshot.size() + " hosts"));
    }

    /** Cancel button path: token, interrupts, queue drain (THREAD.md). */
    public void cancel() {
        pipeline.cancel();
    }

    @Override
    public void close() {
        pipeline.close();
        dbWriter.shutdown();
        try {
            if (!dbWriter.awaitTermination(5, TimeUnit.SECONDS)) {
                dbWriter.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbWriter.shutdownNow();
            Thread.currentThread().interrupt();
        }
        events.close();
    }
}
