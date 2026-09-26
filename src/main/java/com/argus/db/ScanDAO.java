package com.argus.db;

import com.argus.core.model.Finding;
import com.argus.core.model.Host;
import com.argus.core.model.PortResult;
import com.argus.core.model.ScanSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scan lifecycle rows: the cross-table write ({@code finishScan}) and
 * the cascade delete live here — scan is the root, one connection, one
 * transaction (DB.md). Host/port/finding DAOs arrive with their first reader.
 */
public final class ScanDAO {

    private final Database db;

    public ScanDAO(Database db) {
        this.db = db;
    }

    /**
     * Lifecycle write, phase 1: the scan row lands RUNNING the moment the run
     * starts (finished_at null), so History shows the scan live. Returns the
     * generated id; the caller passes it to {@link #finishScan}.
     */
    public long beginScan(ScanSummary scan) throws SQLException {
        try (Connection c = db.connect()) {
            return insertScan(c, scan);
        }
    }

    /**
     * Lifecycle write, phase 2: one transaction — finalize the scan row
     * (status, finished_at, error_message) and insert hosts, ports and
     * findings (PortResult.host maps against subdomain or IP; findings keep
     * their hostId as given). {@code scanId} from {@link #beginScan};
     * negative means begin failed — the scan row is inserted here instead, so
     * bookkeeping can never cost the results. A vanished row (deleted
     * mid-run) is re-inserted for the same reason. Returns the scan id.
     */
    public long finishScan(long scanId, ScanSummary scan, String errorMessage,
                           List<Host> hosts, List<PortResult> ports,
                           List<Finding> findings) throws SQLException {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                long id = scanId >= 0 ? updateScan(c, scanId, scan, errorMessage) : -1;
                if (id < 0) {
                    id = insertScan(c, scan);
                }
                insertResults(c, id, hosts, ports, findings);
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Startup sweep: a row still RUNNING outlived its session (single
     * instance — DB.md). Mark it FAILED with the reason so History carries no
     * phantom runs. Returns how many rows were fixed.
     */
    public int reconcileStale() throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE scan SET status = 'FAILED', finished_at = ?, "
                             + "error_message = 'app closed mid-scan' WHERE status = 'RUNNING'")) {
            p.setString(1, Instant.now().toString());
            return p.executeUpdate();
        }
    }

    /**
     * Host/port/finding half of a results transaction; the scan row is
     * handled by the caller. Ports map to host rows by {@code PortResult.host}
     * against subdomain or IP; findings keep their hostId as given.
     */
    private static void insertResults(Connection c, long scanId, List<Host> hosts,
                                      List<PortResult> ports, List<Finding> findings) throws SQLException {
        Map<String, Long> hostIds = new HashMap<>();
        for (Host h : hosts) {
            long id = insertHost(c, scanId, h);
            hostIds.put(h.subdomain(), id);
            hostIds.put(h.ip(), id);
        }
        for (PortResult p : ports) {
            Long hostId = hostIds.get(p.host());
            if (hostId == null) {
                throw new SQLException("port result for unknown host: " + p.host());
            }
            insertPort(c, hostId, p);
        }
        for (Finding f : findings) {
            insertFinding(c, scanId, f);
        }
    }

    /** Finalizes the RUNNING row; @return the id, or -1 when the row is gone. */
    private static long updateScan(Connection c, long scanId, ScanSummary s,
                                   String errorMessage) throws SQLException {
        String sql = "UPDATE scan SET status = ?, finished_at = ?, error_message = ? WHERE id = ?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, s.status().name());
            if (s.finishedAt() == null) {
                p.setObject(2, null);
            } else {
                p.setString(2, s.finishedAt().toString());
            }
            p.setString(3, errorMessage);
            p.setLong(4, scanId);
            return p.executeUpdate() == 1 ? scanId : -1;
        }
    }

    /** Cascade delete: ports, findings, hosts, then the scan — one transaction. */
    public boolean deleteScan(long scanId) throws SQLException {        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                int deleted = delete(c,
                        "DELETE FROM port WHERE host_id IN (SELECT id FROM host WHERE scan_id = ?)", scanId);
                deleted += delete(c, "DELETE FROM finding WHERE scan_id = ?", scanId);
                deleted += delete(c, "DELETE FROM host WHERE scan_id = ?", scanId);
                deleted += delete(c, "DELETE FROM scan WHERE id = ?", scanId);
                c.commit();
                return deleted > 0;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** All scans, newest first (results/history view). */
    public List<ScanSummary> list() throws SQLException {
        List<ScanSummary> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT * FROM scan ORDER BY id DESC");
             ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                out.add(scanRow(rs));
            }
        }
        return out;
    }

    private static ScanSummary scanRow(ResultSet rs) throws SQLException {
        String finished = rs.getString("finished_at");
        return new ScanSummary(
                rs.getLong("id"),
                rs.getLong("operator_id"),
                rs.getString("target"),
                rs.getString("profile"),
                ScanSummary.Status.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("started_at")),
                finished == null ? null : Instant.parse(finished));
    }

    private static long insertScan(Connection c, ScanSummary s) throws SQLException {
        String sql = "INSERT INTO scan (operator_id, target, profile, status, started_at, finished_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, s.operatorId());
            p.setString(2, s.target());
            p.setString(3, s.profile());
            p.setString(4, s.status().name());
            p.setString(5, s.startedAt().toString());
            if (s.finishedAt() == null) {
                p.setObject(6, null);
            } else {
                p.setString(6, s.finishedAt().toString());
            }
            p.executeUpdate();
            try (var keys = p.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertHost(Connection c, long scanId, Host h) throws SQLException {
        String sql = "INSERT INTO host (scan_id, subdomain, ip, is_alive, country, asn, org) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, scanId);
            p.setString(2, h.subdomain());
            p.setString(3, h.ip());
            p.setInt(4, h.alive() ? 1 : 0);
            p.setString(5, h.country());
            p.setString(6, h.asn());
            p.setString(7, h.org());
            p.executeUpdate();
            try (var keys = p.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static void insertPort(Connection c, long hostId, PortResult p) throws SQLException {
        String sql = "INSERT INTO port (host_id, port_number, protocol, service, version, banner, title) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, hostId);
            ps.setInt(2, p.port());
            ps.setString(3, p.protocol());
            ps.setString(4, p.service());
            ps.setString(5, p.version());
            ps.setString(6, p.banner());
            ps.setString(7, p.title());
            ps.executeUpdate();
        }
    }

    private static void insertFinding(Connection c, long scanId, Finding f) throws SQLException {
        String sql = "INSERT INTO finding (scan_id, host_id, module_id, type, severity, detail_json) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, scanId);
            if (f.hostId() == null) {
                p.setObject(2, null);
            } else {
                p.setLong(2, f.hostId());
            }
            p.setString(3, f.moduleId());
            p.setString(4, f.type());
            p.setString(5, f.severity());
            p.setString(6, f.detailJson());
            p.executeUpdate();
        }
    }

    private static int delete(Connection c, String sql, long scanId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, scanId);
            return p.executeUpdate();
        }
    }
}
