package com.argus.db;

import com.argus.core.model.PortResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read side for ports (Entry Points view). One DAO per table (DB.md). */
public final class PortDAO {

    private final Database db;

    public PortDAO(Database db) {
        this.db = db;
    }

    /** Open ports of one scan via the host join, ordered by port number. */
    public List<PortResult> listByScan(long scanId) throws SQLException {
        String sql = """
                SELECT h.subdomain, p.port_number, p.protocol, p.service,
                       p.version, p.banner, p.title
                FROM port p
                JOIN host h ON p.host_id = h.id
                WHERE h.scan_id = ?
                ORDER BY p.port_number""";
        List<PortResult> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, scanId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.add(new PortResult(
                            rs.getString("subdomain"),
                            rs.getInt("port_number"),
                            rs.getString("protocol"),
                            rs.getString("service"),
                            rs.getString("version"),
                            rs.getString("banner"),
                            rs.getString("title"),
                            true)); // only open ports are queued (CORE.md)
                }
            }
        }
        return out;
    }
}
