package com.argus.core.event;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest {

    @Test
    void listenerReceivesPublishedEvents() throws InterruptedException {
        try (EventBus bus = new EventBus()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ScanEvent> got = new AtomicReference<>();
            bus.subscribe(e -> {
                got.set(e);
                latch.countDown();
            });

            bus.publish(new ScanEvent.ScanStarted("example.com"));

            assertTrue(latch.await(2, TimeUnit.SECONDS), "listener never fired");
            assertEquals(new ScanEvent.ScanStarted("example.com"), got.get());
        }
    }

    @Test
    void publishAfterCloseIsDropped() {
        EventBus bus = new EventBus();
        bus.close();
        bus.publish(new ScanEvent.ScanStarted("late")); // must not throw or block
    }
}
