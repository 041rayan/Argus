package com.argus.core.event;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * BlockingQueue of events with listener dispatch (CORE.md): producers
 * {@link #publish}, the UI {@link #subscribe}s. One daemon dispatcher thread
 * drains the queue so producers never block on the listener; the listener
 * hops to the FX thread itself (core stays JavaFX-free).
 */
public final class EventBus implements AutoCloseable {

    private final BlockingQueue<ScanEvent> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread dispatcher;
    private volatile Consumer<ScanEvent> listener = event -> {
    };

    public EventBus() {
        dispatcher = new Thread(this::dispatch, "event-dispatch");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    public void publish(ScanEvent event) {
        if (running.get()) {
            queue.add(event);
        }
    }

    public void subscribe(Consumer<ScanEvent> listener) {
        this.listener = listener;
    }

    private void dispatch() {
        while (running.get()) {
            try {
                ScanEvent event = queue.take();
                listener.accept(event); // read after take(): must not capture a stale listener
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Delivers whatever is still queued, then stops the dispatcher. */
    @Override
    public void close() {
        running.set(false);
        ScanEvent pending;
        while ((pending = queue.poll()) != null) {
            listener.accept(pending);
        }
        dispatcher.interrupt(); // wake it if it is parked in take()
    }
}
