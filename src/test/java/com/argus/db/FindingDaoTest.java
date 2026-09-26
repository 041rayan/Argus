package com.argus.db;

import com.argus.core.model.Finding;
import com.argus.core.model.ScanSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FindingDaoTest {

    @TempDir
    Path tmp;

    private ScanDAO scans;
    private FindingDAO findings;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tmp.resolve("argus-test.db"));
        scans = new ScanDAO(db);
        findings = new FindingDAO(db);
        try (var c = db.connect();
             var p = c.prepareStatement(
                     "INSERT INTO operator (username, auth_salt, auth_hash, vault_salt, created_at) "
                             + "VALUES ('tester', X'00', X'00', X'00', '2026-09-25T00:00:00Z')")) {
            p.executeUpdate();
        }
    }

    @Test
    void listByScanRoundTripsFindingsWithNullHost() throws SQLException {
        long scanId = scans.insertScanResults(
                new ScanSummary(null, 1, "example.com", "quick", ScanSummary.Status.COMPLETED,
                        Instant.now(), Instant.now()),
                List.of(),
                List.of(),
                List.of(
                        new Finding(0, null, "kev-analyze", "KEV_MATCH", "HIGH",
                                "{\"host\":\"www.example.com\",\"port\":80}"),
                        new Finding(0, null, "kev-analyze", "KEV_MATCH", "MEDIUM",
                                "{\"host\":\"www.example.com\",\"port\":8080}")));

        List<Finding> got = findings.listByScan(scanId);
        assertEquals(2, got.size());
        for (Finding f : got) {
            assertEquals(scanId, f.scanId());
            assertNull(f.hostId(), "host_id stays null while in memory (decision aaa)");
            assertEquals("kev-analyze", f.moduleId());
            assertEquals("KEV_MATCH", f.type());
            assertTrue(f.detailJson().contains("www.example.com"));
        }

        assertTrue(findings.listByScan(9999).isEmpty(), "unknown scan has no findings");
    }
}
