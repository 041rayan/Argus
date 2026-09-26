package com.argus.core.api;

import com.argus.core.api.dto.IpApiResult;
import com.argus.core.json.Json;
import com.argus.db.ApiCacheDAO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * IP geolocation/ASN via ip-api (API.md): per-IP cache (7 d), then batches of
 * up to 100 unseen IPs, request flow cache → send (15 s) → status handling.
 * Keyless — the free tier is plain-http only, which is why the default base
 * URL is http. 429 disables the provider for the session; 5xx and timeouts
 * retry once. Degradation returns a partial answer, never an exception:
 * enrichment must not fail a scan. Tests run on fixtures and a mock server
 * only (API.md quota laws).
 */
public final class IpApiClient {

    public static final String PROVIDER = "ip-api";

    private static final Logger LOG = LoggerFactory.getLogger(IpApiClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Duration TTL = Duration.ofDays(7);
    private static final int BATCH = 100;
    private static final TypeReference<List<IpApiResult>> RESULTS = new TypeReference<>() {
    };

    private final ApiCacheDAO cache;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile boolean degraded;
    private volatile int degradedStatus = -1;

    public IpApiClient(ApiCacheDAO cache) {
        this(cache, "http://ip-api.com");
    }

    /** Base URL is injectable for the mock-server tests. */
    public IpApiClient(ApiCacheDAO cache, String baseUrl) {
        this.cache = cache;
        this.baseUrl = baseUrl;
    }

    /**
     * Results for the given IPs — cache first, batches of the unseen after.
     * Partial on degradation: IPs that never got answered stay absent.
     */
    public Map<String, IpApiResult> lookup(Collection<String> ips) {
        Map<String, IpApiResult> out = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String ip : ips) {
            if (ip == null || ip.isBlank()) {
                continue;
            }
            IpApiResult hit = cached(ip);
            if (hit != null) {
                out.put(ip, hit);
            } else {
                missing.add(ip);
            }
        }
        for (int i = 0; i < missing.size() && !degraded; i += BATCH) {
            fetch(missing.subList(i, Math.min(i + BATCH, missing.size())), out);
        }
        return out;
    }

    public boolean degraded() {
        return degraded;
    }

    /** HTTP status that disabled the provider, or -1 when it is healthy. */
    public int degradedStatus() {
        return degradedStatus;
    }

    /** One batch: 200 caches per IP, 429 disables, 5xx/timeout retries once (API.md). */
    private void fetch(List<String> batch, Map<String, IpApiResult> out) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/batch"))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            Json.MAPPER.writeValueAsString(batch)))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("IP list not serializable", e); // strings only
        }
        IOException last = new IOException("no attempt made");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpResponse<String> resp =
                        http.send(request, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                if (code == 200) {
                    accept(resp.body(), out);
                    return;
                }
                if (code == 404) {
                    return; // no data — not an error (API.md)
                }
                if (code == 429) {
                    degrade(code, "rate limited — provider disabled for the session");
                    return;
                }
                if (code < 500) {
                    degrade(code, "unexpected status " + code);
                    return;
                }
                last = new IOException("HTTP " + code);
            } catch (IOException e) {
                last = e; // 5xx, timeout or transport error — retry once
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                degrade(-1, "interrupted");
                return;
            }
        }
        degrade(-1, "ip-api unavailable after retry: " + last.getMessage());
    }

    private void accept(String body, Map<String, IpApiResult> out) throws IOException {
        for (IpApiResult r : Json.MAPPER.readValue(body, RESULTS)) {
            if (r == null || !"success".equals(r.status()) || r.query() == null) {
                continue; // "fail" rows carry nothing useful (reserved/private IPs)
            }
            out.put(r.query(), r);
            store(r);
        }
    }

    private void degrade(int status, String why) {
        degraded = true;
        degradedStatus = status;
        LOG.warn("ip-api degraded ({}): {}", status, why);
    }

    private IpApiResult cached(String ip) {
        Optional<String> hit;
        try {
            hit = cache.find(PROVIDER, ip);
        } catch (SQLException e) {
            LOG.warn("ip-api cache read failed: {}", e.getMessage());
            return null;
        }
        if (hit.isEmpty()) {
            return null;
        }
        try {
            return Json.MAPPER.readValue(hit.get(), IpApiResult.class);
        } catch (JsonProcessingException e) {
            return null; // corrupt entry — treat as unseen and refetch
        }
    }

    private void store(IpApiResult r) {
        try {
            cache.put(PROVIDER, r.query(), Json.MAPPER.writeValueAsString(r), TTL);
        } catch (SQLException e) {
            LOG.warn("ip-api cache write failed: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            LOG.warn("ip-api result not serializable: {}", e.getMessage());
        }
    }
}
