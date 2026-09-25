package com.argus.core.pipeline;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * The queue loop every queue-driven module runs inside {@code execute()}:
 * take items until the poison pill, run the body, wake sibling workers with
 * injected pills, and — the last worker out — forward exactly one pill
 * downstream, so worker counts may differ per stage. One Pump instance is
 * shared by all workers of a stage; cancellation is a token check at the
 * loop top plus interrupt (THREAD.md).
 */
public final class Pump {

    public static final Object POISON = new Object();

    private final BlockingQueue<Object> in;
    private final BlockingQueue<Object> out;
    private final CancellationToken token;
    private final int workers;

    private final CountDownLatch remaining;
    private final Object lock = new Object();
    private boolean woke;
    private boolean forwarded;

    public Pump(BlockingQueue<Object> in, BlockingQueue<Object> out,
                CancellationToken token, int workers) {
        this.in = in;
        this.out = out;
        this.token = token;
        this.workers = workers;
        this.remaining = new CountDownLatch(workers);
    }

    /** One worker's loop — call once per worker, i.e. once per execute() call. */
    public void run(Consumer<Object> body) {
        boolean pill = false;
        try {
            while (!token.isCancelled()) {
                Object item = in.take();
                if (item == POISON) {
                    pill = true;
                    break;
                }
                body.accept(item);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (pill) {
                synchronized (lock) {
                    if (!woke) {
                        woke = true;
                        for (int i = 1; i < workers; i++) {
                            in.add(POISON);
                        }
                    }
                }
                remaining.countDown();
                synchronized (lock) {
                    if (remaining.getCount() == 0 && !forwarded) {
                        forwarded = true;
                        if (out != null) {
                            out.add(POISON);
                        }
                    }
                }
            }
        }
    }

    /** Cast helper for modules reading typed payloads off an Object queue. */
    @SuppressWarnings("unchecked")
    public static <T> T payload(Object item) {
        return (T) item;
    }
}
