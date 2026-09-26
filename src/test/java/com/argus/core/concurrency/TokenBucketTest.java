package com.argus.core.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketTest {

    private TokenBucket bucket;

    @AfterEach
    void tearDown() {
        if (bucket != null) {
            bucket.close();
        }
    }

    @Test
    void startsWithFourPermits() {
        bucket = new TokenBucket(Duration.ofSeconds(10));
        // broken capacity would block on the first refills for ~10 s each
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            for (int i = 0; i < 4; i++) {
                bucket.acquire();
            }
        }, "the first four acquires come from capacity, not the refiller");
    }

    @Test
    void fifthAcquireWaitsForRefill() throws Exception {
        bucket = new TokenBucket(Duration.ofMillis(100));
        for (int i = 0; i < 4; i++) {
            bucket.acquire();
        }
        assertTrue(tryAcquireWithin(2_000), "refill must hand the fifth permit back");
    }

    @Test
    void refillNeverExceedsFour() throws Exception {
        bucket = new TokenBucket(Duration.ofMillis(100));
        bucket.acquire();
        bucket.acquire();
        Thread.sleep(600); // ~6 refill ticks: free slots restored, capacity not blown past
        assertEquals(4, bucket.available(), "refiller stops at 4 (API.md)");
    }

    /** One attempt at acquire() from a side thread; true when it completed. */
    private boolean tryAcquireWithin(long millis) throws InterruptedException {
        CountDownLatch got = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                bucket.acquire();
                got.countDown();
            } catch (InterruptedException ignored) {
                // test over
            }
        }, "acquire-attempt");
        t.setDaemon(true);
        t.start();
        return got.await(millis, TimeUnit.MILLISECONDS);
    }
}
