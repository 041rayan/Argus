package com.argus.db;

import com.argus.core.model.Finding;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
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

class PortDaoTest {

    @TempDir
    Path tmp;

    private ScanDAO scans;
    private PortDAO ports;

    @BeforeEach
    void setUp() throws SQLException {
        Database db = new Database(tmp.resolve("argus-test.db"));
        scans = new ScanDAO(db);
        ports = new PortDAO(db);
        try (var c = db.connect();
             var p = c.prepareStatement(
                     "INSERT INTO operator (username, auth_salt, auth_hash, vault_salt, created_at) "
                             + "VALUES ('tester', X'00', X'00', X'00', '2026-09-25T00:00:00Z')")) {
            p.executeUpdate();
        }
    }

    @Test
    void listByScanReturnsOpenPortsOrderedByPortNumber() throws SQLException {
        long scanId = scans.finishScan(-1,
                new ScanSummary(null, 1, "example.com", "quick", ScanSummary.Status.COMPLETED,
                        Instant.now(), Instant.now()), null,
                List.of(new Host(null, -1, "www.example.com", "127.0.0.1", true, "", "", "")),
                List.of(
                        new PortResult("www.example.com", 8080, "tcp", "http", "", "", "", true),
                        new PortResult("www.example.com", 80, "tcp", "http", "2.4.49",
                                "Server: Apache/2.4.49", "Home", true)),
                List.of(new Finding(0, null, "kev-analyze", "KEV_MATCH", "HIGH", "{}")));

        List<PortResult> got = ports.listByScan(scanId);
        assertEquals(2, got.size());
        assertEquals(80, got.get(0).port(), "ordered by port number");
        assertEquals("www.example.com", got.get(0).host(), "host comes from the host join");
        assertEquals("2.4.49", got.get(0).version());
        assertEquals("Server: Apache/2.4.49", got.get(0).banner());
        assertEquals("Home", got.get(0).title());
        assertTrue(got.get(0).open(), "only open ports are stored");

        assertTrue(ports.listByScan(9999).isEmpty(), "unknown scan has no ports");
    }
}
