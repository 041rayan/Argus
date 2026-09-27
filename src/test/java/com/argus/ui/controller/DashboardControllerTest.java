package com.argus.ui.controller;

import com.argus.core.model.Finding;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void summarizeCountsCardsAndCharts() {
        var hosts = List.of(
                new Host(1L, 7, "a.example.com", "203.0.113.7", true, "", "", ""),
                new Host(2L, 7, "dead.example.com", "", false, "", "", ""));
        var ports = List.of(
                new PortResult("a.example.com", 80, "tcp", "http", "", "", "", true),
                new PortResult("a.example.com", 443, "tcp", "https", "", "", "", true),
                new PortResult("a.example.com", 22, "tcp", "ssh", "", "", "", false));
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH", "{}"),
                new Finding(0, null, "virustotal", "VT_FLAGGED", "MEDIUM", "{}"));

        DashboardController.DashboardStats stats =
                DashboardController.summarize(hosts, ports, findings);

        assertEquals(1, stats.aliveHosts());
        assertEquals(2, stats.openPorts(), "closed port excluded");
        assertEquals(1, stats.kev());
        assertEquals(1, stats.vt());
        assertEquals(Map.of("HIGH", 1, "MEDIUM", 1), stats.bySeverity());
        assertEquals(Set.of("a.example.com"), stats.topPorts().get(80));
    }

    @Test
    void summarizeEmptyScanIsZeroes() {
        DashboardController.DashboardStats stats =
                DashboardController.summarize(List.of(), List.of(), List.of());

        assertEquals(0, stats.aliveHosts());
        assertEquals(0, stats.openPorts());
        assertTrue(stats.bySeverity().isEmpty());
        assertTrue(stats.topPorts().isEmpty());
    }
}
