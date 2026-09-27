package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pane swap-out releases the worker (headless: no FX toolkit touched). */
class ExportControllerTest {

    @Test
    void hiddenExportReleasesThreads() {
        ExportController controller = new ExportController();
        controller.onHidden();

        assertTrue(controller.worker.isShutdown());
    }

    @Test
    void hiddenTwiceStaysSilent() {
        ExportController controller = new ExportController();
        controller.onHidden();

        controller.onHidden(); // second swap: no throw, still shut down
        assertTrue(controller.worker.isShutdown());
    }
}
