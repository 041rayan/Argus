package com.argus.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCacheDaoTest {

    @TempDir
    Path tmp;

    private ApiCacheDAO dao;

    @BeforeEach
    void setUp() {
        dao = new ApiCacheDAO(new Database(tmp.resolve("argus-test.db")));
    }

    @Test
    void hitUntilExpiryThenMiss() throws SQLException {
        dao.put("crtsh", "example.com", "[{\"sub\":\"a.example.com\"}]", Duration.ofHours(1));
        Optional<String> hit = dao.find("crtsh", "example.com");
        assertTrue(hit.isPresent());
        assertEquals("[{\"sub\":\"a.example.com\"}]", hit.get());

        dao.put("crtsh", "example.com", "[]", Duration.ofSeconds(-1));
        assertTrue(dao.find("crtsh", "example.com").isEmpty(), "expired row must miss");
        assertTrue(dao.find("crtsh", "other.com").isEmpty(), "unknown resource must miss");
        assertTrue(dao.find("other", "example.com").isEmpty(), "unknown provider must miss");
    }

    @Test
    void putUpsertsAndReplaces() throws SQLException {
        dao.put("crtsh", "example.com", "v1", Duration.ofHours(1));
        dao.put("crtsh", "example.com", "v2", Duration.ofHours(1));
        assertEquals("v2", dao.find("crtsh", "example.com").orElseThrow());

        dao.put("ipapi", "93.184.216.34", "geo", Duration.ofDays(7));
        assertTrue(dao.find("crtsh", "example.com").isPresent());
        assertTrue(dao.find("ipapi", "93.184.216.34").isPresent());
    }
}
