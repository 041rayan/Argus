package com.argus.core.api;

import com.argus.core.api.dto.IpApiResult;
import com.argus.core.json.Json;
import com.argus.db.ApiCacheDAO;
import com.argus.db.Database;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: fixture body served by a mock batch endpoint, temp database cache. */
class IpApiClientTest {

    private static final String IP_A = "1.1.1.1";
    private static final String IP_B = "8.8.8.8";

    @TempDir
    Path tmp;

    private Database db;
    private ApiCacheDAO cache;
    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int statusCode = 200;
    private volatile String responseBody = "";

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(tmp.resolve("ipapi-test.db"));
        cache = new ApiCacheDAO(db);
        responseBody = fixture();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/batch", exchange -> {
            requests.incrementAndGet();
            byte[] request = exchange.getRequestBody().readAllBytes();
            String body = statusCode == 200
                    ? onlyRequested(new String(request, StandardCharsets.UTF_8))
                    : responseBody;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Real ip-api answers only the IPs that were asked for. */
    private static String onlyRequested(String requestBody) throws IOException {
        Set<String> asked = new HashSet<>();
        for (JsonNode ip : Json.MAPPER.readTree(requestBody)) {
            asked.add(ip.asText());
        }
        ArrayNode wanted = Json.MAPPER.createArrayNode();
        for (JsonNode row : Json.MAPPER.readTree(fixture())) {
            if (asked.contains(row.get("query").asText())) {
                wanted.add(row);
            }
        }
        return wanted.toString();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void batchFetchParsesMapsByQueryAndCaches() throws Exception {
        Map<String, IpApiResult> got =
                new IpApiClient(cache, baseUrl).lookup(List.of(IP_A, IP_B, "192.168.1.1"));

        assertEquals(1, requests.get(), "one batch for three unseen IPs");
        assertEquals(2, got.size(), "the fail row carries nothing useful and is skipped");
        assertEquals("Australia", got.get(IP_A).country());
        assertNotNull(got.get(IP_A).as());
        assertNull(got.get("192.168.1.1"), "fail rows are not returned");

        assertTrue(cache.find(IpApiClient.PROVIDER, IP_A).isPresent(), "success rows cached 7 d");
        assertEquals(0, countFailCached(), "fail rows not cached");
    }

    @Test
    void cachedIpsAreNeverQueriedAgain() throws Exception {
        cache.put(IpApiClient.PROVIDER, IP_A,
                "{\"status\":\"success\",\"country\":\"Cachedland\",\"as\":\"AS1\","
                        + "\"org\":\"O\",\"isp\":\"I\",\"query\":\"" + IP_A + "\"}",
                Duration.ofDays(7));

        Map<String, IpApiResult> got =
                new IpApiClient(cache, baseUrl).lookup(List.of(IP_A, IP_B));

        assertEquals(1, requests.get(), "only the unseen IP reaches the network");
        assertEquals("Cachedland", got.get(IP_A).country(), "cache answer wins");
        assertEquals("United States", got.get(IP_B).country());
    }

    @Test
    void rateLimitDisablesProviderButKeepsCachedResults() throws Exception {
        cache.put(IpApiClient.PROVIDER, IP_A,
                "{\"status\":\"success\",\"country\":\"Cachedland\",\"as\":\"AS1\","
                        + "\"org\":\"O\",\"isp\":\"I\",\"query\":\"" + IP_A + "\"}",
                Duration.ofDays(7));
        statusCode = 429;
        responseBody = "rate limited";

        IpApiClient client = new IpApiClient(cache, baseUrl);
        Map<String, IpApiResult> got = client.lookup(List.of(IP_A, IP_B));

        assertTrue(client.degraded(), "429 disables the provider for the session");
        assertEquals(429, client.degradedStatus());
        assertEquals(1, got.size(), "cached answer survives the degrade");
        assertEquals("Cachedland", got.get(IP_A).country());
        assertEquals(1, requests.get(), "only the unseen IP was attempted");
    }

    @Test
    void serverErrorRetriesOnceThenDegrades() {
        statusCode = 500;
        responseBody = "boom";

        IpApiClient client = new IpApiClient(cache, baseUrl);
        Map<String, IpApiResult> got = client.lookup(List.of(IP_A));

        assertEquals(2, requests.get(), "exactly one retry (API.md)");
        assertTrue(client.degraded());
        assertTrue(got.isEmpty(), "degraded answer is partial, not an exception");
    }

    @Test
    void deadEndpointDegradesGracefully() {
        IpApiClient client = new IpApiClient(cache, "http://127.0.0.1:1");
        Map<String, IpApiResult> got = client.lookup(List.of(IP_A));

        assertTrue(client.degraded());
        assertTrue(got.isEmpty());
        assertFalse(got.containsKey(IP_A));
    }

    private int countFailCached() throws Exception {
        // the fail row's IP must not be in the cache
        return cache.find(IpApiClient.PROVIDER, "192.168.1.1").isPresent() ? 1 : 0;
    }

    private static String fixture() throws IOException {
        try (InputStream in = IpApiClientTest.class.getResourceAsStream("/ipapi-batch.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
