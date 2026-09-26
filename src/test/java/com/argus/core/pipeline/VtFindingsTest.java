package com.argus.core.pipeline;

import com.argus.core.api.dto.VtIpResult;
import com.argus.core.json.Json;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VT_FLAGGED emission: severity mapping, host filter, detail shape (CORE.md, DB.md). */
class VtFindingsTest {

    private static final PortResult ON_TARGET =
            new PortResult("www.example.com", 443, "tcp", "https", "", "", "", true);
    private static final PortResult OTHER_HOST =
            new PortResult("other.example.com", 80, "tcp", "http", "", "", "", true);
    private static final Set<String> NAMES = Set.of("www.example.com", "203.0.113.7");

    @Test
    void cleanVerdictEmitsNothing() {
        assertTrue(ScanRunner.vtFindings("203.0.113.7", new VtIpResult(0, 0), NAMES,
                List.of(ON_TARGET)).isEmpty());
    }

    @Test
    void severityFollowsEngineCounts() {
        assertEquals("HIGH", ScanRunner.vtFindings("203.0.113.7", new VtIpResult(12, 4),
                NAMES, List.of(ON_TARGET)).get(0).severity());
        assertEquals("MEDIUM", ScanRunner.vtFindings("203.0.113.7", new VtIpResult(5, 1),
                NAMES, List.of(ON_TARGET)).get(0).severity());
        assertEquals("LOW", ScanRunner.vtFindings("203.0.113.7", new VtIpResult(0, 3),
                NAMES, List.of(ON_TARGET)).get(0).severity());
    }

    @Test
    void oneFindingPerPortOnHostsBehindTheIp() throws Exception {
        List<Finding> got = ScanRunner.vtFindings("203.0.113.7", new VtIpResult(12, 4),
                NAMES, List.of(ON_TARGET, OTHER_HOST));

        assertEquals(1, got.size(), "only hosts living on the flagged IP");
        Finding f = got.get(0);
        assertEquals("VT_FLAGGED", f.type());
        assertEquals("virustotal", f.moduleId());

        JsonNode d = Json.MAPPER.readTree(f.detailJson());
        assertEquals("www.example.com", d.path("host").asText());
        assertEquals(443, d.path("port").asInt());
        assertEquals("203.0.113.7", d.path("ip").asText());
        assertEquals(12, d.path("malicious").asInt());
        assertEquals(4, d.path("suspicious").asInt());
    }
}
