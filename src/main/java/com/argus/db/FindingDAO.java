package com.argus.db;

import com.argus.core.model.Finding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read side for findings (Entry Points view, export). One DAO per table (DB.md). */
public final class FindingDAO {

    private final Database db;

    public FindingDAO(Database db) {
        this.db = db;
    }

    /** Findings of one scan, ordered for stable display. */
    public List<Finding> listByScan(long scanId) throws SQLException {
        String sql = """
                SELECT scan_id, host_id, module_id, type, severity, detail_json
                FROM finding
                WHERE scan_id = ?
                ORDER BY type, id""";
        List<Finding> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, scanId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    long rowScanId = rs.getLong("scan_id");
                    long hostId = rs.getLong("host_id");
                    boolean hostMissing = rs.wasNull(); // right after the host_id read
                    out.add(new Finding(
                            rowScanId,
                            hostMissing ? null : hostId,
                            rs.getString("module_id"),
                            rs.getString("type"),
                            rs.getString("severity"),
                            rs.getString("detail_json")));
                }
            }
        }
        return out;
    }
}
