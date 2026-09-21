package com.argus.db;

import com.argus.core.model.Operator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

/** CRUD over the operator table. Prepared statements, try-with-resources. */
public final class OperatorDAO {

    private final Database db;

    public OperatorDAO(Database db) {
        this.db = db;
    }

    /** Case-insensitive duplicate check (Lab 1 pattern). */
    public boolean operatorExists(String username) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT 1 FROM operator WHERE username = ?")) {
            p.setString(1, username);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Creates an account; returns the generated id. Rejects duplicate usernames. */
    public long create(String username, byte[] authSalt, byte[] authHash, byte[] vaultSalt) throws SQLException {
        if (operatorExists(username)) {
            throw new IllegalArgumentException("username already exists");
        }
        String sql = "INSERT INTO operator (username, auth_salt, auth_hash, vault_salt, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, username);
            p.setBytes(2, authSalt);
            p.setBytes(3, authHash);
            p.setBytes(4, vaultSalt);
            p.setString(5, Instant.now().toString());
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** First-run detection: are there any accounts yet? */
    public int count() throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM operator");
             ResultSet rs = p.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public Optional<Operator> findByUsername(String username) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT * FROM operator WHERE username = ?")) {
            p.setString(1, username);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(row(rs)) : Optional.empty();
            }
        }
    }

    /** Persists the new failure count and lock deadline (null = not locked). */
    public void updateLockState(long id, int failedAttempts, Instant lockedUntil) throws SQLException {
        String sql = "UPDATE operator SET failed_attempts = ?, locked_until = ? WHERE id = ?";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, failedAttempts);
            p.setString(2, lockedUntil == null ? null : lockedUntil.toString());
            p.setLong(3, id);
            p.executeUpdate();
        }
    }

    /** Successful login: zero the counter, clear the lock. */
    public void clearLockState(long id) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE operator SET failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
            p.setLong(1, id);
            p.executeUpdate();
        }
    }

    private static Operator row(ResultSet rs) throws SQLException {
        String locked = rs.getString("locked_until");
        return new Operator(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getBytes("auth_salt"),
                rs.getBytes("auth_hash"),
                rs.getBytes("vault_salt"),
                rs.getInt("failed_attempts"),
                locked == null ? null : Instant.parse(locked),
                Instant.parse(rs.getString("created_at")));
    }
}
