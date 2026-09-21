package com.argus.core.concurrency;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Auto-lock (red-line): the one Thread subclass. Daemon named
 * `idle-monitor`, polls every 30 s, fires the lock callback once after 5
 * minutes of inactivity and exits. `touch()` is called by the scene-level
 * event filter; on interrupt the flag is restored and the thread exits.
 */
public final class IdleLockMonitor extends Thread {

    private static final long POLL_MS = 30_000L;
    private static final long IDLE_MS = 5 * 60_000L;

    private final AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
    private final Runnable onIdle;

    public IdleLockMonitor(Runnable onIdle) {
        super("idle-monitor");
        setDaemon(true);
        this.onIdle = onIdle;
    }

    /** Called from FX event filters — cheap and lock-free. */
    public void touch() {
        lastActivity.set(System.currentTimeMillis());
    }

    @Override
    public void run() {
        try {
            while (true) {
                sleep(POLL_MS);
                if (System.currentTimeMillis() - lastActivity.get() >= IDLE_MS) {
                    onIdle.run();
                    return;
                }
            }
        } catch (InterruptedException e) {
            interrupt();
        }
    }
}
