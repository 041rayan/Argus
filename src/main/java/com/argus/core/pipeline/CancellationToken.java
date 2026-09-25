package com.argus.core.pipeline;

/**
 * Cancellation flag (THREAD.md): volatile, checked at loop tops and before
 * every socket connect; the cancel button also interrupts the stage executors.
 */
public final class CancellationToken {

    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
