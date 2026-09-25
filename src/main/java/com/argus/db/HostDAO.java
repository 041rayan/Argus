package com.argus.db;

import com.argus.core.model.Host;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Read side for hosts (results view). */
public final class HostDAO {

    private final Database db;

    public HostDAO(Database db) {
        this.db = db;
    }

    /** Hosts of one scan: alive first, then alphabetical. */
    public List<Host> listByScan(long scanId) throws SQLException {
        String sql = "SELECT * FROM host WHERE scan_id = ? ORDER BY is_alive DESC, subdomain";
        List<Host> out = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, scanId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.add(new Host(
                            rs.getLong("id"),
                            rs.getLong("scan_id"),
                            rs.getString("subdomain"),
                            rs.getString("ip"),
                            rs.getInt("is_alive") != 0,
                            rs.getString("country"),
                            rs.getString("asn"),
                            rs.getString("org")));
                }
            }
        }
        return out;
    }
}
