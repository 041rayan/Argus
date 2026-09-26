package com.argus.core.api;

import com.argus.core.api.dto.VtIpResult;
import com.argus.core.concurrency.TokenBucket;
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
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: fixture body served by a mock VT endpoint, temp database cache. */
class VtClientTest {

    private static final String KEY = "test-key-123";
    private static final String IP = "8.8.8.8";

    @TempDir
    Path tmp;

    private Database db;
    private ApiCacheDAO cache;
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int statusCode = 200;
    private volatile String responseBody = "";
    private final AtomicReference<String> seenApiKey = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(tmp.resolve("vt-test.db"));
        cache = new ApiCacheDAO(db);
        responseBody = fixture();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/ip_addresses/", exchange -> {
            requests.incrementAndGet();
            seenApiKey.set(exchange.getRequestHeaders().getFirst("x-apikey"));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private VtClient client() {
        return new VtClient(cache, KEY.getBytes(StandardCharsets.UTF_8), baseUrl,
                new TokenBucket(Duration.ofSeconds(10)));
    }

    @Test
    void fetchParsesVerdictSendsKeyAndCaches() throws Exception {
        Optional<VtIpResult> got = client().lookup(IP);

        assertTrue(got.isPresent());
        assertEquals(0, got.get().malicious(), "fixture: 8.8.8.8 is clean");
        assertEquals(0, got.get().suspicious());
        assertEquals(KEY, seenApiKey.get(), "key goes in the x-apikey header");
        assertTrue(cache.find(VtClient.PROVIDER, IP).isPresent(), "24 h cache row written");
    }

    @Test
    void secondLookupServedFromCache() throws Exception {
        VtClient client = client();
        client.lookup(IP);
        server.stop(0); // network gone: only the cache can answer now

        Optional<VtIpResult> again = new VtClient(cache, KEY.getBytes(StandardCharsets.UTF_8),
                baseUrl, new TokenBucket(Duration.ofSeconds(10))).lookup(IP);

        assertTrue(again.isPresent(), "24 h cache covers the repeat");
        assertEquals(0, again.get().malicious());
    }

    @Test
    void noDataIsNotCached() throws Exception {
        statusCode = 404;
        VtClient client = client();

        assertTrue(client.lookup(IP).isEmpty());
        assertTrue(cache.find(VtClient.PROVIDER, IP).isEmpty(), "404 must not poison the cache");

        statusCode = 200;
        assertTrue(client.lookup(IP).isPresent(), "next attempt refetches");
    }

    @Test
    void rateLimitDisablesProviderButKeepsCache() throws Exception {
        cache.put(VtClient.PROVIDER, "9.9.9.9",
                "{\"malicious\":3,\"suspicious\":1}", Duration.ofHours(24));
        statusCode = 429;
        responseBody = "quota exceeded";

        VtClient client = client();
        assertTrue(client.lookup(IP).isEmpty(), "missing IP dies with the degrade");
        assertTrue(client.degraded());
        assertEquals(429, client.degradedStatus());

        Optional<VtIpResult> cached = client.lookup("9.9.9.9");
        assertTrue(cached.isPresent(), "cached answer survives the degrade");
        assertEquals(3, cached.get().malicious());
    }

    @Test
    void serverErrorRetriesOnceThenDegrades() {
        statusCode = 500;
        responseBody = "boom";

        VtClient client = client();
        assertTrue(client.lookup(IP).isEmpty());
        assertEquals(2, requests.get(), "exactly one retry (API.md)");
        assertTrue(client.degraded());
    }

    @Test
    void deadEndpointDegradesGracefully() {
        VtClient client = new VtClient(cache, KEY.getBytes(StandardCharsets.UTF_8),
                "http://127.0.0.1:1", new TokenBucket(Duration.ofSeconds(10)));
        assertTrue(client.lookup(IP).isEmpty());
        assertTrue(client.degraded());
    }

    @Test
    void degradedProviderStillAnswersFromCacheWithoutTouchingTheNetwork() throws Exception {
        cache.put(VtClient.PROVIDER, IP, "{\"malicious\":0,\"suspicious\":0}",
                Duration.ofHours(24));
        VtClient client = client();

        assertTrue(client.lookup(IP).isPresent());
        assertEquals(0, requests.get(), "cache hit never reaches the network");
        assertFalse(client.degraded());
    }

    private static String fixture() throws IOException {
        try (InputStream in = VtClientTest.class.getResourceAsStream("/vt-ip.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
