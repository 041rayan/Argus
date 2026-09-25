package com.argus.core.pipeline;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline: loopback sockets only, no external traffic. */
class PortScanModuleTest {

    private final EventBus events = new EventBus();
    private final AtomicInteger portFound = new AtomicInteger();

    public PortScanModuleTest() {
        events.subscribe(e -> {
            if (e instanceof ScanEvent.PortFound) {
                portFound.incrementAndGet();
            }
        });
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    @Test
    void openPortQueuedClosedPortSkippedHostForwarded() throws Exception {
        int closed = closedPort();
        try (ServerSocket server = openServer()) {
            int open = server.getLocalPort();
            BlockingQueue<Object> in = new LinkedBlockingQueue<>();
            BlockingQueue<Object> out = new LinkedBlockingQueue<>();
            CancellationToken token = new CancellationToken();
            PortScanModule module =
                    new PortScanModule(in, out, events, token, List.of(closed, open));
            Host host = new Host(null, -1, "www.example.com", "127.0.0.1", true, "", "", "");
            in.add(host);
            in.add(Pump.POISON);

            module.execute(new TargetContext("example.com", List.of("127.0.0.1/32")));

            List<Object> items = drain(out);
            assertTrue(items.stream().anyMatch(i -> i instanceof PortResult p && p.port() == open),
                    "open port recorded");
            assertTrue(items.stream().noneMatch(i -> i instanceof PortResult p && p.port() == closed),
                    "closed port not recorded");
            assertTrue(items.stream().anyMatch(i -> i == host), "host forwarded downstream");
            assertEquals(1, portFound.get(), "one PortFound event per open port");
        }
    }

    @Test
    void outOfScopeAndDeadHostsAreNeverConnected() throws Exception {
        try (ServerSocket server = openServer()) {
            int open = server.getLocalPort();
            BlockingQueue<Object> in = new LinkedBlockingQueue<>();
            BlockingQueue<Object> out = new LinkedBlockingQueue<>();
            CancellationToken token = new CancellationToken();
            PortScanModule module =
                    new PortScanModule(in, out, events, token, List.of(open));
            Host foreign = new Host(null, -1, "foo.other.org", "127.0.0.1", true, "", "", "");
            Host dead = new Host(null, -1, "gone.example.com", "", false, "", "", "");
            in.add(foreign);
            in.add(dead);
            in.add(Pump.POISON);

            module.execute(new TargetContext("example.com", List.of("10.0.0.0/8")));

            List<Object> items = drain(out);
            assertTrue(items.stream().noneMatch(i -> i instanceof PortResult),
                    "scope check blocked the connect");
            assertEquals(0, portFound.get());
            assertTrue(items.contains(foreign), "out-of-scope host forwarded untouched");
            assertTrue(items.contains(dead), "dead host forwarded untouched");
        }
    }

    private static List<Object> drain(BlockingQueue<Object> out) {
        List<Object> items = new ArrayList<>();
        out.drainTo(items);
        items.removeIf(i -> i == Pump.POISON);
        return items;
    }

    private static ServerSocket openServer() throws IOException {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    }

    /** Bind an ephemeral port, then release it — nothing should listen there. */
    private static int closedPort() throws IOException {
        ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        int port = socket.getLocalPort();
        socket.close();
        return port;
    }
}
