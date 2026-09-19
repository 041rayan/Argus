package com.argus.db;

import com.argus.core.model.Target;
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

class TargetDaoTest {

    @TempDir
    Path tmp;

    private TargetDAO dao;

    @BeforeEach
    void setUp() throws SQLException {
        dao = new TargetDAO(new Database(tmp.resolve("argus-test.db")));
    }

    private static Target sample() {
        return new Target(null, "Lab", "example.com",
                List.of("10.0.0.0/8", "192.168.1.0/24"), "quick", Instant.now());
    }

    @Test
    void crudRoundTrip() throws SQLException {
        long id = dao.insert(sample());
        Target saved = dao.find(id).orElseThrow();
        assertEquals("example.com", saved.domain());
        assertEquals(List.of("10.0.0.0/8", "192.168.1.0/24"), saved.scopeCidrs());

        long emptyId = dao.insert(new Target(null, "NoScope", "empty.example", List.of(), "quick", Instant.now()));
        assertEquals(List.of(), dao.find(emptyId).orElseThrow().scopeCidrs());

        dao.update(new Target(id, "Lab 2", "example.com", List.of("10.0.0.0/8"), "full", saved.createdAt()));
        Target updated = dao.find(id).orElseThrow();
        assertEquals("Lab 2", updated.label());
        assertEquals("full", updated.profile());
        assertEquals(saved.createdAt(), updated.createdAt());

        assertEquals(2, dao.list().size());

        assertTrue(dao.delete(id));
        assertFalse(dao.find(id).isPresent());
        assertFalse(dao.delete(id));
    }

    @Test
    void duplicateDomainRejectedCaseInsensitive() throws SQLException {
        dao.insert(sample());
        assertTrue(dao.targetExists("EXAMPLE.COM"));
        assertThrows(IllegalArgumentException.class, () -> dao.insert(sample()));
    }
}
