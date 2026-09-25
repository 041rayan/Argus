package com.argus.core.api;

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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API.md quota laws: fixtures and a local mock server only — never live.
 */
class CrtshClientTest {

    @TempDir
    Path tmp;

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("[]");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private CrtshClient client() throws Exception {
        ApiCacheDAO cache = new ApiCacheDAO(new Database(tmp.resolve("argus-test.db")));
        return new CrtshClient(cache, baseUrl);
    }

    private static String fixture() throws IOException {
        try (InputStream in = CrtshClientTest.class.getResourceAsStream("/crtsh.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void fixtureParsesAndSecondCallHitsCache() throws Exception {
        body.set(fixture());
        List<String> first = client().subdomains("example.com");

        assertTrue(first.size() > 100, "fixture has 100+ entries, got " + first.size());
        assertTrue(first.contains("blog.example.com"));
        assertEquals(1, hits.get());

        List<String> second = client().subdomains("example.com"); // fresh client, same cache row
        assertTrue(second.contains("blog.example.com"));
        assertEquals(1, hits.get(), "second call must be served from api_cache");
    }

    @Test
    void notFoundIsNoDataWithoutRetry() throws Exception {
        status.set(404);
        assertTrue(client().subdomains("nothing.example").isEmpty());
        assertEquals(1, hits.get());
    }

    @Test
    void rateLimitDisablesProviderForSession() throws Exception {
        status.set(429);
        CrtshClient c = client();

        ProviderDegradedException e = assertThrows(ProviderDegradedException.class,
                () -> c.subdomains("example.com"));
        assertEquals(429, e.status());
        assertTrue(c.degraded());

        assertThrows(ProviderDegradedException.class, () -> c.subdomains("example.com"));
        assertEquals(1, hits.get(), "disabled provider must not call out again");
    }

    @Test
    void serverErrorRetriesOnceThenDegrades() throws Exception {
        status.set(500);
        body.set("oops");
        ProviderDegradedException e = assertThrows(ProviderDegradedException.class,
                () -> client().subdomains("example.com"));
        assertEquals(500, e.status());
        assertEquals(2, hits.get(), "5xx retries exactly once");
    }
}
