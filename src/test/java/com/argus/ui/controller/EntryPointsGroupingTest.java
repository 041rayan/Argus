package com.argus.ui.controller;

import com.argus.core.model.Finding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entry point inspector groups the scan's findings once, off the FX
 * thread, so a row click is a map lookup (headless, pure).
 */
class EntryPointsGroupingTest {

    @Test
    void findingsGroupUnderTheirHostPort() {
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                        "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}"),
                new Finding(0, null, "virustotal", "VT_FLAGGED", "MEDIUM",
                        "{\"host\":\"a.example.com\",\"port\":80,\"malicious\":12}"),
                new Finding(0, null, "kev", "KEV_MATCH", "LOW",
                        "{\"host\":\"a.example.com\",\"port\":443,\"cve\":\"CVE-2021-2\"}"));

        var grouped = EntryPointsController.groupByHostPort(findings);

        assertEquals(2, grouped.size(), "one key per host:port, not per finding");
        assertEquals(2, grouped.get("a.example.com:80").size());
        assertEquals(1, grouped.get("a.example.com:443").size());
    }

    @Test
    void anUnreadableFindingIsDroppedRatherThanKeyedWrongly() {
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH", null),
                new Finding(0, null, "kev", "KEV_MATCH", "LOW", "{"));

        assertTrue(EntryPointsController.groupByHostPort(findings).isEmpty(),
                "a finding with no readable host must not appear under a wrong key");
    }

    @Test
    void aLineNeverRendersTheWordNull() {
        var out = new com.argus.core.export.ExportService.FindingOut(null, null, null);

        assertEquals("-  -  -", EntryPointsController.line(out));
    }
}
