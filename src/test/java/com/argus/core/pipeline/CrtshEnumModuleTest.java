package com.argus.core.pipeline;

import com.argus.core.api.CrtshClient;
import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.db.ApiCacheDAO;
import com.argus.db.Database;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: mock server fixtures only (API.md quota laws). */
class CrtshEnumModuleTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("[]");
    private EventBus events;
    private CancellationToken token;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        events = new EventBus();
        token = new CancellationToken();
    }

    @AfterEach
    void tearDown() {
        events.close();
        server.stop(0);
    }

    private CrtshEnumModule module(BlockingQueue<Object> out) {
        ApiCacheDAO cache = new ApiCacheDAO(new Database(tmp.resolve("argus-test.db")));
        return new CrtshEnumModule(new CrtshClient(cache, baseUrl), out, events, token);
    }

    @Test
    void emitsInScopeSubdomainsThenPill() throws Exception {
        body.set(fixture());
        CountDownLatch progress = new CountDownLatch(1);
        AtomicReference<ScanEvent> last = new AtomicReference<>();
        events.subscribe(e -> {
            last.set(e);
            progress.countDown();
        });

        BlockingQueue<Object> out = new LinkedBlockingQueue<>();
        module(out).execute(new TargetContext("example.com", List.of()));

        List<Object> items = new ArrayList<>(out);
        assertTrue(items.size() > 101, "100+ subdomains then pill, got " + items.size());
        assertTrue(items.stream().limit(items.size() - 1).allMatch(s -> s instanceof String
                && (((String) s).equals("example.com") || ((String) s).endsWith(".example.com"))),
                "scope filter");
        assertEquals(Pump.POISON, items.get(items.size() - 1));

        assertTrue(progress.await(2, TimeUnit.SECONDS), "StageProgress missing");
        assertInstanceOf(ScanEvent.StageProgress.class, last.get());
    }

    @Test
    void degradedProviderStillForwardsPill() throws Exception {
        status.set(429);
        CountDownLatch degraded = new CountDownLatch(1);
        AtomicReference<ScanEvent> last = new AtomicReference<>();
        events.subscribe(e -> {
            last.set(e);
            degraded.countDown();
        });

        BlockingQueue<Object> out = new LinkedBlockingQueue<>();
        module(out).execute(new TargetContext("example.com", List.of()));

        assertEquals(1, out.size(), "only the pill");
        assertEquals(Pump.POISON, out.peek());
        assertTrue(degraded.await(2, TimeUnit.SECONDS), "ProviderDegraded missing");
        assertInstanceOf(ScanEvent.ProviderDegraded.class, last.get());
    }

    private static String fixture() throws IOException {
        try (InputStream in = CrtshEnumModuleTest.class.getResourceAsStream("/crtsh.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
