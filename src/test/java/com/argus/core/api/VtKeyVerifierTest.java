package com.argus.core.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Offline: mock VT endpoint, no live calls (API.md quota laws). */
class VtKeyVerifierTest {

    private static final String KEY = "test-key-123";

    private HttpServer server;
    private String baseUrl;
    private volatile int statusCode = 200;
    private final AtomicReference<String> seenApiKey = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/ip_addresses/8.8.8.8", exchange -> {
            seenApiKey.set(exchange.getRequestHeaders().getFirst("x-apikey"));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
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

    @Test
    void validKeyReturns200AndTravelsInHeaderOnly() {
        assertEquals(200, new VtKeyVerifier(baseUrl).check(KEY));
        assertEquals(KEY, seenApiKey.get(), "key goes in the x-apikey header");
    }

    @Test
    void rejectedKeyReturns401() {
        statusCode = 401;
        assertEquals(401, new VtKeyVerifier(baseUrl).check(KEY));
    }

    @Test
    void quotaReturns429() {
        statusCode = 429;
        assertEquals(429, new VtKeyVerifier(baseUrl).check(KEY));
    }

    @Test
    void unreachableProviderReturnsMinusOne() {
        assertEquals(-1, new VtKeyVerifier("http://127.0.0.1:1").check(KEY));
    }
}
