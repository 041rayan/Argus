package com.argus.db;

import com.argus.core.model.Host;
import com.argus.core.model.ScanSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostDaoTest {

    @TempDir
    Path tmp;

    private ScanDAO scans;
    private HostDAO hosts;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tmp.resolve("argus-test.db"));
        scans = new ScanDAO(db);
        hosts = new HostDAO(db);
        try (var c = db.connect();
             var p = c.prepareStatement(
                     "INSERT INTO operator (username, auth_salt, auth_hash, vault_salt, created_at) "
                             + "VALUES ('tester', X'00', X'00', X'00', '2026-09-25T00:00:00Z')")) {
            p.executeUpdate();
        }
    }

    @Test
    void listByScanReturnsAliveHostsFirst() throws SQLException {
        long scanId = scans.insertScanResults(
                new ScanSummary(null, 1, "example.com", "quick", ScanSummary.Status.COMPLETED,
                        Instant.now(), Instant.now()),
                List.of(
                        new Host(null, -1, "zeta.example.com", "93.184.216.34", true, "", "", ""),
                        new Host(null, -1, "alpha.example.com", "", false, "", "", ""),
                        new Host(null, -1, "mid.example.com", "93.184.216.35", true, "", "", "")),
                List.of(), List.of());

        List<Host> got = hosts.listByScan(scanId);
        assertEquals(3, got.size());
        assertTrue(got.get(0).alive());
        assertEquals("mid.example.com", got.get(0).subdomain(), "alive, then alphabetical");
        assertEquals("zeta.example.com", got.get(1).subdomain());
        assertEquals("alpha.example.com", got.get(2).subdomain(), "dead last");

        assertTrue(hosts.listByScan(9999).isEmpty(), "unknown scan has no hosts");
    }
}
