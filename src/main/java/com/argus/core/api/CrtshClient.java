package com.argus.core.api;

import com.argus.core.api.dto.CrtShEntry;
import com.argus.core.json.Json;
import com.argus.db.ApiCacheDAO;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Subdomain enumeration via crt.name — the crt.sh replacement (API.md).
 * Request flow: cache → send (15 s) → status handling. One call per scan:
 * the 24 h cache plus one enum stage per run is the "simple pacing" for a
 * keyless provider. 429 disables the provider for the session; 5xx and
 * timeouts retry once, then degrade. Tests run on fixtures and a mock
 * server only (API.md quota laws).
 */
public final class CrtshClient {

    public static final String PROVIDER = "crtsh";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TTL = Duration.ofHours(24);
    private static final TypeReference<List<CrtShEntry>> ENTRIES = new TypeReference<>() {
    };

    private final ApiCacheDAO cache;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile boolean degraded;

    public CrtshClient(ApiCacheDAO cache) {
        this(cache, "https://crt.name");
    }

    /** Base URL is injectable for the mock-server tests. */
    public CrtshClient(ApiCacheDAO cache, String baseUrl) {
        this.cache = cache;
        this.baseUrl = baseUrl;
    }

    /** Distinct subdomains for the apex, or empty on no data. */
    public List<String> subdomains(String apex) throws IOException, InterruptedException {
        if (degraded) {
            throw new ProviderDegradedException(-1, "crt.name disabled for this session");
        }
        Optional<String> hit = cached(apex);
        if (hit.isPresent()) {
            return parse(hit.get());
        }

        String url = baseUrl + "/v1/search?apex=" + apex + "&format=json";
        IOException last = new IOException("no attempt made");
        int code = -1;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpResponse<String> resp = http.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                code = resp.statusCode();
                if (code == 200) {
                    store(apex, resp.body());
                    return parse(resp.body());
                }
                if (code == 404) {
                    return List.of();
                }
                if (code == 429) {
                    degraded = true;
                    throw new ProviderDegradedException(429, "rate limited — provider disabled for the session");
                }
                if (code < 500) {
                    throw new ProviderDegradedException(code, "unexpected status " + code);
                }
                last = new IOException("HTTP " + code);
            } catch (ProviderDegradedException e) {
                throw e;
            } catch (IOException e) {
                last = e; // 5xx, timeout or transport error — retry once (API.md)
            }
        }
        throw new ProviderDegradedException(code, "crt.name unavailable after retry: " + last.getMessage(), last);
    }

    public boolean degraded() {
        return degraded;
    }

    /** JSON.md crt.name parsing: strip `*.`, drop empties, deduplicate. */
    private static List<String> parse(String json) throws IOException {
        List<CrtShEntry> entries = Json.MAPPER.readValue(json, ENTRIES);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (CrtShEntry e : entries) {
            if (e == null || e.sub() == null) {
                continue;
            }
            String sub = e.sub().trim();
            if (sub.startsWith("*.")) {
                sub = sub.substring(2);
            }
            if (!sub.isEmpty()) {
                out.add(sub.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(out);
    }

    private Optional<String> cached(String apex) {
        try {
            return cache.find(PROVIDER, apex);
        } catch (SQLException e) {
            throw new IllegalStateException("api_cache read failed", e);
        }
    }

    private void store(String apex, String body) {
        try {
            cache.put(PROVIDER, apex, body, TTL);
        } catch (SQLException e) {
            throw new IllegalStateException("api_cache write failed", e);
        }
    }
}
