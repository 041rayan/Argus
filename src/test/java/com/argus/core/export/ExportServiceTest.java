package com.argus.core.export;

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
}
