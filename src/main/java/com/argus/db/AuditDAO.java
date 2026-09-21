package com.argus.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

/** Writes audit events (LOGIN_SUCCESS, LOGIN_FAILURE, LOCKED, ...). detail must never hold secrets. */
public final class AuditDAO {

    private final Database db;

    public AuditDAO(Database db) {
        this.db = db;
    }

    /** operatorId is null for attempts with no matching account. */
    public void write(Long operatorId, String action, String detail) throws SQLException {
        String sql = "INSERT INTO audit_log (operator_id, action, detail, at) VALUES (?, ?, ?, ?)";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            if (operatorId == null) {
                p.setNull(1, Types.INTEGER);
            } else {
                p.setLong(1, operatorId);
            }
            p.setString(2, action);
            p.setString(3, detail);
            p.setString(4, Instant.now().toString());
            p.executeUpdate();
        }
    }
}
