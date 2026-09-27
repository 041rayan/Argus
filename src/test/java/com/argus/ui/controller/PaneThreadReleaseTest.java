package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every pane owns a worker thread, and the shell releases it on swap-out.
 * A pane that does not implement {@link ShellContent} leaks one daemon
 * thread per navigation (headless: no FX toolkit touched).
 */
class PaneThreadReleaseTest {

    @Test
    void hiddenResultsReleasesThreads() {
        ResultsController controller = new ResultsController();
        controller.onHidden();

        assertTrue(controller.executor.isShutdown());
    }

    @Test
    void hiddenEntryPointsReleasesThreads() {
        EntryPointsController controller = new EntryPointsController();
        controller.onHidden();

        assertTrue(controller.worker.isShutdown());
    }

    @Test
    void hiddenTwiceStaysSilent() {
        ResultsController controller = new ResultsController();
        controller.onHidden();

        controller.onHidden(); // second swap: no throw, still shut down
        assertTrue(controller.executor.isShutdown());
    }
}
