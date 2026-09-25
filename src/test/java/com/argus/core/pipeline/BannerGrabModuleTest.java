package com.argus.core.pipeline;

import com.argus.core.model.PortResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: loopback HTTP and SSH-style servers only. */
class BannerGrabModuleTest {

    private static final Function<String, Optional<String>> LOOPBACK =
            host -> Optional.of("127.0.0.1");

    private HttpServer server;
    private int httpPort;
    private final AtomicReference<String> requestLine = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestLine.set(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " host=" + exchange.getRequestHeaders().getFirst("Host"));
            exchange.getResponseHeaders().set("Server", "nginx/1.18.0");
            byte[] body = ("<html><head><title> Lab Home </title></head>"
                    + "<body>hi</body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        httpPort = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void webPortGetsTitleAndServerHeaderFromRealHttp() throws Exception {
        List<Object> items = run(ctx("localhost"), Set.of(httpPort), Set.of(),
                new PortResult("localhost", httpPort, "tcp", "", "", "", "", true));

        PortResult grabbed = onlyPort(items);
        assertEquals("http", grabbed.service());
        assertEquals("Server: nginx/1.18.0", grabbed.banner());
        assertEquals("Lab Home", grabbed.title());
        assertTrue(requestLine.get().startsWith("GET / host=localhost:" + httpPort),
                "real GET with Host header, got " + requestLine.get());
    }

    @Test
    void rawPortReadsFirstLineServerSends() throws Exception {
        try (ServerSocket greeter = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            int port = greeter.getLocalPort();
            Thread.ofVirtual().start(() -> {
                try (Socket client = greeter.accept()) {
                    client.getOutputStream()
                            .write("SSH-2.0-OpenSSH_9.2\r\n".getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().flush();
                } catch (IOException e) {
                    // test server side — the client assertion tells the story
                }
            });

            List<Object> items = run(ctx("localhost"), Set.of(), Set.of(port),
                    new PortResult("localhost", port, "tcp", "", "", "", "", true));

            PortResult grabbed = onlyPort(items);
            assertEquals("SSH-2.0-OpenSSH_9.2", grabbed.banner());
            assertEquals("", grabbed.title(), "raw grab does not invent a title");
        }
    }

    @Test
    void outOfScopeAndUnknownPortsPassThroughUntouched() throws Exception {
        PortResult foreign = new PortResult("foo.other.org", 80, "tcp", "", "", "", "", true);
        PortResult unknown = new PortResult("localhost", 12345, "tcp", "", "", "", "", true);
        List<Object> items = run(ctx("localhost"), Set.of(80), Set.of(22), foreign, unknown);

        assertEquals(2, items.size());
        assertEquals(foreign, items.get(0), "out of scope: no connect");
        assertEquals(unknown, items.get(1), "non-grab port untouched");
    }

    private static TargetContext ctx(String domain) {
        return new TargetContext(domain, List.of());
    }

    private List<Object> run(TargetContext ctx, Set<Integer> webPorts, Set<Integer> rawPorts,
                             PortResult... input) {
        BlockingQueue<Object> in = new LinkedBlockingQueue<>();
        BlockingQueue<Object> out = new LinkedBlockingQueue<>();
        BannerGrabModule module = new BannerGrabModule(in, out, new CancellationToken(),
                1, LOOPBACK, webPorts, rawPorts);
        for (PortResult p : input) {
            in.add(p);
        }
        in.add(Pump.POISON);
        module.execute(ctx);

        List<Object> items = new ArrayList<>();
        out.drainTo(items);
        items.removeIf(i -> i == Pump.POISON);
        return items;
    }

    private static PortResult onlyPort(List<Object> items) {
        assertEquals(1, items.size(), "exactly one payload forwarded");
        return (PortResult) items.get(0);
    }
}
