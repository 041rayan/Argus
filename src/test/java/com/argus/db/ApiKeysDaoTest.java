package com.argus.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeysDaoTest {

    @TempDir
    Path tmp;

    private Database db;
    private ApiKeysDAO dao;
    private long operatorId;
    private final byte[] vaultKey = new byte[32];

    @BeforeEach
    void setUp() throws SQLException {
        db = new Database(tmp.resolve("argus-test.db"));
        dao = new ApiKeysDAO(db);
        vaultKey[0] = 42;
        operatorId = new OperatorDAO(db).create("alice", new byte[16], new byte[32], new byte[16]);
    }

    private static byte[] key(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void roundTrip() throws SQLException {
        dao.upsert(operatorId, "VirusTotal", key("vt-secret-1"), vaultKey);

        byte[] back = dao.find(operatorId, "virustotal", vaultKey).orElseThrow();
        assertArrayEquals(key("vt-secret-1"), back);
        assertEquals(Set.of("VirusTotal"), dao.configuredProviders(operatorId));
    }

    @Test
    void upsertReplacesOldKey() throws SQLException {
        dao.upsert(operatorId, "Shodan", key("first"), vaultKey);
        dao.upsert(operatorId, "Shodan", key("second"), vaultKey);

        assertArrayEquals(key("second"), dao.find(operatorId, "Shodan", vaultKey).orElseThrow());
    }

    @Test
    void wrongVaultKeyFailsClosed() throws SQLException {
        dao.upsert(operatorId, "Shodan", key("secret"), vaultKey);
        byte[] wrong = new byte[32];
        wrong[0] = 1;

        assertThrows(IllegalStateException.class, () -> dao.find(operatorId, "Shodan", wrong));
    }

    @Test
    void deleteRemovesRow() throws SQLException {
        dao.upsert(operatorId, "Shodan", key("secret"), vaultKey);
        assertTrue(dao.delete(operatorId, "Shodan"));
        assertFalse(dao.delete(operatorId, "Shodan"));
        assertTrue(dao.find(operatorId, "Shodan", vaultKey).isEmpty());
        assertTrue(dao.configuredProviders(operatorId).isEmpty());
    }

    @Test
    void missingProviderIsEmpty() throws SQLException {
        assertTrue(dao.find(operatorId, "nope", vaultKey).isEmpty());
    }
}
