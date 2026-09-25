package com.argus.core.pipeline;

/**
 * Every pipeline stage (CORE.md). {@code supports()} verifies scope before
 * any action; {@code execute()} runs cooperatively cancellable — it must
 * restore the interrupt flag and return on InterruptedException.
 */
public interface ArgusModule {

    ModuleDescriptor descriptor();

    boolean supports(TargetContext ctx);

    void execute(TargetContext ctx) throws InterruptedException;

    /** Called once when the scan is cancelled; free resources, don't emit. */
    default void onCancel() {
    }
}
