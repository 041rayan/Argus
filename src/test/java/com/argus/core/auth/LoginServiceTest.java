package com.argus.core.auth;

import com.argus.db.AuditDAO;
import com.argus.db.Database;
import com.argus.db.OperatorDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginServiceTest {

    @TempDir
    Path tmp;

    private LoginService service;
    private Database db;

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tmp.resolve("argus-test.db"));
        service = new LoginService(new OperatorDAO(db), new AuditDAO(db));
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void firstRunCreateThenLogin() throws SQLException {
        assertFalse(service.hasAccounts());
        service.createAccount("alice", pw("hunter2 hunter2"));
        assertTrue(service.hasAccounts());

        LoginService.LoginResult ok = service.login("ALICE", pw("hunter2 hunter2"));
        assertTrue(ok.success());
        assertEquals(32, ok.vaultKey().length);
        assertEquals(List.of("LOGIN_SUCCESS"), auditActions());
    }

    @Test
    void fiveFailuresLockAndAudit() throws SQLException {
        service.createAccount("bob", pw("correct horse"));
        for (int i = 1; i < 5; i++) {
            LoginService.LoginResult r = service.login("bob", pw("wrong"));
            assertFalse(r.success());
            assertTrue(r.message().contains("attempt(s) left"), r.message());
        }
        LoginService.LoginResult fifth = service.login("bob", pw("wrong"));
        assertFalse(fifth.success());
        assertTrue(fifth.message().contains("locked"), fifth.message());

        LoginService.LoginResult whileLocked = service.login("bob", pw("correct horse"));
        assertFalse(whileLocked.success(), "password must not matter while locked");

        List<String> actions = auditActions();
        assertEquals(6, actions.stream().filter("LOGIN_FAILURE"::equals).count(), actions.toString());
        assertEquals(1, actions.stream().filter("LOCKED"::equals).count(), actions.toString());

        // Lock expiry: move the deadline into the past, password works again.
        expireLock("bob");
        LoginService.LoginResult after = service.login("bob", pw("correct horse"));
        assertTrue(after.success());
        assertEquals(List.of("LOGIN_SUCCESS"), auditActionsAfterLast());
    }

    @Test
    void lockDoublesAndCaps() {
        assertEquals(Duration.ofSeconds(30), LoginService.lockDuration(5));
        assertEquals(Duration.ofSeconds(60), LoginService.lockDuration(6));
        assertEquals(Duration.ofSeconds(120), LoginService.lockDuration(7));
        assertEquals(Duration.ofSeconds(480), LoginService.lockDuration(9));
        assertEquals(Duration.ofSeconds(900), LoginService.lockDuration(10));
        assertEquals(Duration.ofSeconds(900), LoginService.lockDuration(1000));
    }

    @Test
    void unknownUsernameIsAuditedAndToldNothingExtra() throws SQLException {
        LoginService.LoginResult r = service.login("ghost", pw("x"));
        assertFalse(r.success());
        assertFalse(r.message().contains("locked"));
        assertEquals(List.of("LOGIN_FAILURE"), auditActions());
    }

    private void expireLock(String username) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE operator SET locked_until = '2000-01-01T00:00:00Z' WHERE username = ?")) {
            p.setString(1, username);
            p.executeUpdate();
        }
    }

    private List<String> auditActions() throws SQLException {
        return query("SELECT action FROM audit_log ORDER BY id");
    }

    private List<String> auditActionsAfterLast() throws SQLException {
        List<String> all = query("SELECT action FROM audit_log ORDER BY id");
        // last event only, after the unlock login
        return List.of(all.get(all.size() - 1));
    }

    private List<String> query(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = db.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }
}
