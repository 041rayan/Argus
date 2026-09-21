package com.argus.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditDaoTest {

    @TempDir
    Path tmp;

    private Database db;
    private AuditDAO dao;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tmp.resolve("argus-test.db"));
        dao = new AuditDAO(db);
    }

    @Test
    void writesEventWithAndWithoutOperator() throws SQLException {
        long operatorId = new OperatorDAO(db).create("carol", new byte[16], new byte[32], new byte[16]);
        dao.write(operatorId, "LOGIN_SUCCESS", null);
        dao.write(null, "LOGIN_FAILURE", "no matching account");

        assertEquals(List.of("LOGIN_SUCCESS:" + operatorId, "LOGIN_FAILURE:null"), readActions());
    }

    private List<String> readActions() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = db.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT action, operator_id FROM audit_log ORDER BY id")) {
            while (rs.next()) {
                out.add(rs.getString("action") + ":" + rs.getObject("operator_id"));
            }
        }
        assertFalse(out.isEmpty());
        return out;
    }
}
