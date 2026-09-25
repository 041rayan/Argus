package com.argus.core.pipeline;

import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.model.Host;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnsResolveModuleTest {

    private final BlockingQueue<Object> in = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> out = new LinkedBlockingQueue<>();
    private final CancellationToken token = new CancellationToken();
    private EventBus events;

    @BeforeEach
    void setUp() {
        events = new EventBus();
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    @Test
    void resolvesInScopeHostsSkipsOutOfScopeAndForwardsPill() throws Exception {
        CountDownLatch hosts = new CountDownLatch(2);
        List<ScanEvent> got = new ArrayList<>();
        events.subscribe(e -> {
            if (e instanceof ScanEvent.HostFound) {
                synchronized (got) {
                    got.add(e);
                }
                hosts.countDown();
            }
        });

        Function<String, Optional<String>> resolver =
                h -> h.equals("www.example.com") ? Optional.of("93.184.216.34") : Optional.empty();
        DnsResolveModule module = new DnsResolveModule(in, out, events, token, 1, resolver);

        in.add("www.example.com");
        in.add("dead.example.com");
        in.add("evil.com"); // out of scope — no resolution attempt
        in.add(Pump.POISON);

        module.execute(new TargetContext("example.com", List.of()));

        List<Object> items = new ArrayList<>(out);
        assertEquals(3, items.size(), "two hosts + pill");
        Host alive = assertInstanceOf(Host.class, items.get(0));
        assertEquals("93.184.216.34", alive.ip());
        assertTrue(alive.alive());
        Host dead = assertInstanceOf(Host.class, items.get(1));
        assertEquals("", dead.ip());
        assertFalse(dead.alive());
        assertEquals(Pump.POISON, items.get(2));

        assertTrue(hosts.await(2, TimeUnit.SECONDS), "HostFound events missing");
        assertEquals(2, got.size());
    }
}
