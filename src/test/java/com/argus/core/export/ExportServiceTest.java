package com.argus.core.export;

import com.argus.core.model.PortResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceTest {

    private final ExportService service = new ExportService();

    private static final List<ExportService.EntryPoint> POINTS = List.of(
            new ExportService.EntryPoint(1, "api.example.com:443", "https", "CVE-2021-44228", 75),
            new ExportService.EntryPoint(2, "mail.example.com:25", "smtp", "", 40));

    private static final List<ExportService.FindingOut> FINDINGS = List.of(
            new ExportService.FindingOut("KEV_MATCH", "CRITICAL", "log4j 2.14.1"),
            new ExportService.FindingOut("VT_FLAGGED", "MEDIUM", "3 engines"));

    @Test
    void markdownHasHeaderTableAndFindings() {
        String md = service.markdown("example.com", Instant.parse("2026-09-25T00:00:00Z"),
                "alice", POINTS, FINDINGS);

        assertTrue(md.contains("# Target package: example.com"));
        assertTrue(md.contains("- Operator: alice"));
        assertTrue(md.contains("| 1 | api.example.com:443 | https | CVE-2021-44228 | 75 |"));
        assertTrue(md.contains("- [CRITICAL] KEV_MATCH: log4j 2.14.1"));
    }

    @Test
    void markdownPipeInFieldIsEscaped() {
        String md = service.markdown("t", null, "op|er", List.of(), List.of());
        assertTrue(md.contains("op\\|er"));
        assertTrue(md.contains("not yet scanned"));
        assertTrue(md.contains("(none)"));
    }

    @Test
    void jsonRoundTripsShape() throws Exception {
        String json = service.json("example.com", Instant.parse("2026-09-25T00:00:00Z"),
                POINTS, FINDINGS);

        JsonNode doc = new ObjectMapper().readTree(json);
        assertEquals("example.com", doc.get("target").asText());
        assertEquals("2026-09-25T00:00:00Z", doc.get("scannedAt").asText());
        assertEquals(2, doc.get("entryPoints").size());
        assertEquals("CVE-2021-44228", doc.get("entryPoints").get(0).get("kev").asText());
        assertEquals(2, doc.get("findings").size());
    }

    @Test
    void jsonWithoutScanHasNullScannedAt() throws Exception {
        JsonNode doc = new ObjectMapper().readTree(
                service.json("t", null, List.of(), List.of()));
        assertTrue(doc.get("scannedAt").isNull());
        assertEquals(0, doc.get("entryPoints").size());
    }

    @Test
    void entryPointsRankByCorePriorityScore() {
        PortResult apache80 = new PortResult("www.example.com", 80, "tcp", "http",
                "2.4.49", "", "", true);
        PortResult ssh = new PortResult("www.example.com", 22, "tcp", "ssh", "", "", "", true);
        PortResult odd = new PortResult("www.example.com", 9999, "tcp", "", "", "", "", true);

        List<ExportService.EntryPoint> points = ExportService.entryPoints(
                List.of(apache80, ssh, odd),
                List.of(finding("www.example.com", 80, "CONFIRMED", true),
                        finding("www.example.com", 22, "CANDIDATE", false)));

        assertEquals(3, points.size(), "every open port is an entry point");
        assertEquals(1, points.get(0).rank());
        assertEquals("www.example.com:80", points.get(0).hostPort());
        assertEquals(55, points.get(0).score(), "35 confirmed + 10 ransomware + 10 web");
        assertTrue(points.get(0).kev().contains("CVE-2021-41773 CONFIRMED"));
        assertEquals(25, points.get(1).score(), "15 candidate + 10 admin service");
        assertEquals(0, points.get(2).score(), "unknown port, no findings");
        assertEquals("-", points.get(2).kev());
        assertEquals("-", points.get(2).service());
    }

    @Test
    void scoreIsCappedAtHundred() {
        List<ExportService.EntryPoint> points = ExportService.entryPoints(
                List.of(new PortResult("h", 80, "tcp", "http", "", "", "", true)),
                List.of(finding("h", 80, "CONFIRMED", false),
                        finding("h", 80, "CONFIRMED", false),
                        finding("h", 80, "CONFIRMED", false),
                        finding("h", 80, "CONFIRMED", false)));
        assertEquals(100, points.get(0).score(), "35*4 + 10 web overflows the cap");
    }

    @Test
    void findingOutsSummarizeKevDetails() {
        List<ExportService.FindingOut> outs =
                ExportService.findingOuts(List.of(finding("h", 80, "CONFIRMED", false)));
        assertEquals("KEV_MATCH", outs.get(0).type());
        assertTrue(outs.get(0).detail().contains("h:80 CVE-2021-41773 (CONFIRMED)"),
                outs.get(0).detail());
    }

    private static com.argus.core.model.Finding finding(String host, int port,
                                                        String confidence, boolean ransomware) {
        return new com.argus.core.model.Finding(0, null, "kev-analyze", "KEV_MATCH",
                "CONFIRMED".equals(confidence) ? "HIGH" : "MEDIUM",
                "{\"host\":\"" + host + "\",\"port\":" + port
                        + ",\"cve\":\"CVE-2021-41773\",\"confidence\":\"" + confidence
                        + "\",\"ransomware\":" + ransomware + "}");
    }
}
