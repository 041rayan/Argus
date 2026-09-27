package com.argus.ui;

import com.argus.core.export.ExportService;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Results inspector reads already loaded lists, grouped per host.
 * Pure, so it is tested without a toolkit or a database.
 */
class HostDetailTest {

    @Test
    void portsAndFindingsLandOnTheRightHost() {
        var ports = List.of(
                new PortResult("a.example.com", 80, "tcp", "http", "nginx/1.24", "", "", true),
                new PortResult("a.example.com", 22, "tcp", "ssh", "", "", "", false),
                new PortResult("b.example.com", 443, "tcp", "https", "", "", "", true));
        var findings = List.of(new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}"));

        Map<String, HostDetail> byHost = HostDetail.byHost(ports, findings);

        assertEquals(Set.of("a.example.com", "b.example.com"), byHost.keySet());
        assertEquals(1, byHost.get("a.example.com").openPorts().size(),
                "the closed ssh port is not open");
        assertEquals(1, byHost.get("a.example.com").findingCount());
        assertEquals(0, byHost.get("b.example.com").findingCount());
    }

    @Test
    void emptyDetailHasNothingToShow() {
        HostDetail empty = HostDetail.empty("a.example.com");

        assertTrue(empty.openPorts().isEmpty());
        assertTrue(empty.findings().isEmpty());
        assertEquals(0, empty.findingCount());
        assertEquals(Map.of(), HostDetail.byHost(List.of(), List.of()));
    }

    @Test
    void aFindingWithNoHostIsDropped() {
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH", null),
                new Finding(0, null, "kev", "KEV_MATCH", "LOW", "{"));

        assertTrue(HostDetail.byHost(List.of(), findings).isEmpty(),
                "an unreadable detail_json must not invent a host row");
    }

    @Test
    void aHostWithPortsAndFindingsKeepsBoth() {
        var ports = List.of(new PortResult("a.example.com", 80, "tcp", "http", "", "", "", true));
        var findings = List.of(
                new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                        "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}"),
                new Finding(0, null, "virustotal", "VT_FLAGGED", "MEDIUM",
                        "{\"host\":\"a.example.com\",\"port\":80,\"malicious\":12}"));

        HostDetail detail = HostDetail.byHost(ports, findings).get("a.example.com");

        assertEquals(1, detail.openPorts().size(),
                "the merge must keep the ports it already had");
        assertEquals(2, detail.findingCount());
        assertEquals("a.example.com", detail.host());
    }

    @Test
    void theHostKeyComesFromTheSharedHostPortContract() {
        var finding = new Finding(0, null, "kev", "KEV_MATCH", "HIGH",
                "{\"host\":\"a.example.com\",\"port\":80,\"cve\":\"CVE-2021-1\"}");

        assertEquals("a.example.com:80", ExportService.hostPortOf(finding));
        assertEquals(Set.of("a.example.com"),
                HostDetail.byHost(List.of(), List.of(finding)).keySet(),
                "the same finding must group under the host part of that key");
    }
}
