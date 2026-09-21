package com.argus.db;

import com.argus.core.model.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorDaoTest {

    @TempDir
    Path tmp;

    private OperatorDAO dao;

    @BeforeEach
    void setUp() throws SQLException {
        dao = new OperatorDAO(new Database(tmp.resolve("argus-test.db")));
    }

    private static byte[] b(int n) {
        return new byte[n];
    }

    @Test
    void createAndFindRoundTrip() throws SQLException {
        long id = dao.create("alice", b(16), b(32), b(16));
        Operator op = dao.findByUsername("ALICE").orElseThrow();
        assertEquals(id, op.id());
        assertEquals("alice", op.username());
        assertEquals(0, op.failedAttempts());
        assertFalse(op.isLocked(Instant.now()));
    }

    @Test
    void duplicateUsernameRejectedCaseInsensitive() throws SQLException {
        dao.create("alice", b(16), b(32), b(16));
        assertTrue(dao.operatorExists("Alice"));
        assertThrows(IllegalArgumentException.class,
                () -> dao.create("ALICE", b(16), b(32), b(16)));
    }

    @Test
    void lockStateUpdatesAndClears() throws SQLException {
        long id = dao.create("bob", b(16), b(32), b(16));
        Instant deadline = Instant.now().plusSeconds(30);

        dao.updateLockState(id, 5, deadline);
        Operator locked = dao.findByUsername("bob").orElseThrow();
        assertEquals(5, locked.failedAttempts());
        assertTrue(locked.isLocked(Instant.now()));
        assertFalse(locked.isLocked(deadline.plusSeconds(1)));

        dao.clearLockState(id);
        Operator cleared = dao.findByUsername("bob").orElseThrow();
        assertEquals(0, cleared.failedAttempts());
        assertFalse(cleared.isLocked(Instant.now()));
    }

    @Test
    void findMissingReturnsEmpty() throws SQLException {
        assertTrue(dao.findByUsername("nobody").isEmpty());
    }
}
