package com.argus.core.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * One-shot validity check for a stored VirusTotal key: GET a known-good IP
 * with the x-apikey header and read the status — 200 verified, 401/403
 * rejected, 429 authenticated but quota'd, -1 provider unreachable. The key
 * travels only in the header and is never logged (API.md logging law).
 * Tests run against a mock server only (API.md quota laws).
 */
public final class VtKeyVerifier {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String PROBE = "/api/v3/ip_addresses/8.8.8.8";

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public VtKeyVerifier() {
        this("https://www.virustotal.com");
    }

    /** Base URL is injectable for the mock-server tests. */
    public VtKeyVerifier(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** @return the HTTP status, or -1 when the provider is unreachable */
    public int check(String apiKey) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + PROBE))
                .timeout(TIMEOUT)
                .header("x-apikey", apiKey)
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
