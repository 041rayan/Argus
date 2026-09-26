package com.argus.core.api;

import com.argus.core.api.dto.KevCatalog;
import com.argus.db.ApiCacheDAO;
import com.argus.db.Database;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: mock server for the feed, temp database for the cache, bundled fallback. */
class KevClientTest {

    private static final String PROVIDER = "cisa-kev";
    private static final String RESOURCE = "known_exploited_vulnerabilities.json";
    private static final String TINY =
            "{\"title\":\"tiny\",\"catalogVersion\":\"1\",\"vulnerabilities\":[]}";

    @TempDir
    Path tmp;

    private Database db;
    private ApiCacheDAO cache;
    private HttpServer server;
    private String feed;

    @BeforeEach
    void setUp() throws Exception {
        db = new Database(tmp.resolve("kev-test.db"));
        cache = new ApiCacheDAO(db);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/feed", exchange -> {
            byte[] body = TINY.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        feed = "http://127.0.0.1:" + server.getAddress().getPort() + "/feed";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void cacheHitServesCachedCatalogWithoutFetching() throws Exception {
        String cached = "{\"title\":\"from-cache\",\"catalogVersion\":\"1\","
                + "\"vulnerabilities\":[]}";
        cache.put(PROVIDER, RESOURCE, cached, Duration.ofDays(7));
        // dead feed URL: only the cache path can answer "from-cache"
        KevCatalog catalog = new KevClient(cache, HttpClient.newHttpClient(),
                "http://127.0.0.1:1/unreachable").catalog();
        assertEquals("from-cache", catalog.title());
    }

    @Test
    void liveFeedParsedAndCached() throws Exception {
        KevCatalog catalog = new KevClient(cache, HttpClient.newHttpClient(), feed).catalog();
        assertEquals("tiny", catalog.title());
        Optional<String> hit = cache.find(PROVIDER, RESOURCE);
        assertTrue(hit.isPresent(), "feed body cached for 7 days");
        assertEquals(TINY, hit.get());
    }

    @Test
    void httpErrorFromFeedFallsBackToBundledSnapshot() {
        KevCatalog catalog = new KevClient(cache, HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/missing").catalog();
        assertTrue(catalog.title().startsWith("CISA Catalog"),
                "bundled snapshot served, got: " + catalog.title());
        assertTrue(catalog.vulnerabilities().size() > 1000,
                "bundled catalog is the full KEV feed");
    }

    @Test
    void corruptCacheFallsThroughToFeed() throws Exception {
        cache.put(PROVIDER, RESOURCE, "{not json", Duration.ofDays(7));
        KevCatalog catalog = new KevClient(cache, HttpClient.newHttpClient(), feed).catalog();
        assertEquals("tiny", catalog.title(), "unparseable cache entry skipped");
    }
}
