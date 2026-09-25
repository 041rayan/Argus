package com.argus.core.pipeline;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * One pipeline stage (CORE.md): a named executor whose workers each run the
 * module's {@code execute(ctx)} — called once per worker. The queue loop and
 * poison pill live in the module's {@link Pump}; a Stage only submits work,
 * signals drain, interrupts on cancel and shuts down gracefully (THREAD.md).
 */
public final class Stage {

    /** Module work: may throw InterruptedException; the runner restores the flag (THREAD.md). */
    @FunctionalInterface
    public interface Work {
        void run() throws InterruptedException;
    }

    private final ExecutorService executor;
    private final int workers;
    private final Work work;
    private final List<BlockingQueue<?>> drains;
    private final CountDownLatch drained;

    private Stage(ExecutorService executor, int workers, Work work,
                  List<BlockingQueue<?>> drains) {
        this.executor = executor;
        this.workers = workers;
        this.work = work;
        this.drains = drains;
        this.drained = new CountDownLatch(workers);
    }

    /**
     * @param drains queues cleared by Pipeline.cancel() — the module's input
     *               and output queues, so a cancelled stage leaves no backlog
     */
    public static Stage runner(ExecutorService executor, int workers, Work work,
                               BlockingQueue<?>... drains) {
        return new Stage(executor, workers, work, List.of(drains));
    }

    /** Purpose-based thread names (AGENTS.md); daemon so a forgotten executor can't hold the app. */
    public static ExecutorService executor(String name, int threads) {
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    /** Submitted exactly once by Pipeline.start(). */
    void start() {
        for (int i = 0; i < workers; i++) {
            executor.execute(() -> {
                try {
                    work.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    drained.countDown();
                }
            });
        }
    }

    /** True when every worker returned from execute(). */
    boolean awaitDrained(long seconds, TimeUnit unit) throws InterruptedException {
        return drained.await(seconds, unit);
    }

    void interrupt() {
        executor.shutdownNow();
    }

    void drain() {
        for (BlockingQueue<?> queue : drains) {
            queue.clear();
        }
    }

    /** THREAD.md graceful shutdown: shutdown → await 5 s → shutdownNow → await 5 s. */
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
