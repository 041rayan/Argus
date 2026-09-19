package com.argus.db;

import com.argus.core.model.Target;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD over the target table. Prepared statements, try-with-resources, one connection per operation. */
public final class TargetDAO {

    private final Database db;

    public TargetDAO(Database db) {
        this.db = db;
    }

    /** Lab 1 duplicate check: domain match is case-insensitive. */
    public boolean targetExists(String domain) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT 1 FROM target WHERE domain = ?")) {
            p.setString(1, domain);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Insert; returns the generated id. Rejects duplicate domains. */
    public long insert(Target t) throws SQLException {
        if (targetExists(t.domain())) {
            throw new IllegalArgumentException("target domain already exists");
        }
        String sql = "INSERT INTO target (label, domain, scope_cidrs, profile, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, t.label());
            p.setString(2, t.domain());
            p.setString(3, String.join(",", t.scopeCidrs()));
            p.setString(4, t.profile());
            p.setString(5, t.createdAt().toString());
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<Target> find(long id) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT * FROM target WHERE id = ?")) {
            p.setLong(1, id);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? Optional.of(row(rs)) : Optional.empty();
            }
        }
    }

    /** Updates label, domain, scope and profile; created_at is immutable. */
    public void update(Target t) throws SQLException {
        String sql = "UPDATE target SET label = ?, domain = ?, scope_cidrs = ?, profile = ? WHERE id = ?";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, t.label());
            p.setString(2, t.domain());
            p.setString(3, String.join(",", t.scopeCidrs()));
            p.setString(4, t.profile());
            p.setLong(5, t.id());
            p.executeUpdate();
        }
    }

    /** true when a row was deleted. */
    public boolean delete(long id) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("DELETE FROM target WHERE id = ?")) {
            p.setLong(1, id);
            return p.executeUpdate() > 0;
        }
    }

    public List<Target> list() throws SQLException {
        List<Target> out = new ArrayList<>();
        try (Connection c = db.connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM target ORDER BY id")) {
            while (rs.next()) {
                out.add(row(rs));
            }
        }
        return out;
    }

    private static Target row(ResultSet rs) throws SQLException {
        return new Target(
                rs.getLong("id"),
                rs.getString("label"),
                rs.getString("domain"),
                csv(rs.getString("scope_cidrs")),
                rs.getString("profile"),
                Instant.parse(rs.getString("created_at")));
    }

    /** Empty list is stored as "". */
    private static List<String> csv(String s) {
        return s.isEmpty() ? List.of() : List.of(s.split(","));
    }
}
