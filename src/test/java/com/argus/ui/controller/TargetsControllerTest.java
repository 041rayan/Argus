package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pane swap-out releases the DAO thread (headless: no FX toolkit touched). */
class TargetsControllerTest {

    @Test
    void hiddenTargetsReleasesThreads() {
        TargetsController controller = new TargetsController();
        controller.onHidden();

        assertTrue(controller.executor.isShutdown());
    }

    @Test
    void hiddenTwiceStaysSilent() {
        TargetsController controller = new TargetsController();
        controller.onHidden();

        controller.onHidden(); // second swap: no throw, still shut down
        assertTrue(controller.executor.isShutdown());
    }
}
