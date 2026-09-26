package com.argus.core.concurrency;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * API.md TokenBucket: a semaphore with 4 permits, refilled one permit per
 * interval by a single daemon scheduler thread named {@code vt-rate-limiter}.
 * Refill only while below 4; {@link #acquire()} blocks until a permit is
 * free. A known benign race can let one permit slip — documented and
 * acceptable (API.md).
 */
public final class TokenBucket implements AutoCloseable {

    private static final int CAPACITY = 4;
    private static final Duration INTERVAL = Duration.ofSeconds(15); // 4 permits per minute

    private final Semaphore permits = new Semaphore(CAPACITY);
    private final ScheduledExecutorService refiller =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vt-rate-limiter");
                t.setDaemon(true);
                return t;
            });

    public TokenBucket() {
        this(INTERVAL);
    }

    /** Interval is injectable for the tests. */
    public TokenBucket(Duration interval) {
        long millis = Math.max(1, interval.toMillis());
        refiller.scheduleAtFixedRate(this::refill, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** Blocks until a permit is free (API.md). */
    public void acquire() throws InterruptedException {
        permits.acquire();
    }

    /** Permits currently free — package-private, for the tests. */
    int available() {
        return permits.availablePermits();
    }

    /** Single refill thread, so the below-4 check can't overshoot capacity. */
    private void refill() {
        if (permits.availablePermits() < CAPACITY) {
            permits.release(1);
        }
    }

    @Override
    public void close() {
        refiller.shutdownNow();
    }
}
