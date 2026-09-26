package com.argus.core.api;

import com.argus.core.api.dto.VtIpResult;
import com.argus.core.concurrency.TokenBucket;
import com.argus.core.json.Json;
import com.argus.db.ApiCacheDAO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

/**
 * IP reputation via VirusTotal (API.md request flow): api_cache first
 * (24 h), then one TokenBucket permit (4/min), then GET
 * /api/v3/ip_addresses/{ip} with the x-apikey header. 200 parses and
 * caches, 404 is "no data" (uncached, no retry), 429 disables the provider
 * for the session, 5xx and timeouts retry once — then degrade. Degradation
 * returns an empty answer, never an exception: enrichment must not fail a
 * scan. The key travels only in the header and is never logged. Tests run
 * on fixtures and a mock server only (API.md quota laws).
 */
public final class VtClient implements AutoCloseable {

    public static final String PROVIDER = "virustotal";

    private static final Logger LOG = LoggerFactory.getLogger(VtClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TTL = Duration.ofHours(24);

    private final ApiCacheDAO cache;
    private final String apiKey;
    private final String baseUrl;
    private final TokenBucket bucket;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile boolean degraded;
    private volatile int degradedStatus = -1;

    public VtClient(ApiCacheDAO cache, byte[] apiKey) {
        this(cache, apiKey, "https://www.virustotal.com", new TokenBucket());
    }

    /** Base URL and bucket are injectable for the mock-server tests. */
    public VtClient(ApiCacheDAO cache, byte[] apiKey, String baseUrl, TokenBucket bucket) {
        this.cache = cache;
        this.apiKey = new String(apiKey, StandardCharsets.UTF_8);
        this.baseUrl = baseUrl;
        this.bucket = bucket;
    }

    /** Cache → permit → send. Empty = no data or degraded provider. */
    public Optional<VtIpResult> lookup(String ip) {
        if (ip == null || ip.isBlank()) {
            return Optional.empty();
        }
        Optional<VtIpResult> hit = cached(ip);
        if (hit.isPresent()) {
            return hit;
        }
        return degraded ? Optional.empty() : fetch(ip);
    }

    public boolean degraded() {
        return degraded;
    }

    /** HTTP status that disabled the provider, or -1 when healthy. */
    public int degradedStatus() {
        return degradedStatus;
    }

    private Optional<VtIpResult> fetch(String ip) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/api/v3/ip_addresses/" + ip))
                .timeout(TIMEOUT)
                .header("x-apikey", apiKey)
                .GET()
                .build();
        IOException last = new IOException("no attempt made");
        for (int attempt = 0; attempt < 2 && !degraded; attempt++) {
            try {
                bucket.acquire();
                HttpResponse<String> resp =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code == 200) {
                    VtIpResult result = parse(resp.body());
                    store(ip, result);
                    return Optional.of(result);
                }
                if (code == 404) {
                    return Optional.empty(); // no data — not an error, not cached (API.md)
                }
                if (code == 429) {
                    degrade(code, "rate limited — provider disabled for the session");
                    return Optional.empty();
                }
                if (code < 500) {
                    degrade(code, "unexpected status " + code);
                    return Optional.empty();
                }
                last = new IOException("HTTP " + code);
            } catch (IOException e) {
                last = e; // 5xx, timeout or transport error — retry once
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                degrade(-1, "interrupted");
                return Optional.empty();
            }
        }
        degrade(-1, "virustotal unavailable after retry: " + last.getMessage());
        return Optional.empty();
    }

    /** Reads data.attributes.last_analysis_stats out of the VT envelope. */
    private static VtIpResult parse(String body) throws IOException {
        JsonNode stats = Json.MAPPER.readTree(body)
                .path("data").path("attributes").path("last_analysis_stats");
        if (!stats.isObject()) {
            throw new IOException("unexpected VirusTotal response shape");
        }
        return new VtIpResult(stats.path("malicious").asInt(0),
                stats.path("suspicious").asInt(0));
    }

    private void degrade(int status, String why) {
        degraded = true;
        degradedStatus = status;
        LOG.warn("virustotal degraded ({}): {}", status, why);
    }

    private Optional<VtIpResult> cached(String ip) {
        Optional<String> hit;
        try {
            hit = cache.find(PROVIDER, ip);
        } catch (SQLException e) {
            LOG.warn("virustotal cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
        if (hit.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Json.MAPPER.readValue(hit.get(), VtIpResult.class));
        } catch (JsonProcessingException e) {
            return Optional.empty(); // corrupt entry — treat as unseen and refetch
        }
    }

    private void store(String ip, VtIpResult result) {
        try {
            cache.put(PROVIDER, ip, Json.MAPPER.writeValueAsString(result), TTL);
        } catch (SQLException e) {
            LOG.warn("virustotal cache write failed: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            LOG.warn("virustotal result not serializable: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        bucket.close();
    }
}
