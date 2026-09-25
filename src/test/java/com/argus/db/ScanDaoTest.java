package com.argus.db;

import com.argus.core.model.Finding;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import com.argus.core.model.ScanSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanDaoTest {

    @TempDir
    Path tmp;

    private Database db;
    private ScanDAO dao;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tmp.resolve("argus-test.db"));
        dao = new ScanDAO(db);
        try (Connection c = db.connect();
             var p = c.prepareStatement(
                     "INSERT INTO operator (username, auth_salt, auth_hash, vault_salt, created_at) "
                             + "VALUES ('tester', X'00', X'00', X'00', '2026-09-25T00:00:00Z')")) {
            p.executeUpdate();
        }
    }

    private static ScanSummary scan(ScanSummary.Status status) {
        return new ScanSummary(null, 1, "example.com", "quick", status,
                Instant.parse("2026-09-25T00:00:00Z"), Instant.parse("2026-09-25T00:05:00Z"));
    }

    private static List<Host> hosts() {
        return List.of(
                new Host(null, -1, "www.example.com", "93.184.216.34", true, "US", "AS15133", "EdgeCast"),
                new Host(null, -1, "mail.example.com", "93.184.216.35", false, "", "", ""));
    }

    @Test
    void insertScanResultsRoundTrip() throws SQLException {
        long id = dao.insertScanResults(scan(ScanSummary.Status.COMPLETED), hosts(),
                List.of(
                        new PortResult("www.example.com", 443, "TCP", "https", "1.19.4",
                                "nginx", "Example", true),
                        new PortResult("93.184.216.35", 25, "TCP", "smtp", "", "", "", true)),
                List.of(new Finding(-1, null, "kev", "KEV_MATCH", "CRITICAL", "{\"cve\":\"CVE-2021-44228\"}")));

        try (Connection c = open()) {
            assertEquals(1, count(c, "scan"));
            assertEquals("COMPLETED", scalar(c, "SELECT status FROM scan WHERE id = " + id));
            assertEquals(2, count(c, "host"));
            assertEquals(2, count(c, "port"));
            assertEquals(1, count(c, "finding"));
            // ports land on the right hosts: subdomain key and IP key both resolve
            assertEquals("www.example.com", scalar(c,
                    "SELECT h.subdomain FROM port p JOIN host h ON h.id = p.host_id WHERE p.port_number = 443"));
            assertEquals("mail.example.com", scalar(c,
                    "SELECT h.subdomain FROM port p JOIN host h ON h.id = p.host_id WHERE p.port_number = 25"));
            assertEquals("null", String.valueOf(scalar(c, "SELECT host_id FROM finding")));
        }
    }

    @Test
    void unknownPortHostRollsBackEverything() throws SQLException {
        assertThrows(SQLException.class, () -> dao.insertScanResults(
                scan(ScanSummary.Status.COMPLETED), hosts(),
                List.of(new PortResult("nope.example.com", 80, "TCP", "http", "", "", "", true)),
                List.of()));

        try (Connection c = open()) {
            assertEquals(0, count(c, "scan"));
            assertEquals(0, count(c, "host"));
        }
    }

    @Test
    void deleteScanCascades() throws SQLException {
        long id = dao.insertScanResults(scan(ScanSummary.Status.FAILED), hosts(),
                List.of(new PortResult("www.example.com", 443, "TCP", "https", "", "", "", true)),
                List.of(new Finding(-1, null, "kev", "KEV_MATCH", "HIGH", "{}")));

        assertTrue(dao.deleteScan(id));
        assertFalse(dao.deleteScan(id));
        try (Connection c = open()) {
            assertEquals(0, count(c, "scan"));
            assertEquals(0, count(c, "host"));
            assertEquals(0, count(c, "port"));
            assertEquals(0, count(c, "finding"));
        }
    }

    @Test
    void listReturnsNewestFirstWithParsedFields() throws SQLException {
        long first = dao.insertScanResults(scan(ScanSummary.Status.COMPLETED), List.of(), List.of(), List.of());
        long second = dao.insertScanResults(scan(ScanSummary.Status.CANCELLED), List.of(), List.of(), List.of());

        List<ScanSummary> scans = dao.list();
        assertEquals(2, scans.size());
        assertEquals(second, scans.get(0).id(), "newest first");
        assertEquals(first, scans.get(1).id());
        ScanSummary s = scans.get(0);
        assertEquals(ScanSummary.Status.CANCELLED, s.status());
        assertEquals("example.com", s.target());
        assertEquals("quick", s.profile());
        assertEquals(Instant.parse("2026-09-25T00:05:00Z"), s.finishedAt());
    }

    /** Raw JDBC reader: the read-side DAOs deliberately don't exist yet. */
    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + tmp.resolve("argus-test.db"));
    }

    private static int count(Connection c, String table) throws SQLException {
        try (var s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Object scalar(Connection c, String sql) throws SQLException {
        try (var s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1);
        }
    }
}
