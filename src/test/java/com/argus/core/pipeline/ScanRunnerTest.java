package com.argus.core.pipeline;

import com.argus.core.api.CrtshClient;
import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Target;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline end-to-end: mock server fixture + fake resolver, temp database. */
class ScanRunnerTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private String baseUrl;
    private Database db;
    private EventBus events;
    private final AtomicInteger hostEvents = new AtomicInteger();
    private final AtomicReference<ScanEvent.ScanFinished> finished = new AtomicReference<>();
    private final CountDownLatch finishedLatch = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        db = new Database(tmp.resolve("argus-test.db"));
        try (Connection c = db.connect();
             var p = c.prepareStatement(
                     "INSERT INTO operator (username, auth_salt, auth_hash, vault_salt, created_at) "
                             + "VALUES ('tester', X'00', X'00', X'00', '2026-09-25T00:00:00Z')")) {
            p.executeUpdate();
        }
        events = new EventBus();
        events.subscribe(e -> {
            if (e instanceof ScanEvent.HostFound) {
                hostEvents.incrementAndGet();
            } else if (e instanceof ScanEvent.ScanFinished f) {
                finished.set(f);
                finishedLatch.countDown();
            }
        });
    }

    @AfterEach
    void tearDown() {
        events.close();
        server.stop(0);
    }

    @Test
    void fullRunEmitsHostsPersistsScanAndFinishes() throws Exception {
        Function<String, Optional<String>> resolver =
                h -> h.equals("www.example.com") ? Optional.of("93.184.216.34") : Optional.empty();
        Target target = new Target(null, "Lab", "example.com", List.of(), "quick", Instant.now());

        ScanRunner runner = new ScanRunner(target, 1, events,
                new CrtshClient(new ApiCacheDAO(db), baseUrl), resolver, db);
        try (runner) {
            runner.run();
            assertTrue(finishedLatch.await(5, TimeUnit.SECONDS), "ScanFinished never arrived");
        }
        assertEquals("COMPLETED", finished.get().status());
        assertTrue(hostEvents.get() > 100, "all fixture subdomains resolved, got " + hostEvents.get());

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + tmp.resolve("argus-test.db"))) {
            assertEquals(1, count(c, "scan"));
            assertEquals("COMPLETED", scalar(c, "SELECT status FROM scan"));
            assertEquals("example.com", scalar(c, "SELECT target FROM scan"));
            assertEquals(hostEvents.get(), count(c, "host"),
                    "one host row per resolved subdomain");
            assertEquals(1, count(c, "host WHERE is_alive = 1"));
            assertEquals(0, count(c, "port"));
            assertEquals(0, count(c, "finding"));
        }
    }

    private static int count(Connection c, String table) throws java.sql.SQLException {
        try (var s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String scalar(Connection c, String sql) throws java.sql.SQLException {
        try (var s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static String fixture() throws IOException {
        try (InputStream in = ScanRunnerTest.class.getResourceAsStream("/crtsh.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
