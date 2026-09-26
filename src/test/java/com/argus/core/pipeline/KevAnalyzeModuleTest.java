package com.argus.core.pipeline;

import com.argus.core.api.KevClient;
import com.argus.core.event.EventBus;
import com.argus.core.event.ScanEvent;
import com.argus.core.kev.KevMatcher;
import com.argus.core.json.Json;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KevAnalyzeModuleTest {

    private final BlockingQueue<Object> in = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> out = new LinkedBlockingQueue<>();
    private final CancellationToken token = new CancellationToken();
    private EventBus events;

    @BeforeEach
    void setUp() {
        events = new EventBus();
    }

    @AfterEach
    void tearDown() {
        events.close();
    }

    @Test
    void vulnerableBannerBecomesHighFindingAndPortStillPasses() throws Exception {
        List<ScanEvent> got = new ArrayList<>();
        events.subscribe(got::add);
        KevAnalyzeModule module = new KevAnalyzeModule(in, out, events, token,
                new KevMatcher(KevClient.bundled()));

        in.add(new PortResult("127.0.0.1", 80, "tcp", "http", "",
                "Server: Apache/2.4.49 (Debian)", "", true));
        in.add(Pump.POISON);
        module.execute(new TargetContext("example.com", List.of()));

        List<Object> items = new ArrayList<>(out);
        assertEquals(4, items.size(), "port + two confirmed findings + pill");
        assertInstanceOf(PortResult.class, items.get(0));
        assertEquals(Pump.POISON, items.get(3));
        List<Finding> findings = items.stream()
                .filter(Finding.class::isInstance)
                .map(Finding.class::cast)
                .toList();
        assertEquals(2, findings.size(), "CVE-2021-41773 and CVE-2024-38475 confirmed");
        assertTrue(findings.stream().allMatch(f -> "KEV_MATCH".equals(f.type())));
        assertTrue(findings.stream().allMatch(f -> "HIGH".equals(f.severity())),
                "version confirmed in a vulnerable range");
        assertTrue(findings.stream().allMatch(f -> "kev-analyze".equals(f.moduleId())));
        assertTrue(findings.stream().allMatch(f -> f.hostId() == null),
                "decision aaa: host_id stays null in memory");
        List<JsonNode> details = findings.stream().map(f -> {
            try {
                return Json.MAPPER.readTree(f.detailJson());
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        }).toList();

        JsonNode traversal = details.stream()
                .filter(d -> "CVE-2021-41773".equals(d.get("cve").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CVE-2021-41773 missing"));
        assertEquals("127.0.0.1", traversal.get("host").asText());
        assertEquals(80, traversal.get("port").asInt());
        assertEquals("CONFIRMED", traversal.get("confidence").asText());
        assertTrue(traversal.get("ransomware").asBoolean(), "CVE-2021-41773 ransomware Known");

        assertTrue(got.stream().anyMatch(e -> e instanceof ScanEvent.FindingEmitted),
                "FindingEmitted event published");
    }

    @Test
    void versionlessBannerStaysCandidateAtMedium() throws Exception {
        KevAnalyzeModule module = new KevAnalyzeModule(in, out, events, token,
                new KevMatcher(KevClient.bundled()));

        in.add(new PortResult("127.0.0.1", 8080, "tcp", "http", "",
                "Apache-Coyote/1.1", "", true));
        in.add(Pump.POISON);
        module.execute(new TargetContext("example.com", List.of()));

        List<Object> items = new ArrayList<>(out);
        assertInstanceOf(PortResult.class, items.get(0));
        List<Finding> findings = items.stream()
                .filter(Finding.class::isInstance)
                .map(Finding.class::cast)
                .toList();
        assertTrue(!findings.isEmpty(), "at least one KEV candidate for Tomcat");
        for (Finding f : findings) {
            assertEquals("MEDIUM", f.severity(), "no version → CANDIDATE → MEDIUM");
            JsonNode detail = Json.MAPPER.readTree(f.detailJson());
            assertEquals("CANDIDATE", detail.get("confidence").asText());
            assertEquals("Tomcat", detail.get("product").asText());
        }
    }

    @Test
    void bannersWithoutKeveVerdictPassThroughWithoutFindings() throws Exception {
        KevAnalyzeModule module = new KevAnalyzeModule(in, out, events, token,
                new KevMatcher(KevClient.bundled()));

        in.add(new PortResult("127.0.0.1", 22, "tcp", "ssh", "",
                "SSH-2.0-OpenSSH_9.2", "", true));
        in.add(new PortResult("127.0.0.1", 9999, "tcp", "", "", "", "", true));
        in.add(Pump.POISON);
        module.execute(new TargetContext("example.com", List.of()));

        List<Object> items = new ArrayList<>(out);
        assertEquals(3, items.size(), "two ports + pill, zero findings");
        assertTrue(items.stream().noneMatch(Finding.class::isInstance));
        assertEquals("SSH-2.0-OpenSSH_9.2", ((PortResult) items.get(0)).banner());
    }
}
