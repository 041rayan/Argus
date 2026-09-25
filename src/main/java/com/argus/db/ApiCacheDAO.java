package com.argus.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** api_cache upsert and TTL read (DB.md). One row per (provider, resource). */
public final class ApiCacheDAO {

    private final Database db;

    public ApiCacheDAO(Database db) {
        this.db = db;
    }

    /** Cached body, or empty when missing or expired. */
    public Optional<String> find(String provider, String resource) throws SQLException {
        String sql = "SELECT response_json FROM api_cache "
                + "WHERE provider = ? AND resource = ? AND expires_at > ?";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, provider);
            p.setString(2, resource);
            p.setString(3, Instant.now().toString());
            try (var rs = p.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
            }
        }
    }

    /** Upsert on (provider, resource): replaces body and refreshes both timestamps. */
    public void put(String provider, String resource, String body, Duration ttl) throws SQLException {
        String sql = """
                INSERT INTO api_cache (provider, resource, response_json, fetched_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(provider, resource) DO UPDATE SET
                    response_json = excluded.response_json,
                    fetched_at = excluded.fetched_at,
                    expires_at = excluded.expires_at""";
        Instant now = Instant.now();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, provider);
            p.setString(2, resource);
            p.setString(3, body);
            p.setString(4, now.toString());
            p.setString(5, now.plus(ttl).toString());
            p.executeUpdate();
        }
    }
}
