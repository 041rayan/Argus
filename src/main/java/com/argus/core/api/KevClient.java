package com.argus.core.api;

import com.argus.core.api.dto.KevCatalog;
import com.argus.core.json.Json;
import com.argus.db.ApiCacheDAO;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

/**
 * CISA KEV catalog (API.md): api_cache first, then the live feed with the
 * standard request flow, then the bundled snapshot. The snapshot always
 * parses, so a dead feed can't kill KEV matching — degraded means "older
 * catalog", not "no catalog".
 */
public final class KevClient {

    private static final Logger LOG = LoggerFactory.getLogger(KevClient.class);
    private static final String PROVIDER = "cisa-kev";
    private static final String RESOURCE = "known_exploited_vulnerabilities.json";
    private static final String FEED = "https://www.cisa.gov/sites/default/files/feeds/"
            + "known_exploited_vulnerabilities.json";
    private static final String SNAPSHOT = "/kev/kev-snapshot.json";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TTL = Duration.ofDays(7);

    private final ApiCacheDAO cache;
    private final HttpClient http;
    private final String feed;

    public KevClient(ApiCacheDAO cache) {
        this(cache, HttpClient.newBuilder().connectTimeout(TIMEOUT).build(), FEED);
    }

    /** Test seam: injected client and feed URL keep tests offline. */
    KevClient(ApiCacheDAO cache, HttpClient http, String feed) {
        this.cache = cache;
        this.http = http;
        this.feed = feed;
    }

    /** Cache hit → live feed → bundled snapshot (never null). */
    public KevCatalog catalog() {
        Optional<String> hit = tryCache();
        if (hit.isPresent()) {
            KevCatalog cached = tryParse(hit.get());
            if (cached != null) {
                return cached;
            }
        }
        KevCatalog live = fetchFeed();
        return live != null ? live : bundled();
    }

    /** The bundled snapshot — parses without network, the offline floor. */
    public static KevCatalog bundled() {
        KevCatalog snapshot = tryParse(read(SNAPSHOT));
        if (snapshot == null) {
            throw new IllegalStateException("bundled KEV snapshot unreadable");
        }
        LOG.warn("KEV catalog served from bundled snapshot");
        return snapshot;
    }

    private Optional<String> tryCache() {
        try {
            return cache.find(PROVIDER, RESOURCE);
        } catch (SQLException e) {
            LOG.warn("KEV cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** API.md request flow: 15 s, 200 caches, anything else degrades to null. */
    private KevCatalog fetchFeed() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feed))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("KEV feed status {}", response.statusCode());
                return null;
            }
            KevCatalog parsed = tryParse(response.body());
            if (parsed == null) {
                return null;
            }
            try {
                cache.put(PROVIDER, RESOURCE, response.body(), TTL);
            } catch (SQLException e) {
                LOG.warn("KEV cache write failed: {}", e.getMessage());
            }
            return parsed;
        } catch (IOException e) {
            LOG.warn("KEV feed unreachable: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static KevCatalog tryParse(String body) {
        try {
            return Json.MAPPER.readValue(body, KevCatalog.class);
        } catch (JsonProcessingException e) {
            LOG.warn("KEV JSON unreadable: {}", e.getMessage());
            return null;
        }
    }

    private static String read(String resource) {
        try (InputStream in = KevClient.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read resource " + resource, e);
        }
    }
}
