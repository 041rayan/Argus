package com.argus.ui.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shell swap-out must never kill a running scan (headless: no FX toolkit touched). */
class DashboardControllerTest {

    @Test
    void hiddenIdleDashboardReleasesThreads() {
        DashboardController controller = new DashboardController();
        controller.onHidden();

        assertTrue(controller.worker.isShutdown());
        assertTrue(controller.coordinator.isShutdown());
    }

    @Test
    void hiddenScanningDashboardKeepsCoordinatorAlive() {
        DashboardController controller = new DashboardController();
        controller.starting = true; // scan owns the coordinator thread
        controller.onHidden();

        assertTrue(controller.worker.isShutdown());
        assertFalse(controller.coordinator.isShutdown(),
                "OK on the mid-scan dialog promises the run survives");
    }
}
