package com.argus.core.pipeline;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs a chain of stages (CORE.md): start submits the workers, await waits
 * for the tail to drain, cancel stops everything with partial results kept
 * (THREAD.md), close shuts every executor down gracefully.
 */
public final class Pipeline implements AutoCloseable {

    private final List<Stage> stages;
    private final CancellationToken token;

    public Pipeline(CancellationToken token, Stage... stages) {
        this.token = token;
        this.stages = List.of(stages);
    }

    public void start() {
        for (Stage stage : stages) {
            stage.start();
        }
    }

    /** True when the tail stage's workers all returned (normal end of stream). */
    public boolean await(long seconds) throws InterruptedException {
        return stages.getLast().awaitDrained(seconds, TimeUnit.SECONDS);
    }

    /** Cancel button path: token, interrupt workers, drain queues (THREAD.md). */
    public void cancel() {
        token.cancel();
        for (Stage stage : stages) {
            stage.interrupt();
        }
        for (Stage stage : stages) {
            stage.drain();
        }
    }

    /** Graceful shutdown of every stage executor (THREAD.md). */
    @Override
    public void close() {
        for (Stage stage : stages) {
            stage.shutdown();
        }
    }
}
