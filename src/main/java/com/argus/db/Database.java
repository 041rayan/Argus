package com.argus.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** SQLite connection factory: controlled path, per-connection pragmas, schema. */
public final class Database {

    private static final String AUDIT_TABLE = """
            CREATE TABLE IF NOT EXISTS audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                operator_id INTEGER REFERENCES operator(id),
                action TEXT NOT NULL,
                detail TEXT,
                at TEXT NOT NULL
            )""";

    private static final String OPERATOR_TABLE = """
            CREATE TABLE IF NOT EXISTS operator (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL COLLATE NOCASE UNIQUE,
                auth_salt BLOB NOT NULL,
                auth_hash BLOB NOT NULL,
                vault_salt BLOB NOT NULL,
                failed_attempts INTEGER NOT NULL DEFAULT 0,
                locked_until TEXT,
                created_at TEXT NOT NULL
            )""";

    private static final String API_KEYS_TABLE = """
            CREATE TABLE IF NOT EXISTS api_keys (
                operator_id INTEGER NOT NULL REFERENCES operator(id),
                provider TEXT NOT NULL COLLATE NOCASE,
                encrypted_key BLOB NOT NULL,
                iv BLOB NOT NULL,
                PRIMARY KEY (operator_id, provider)
            )""";

    private static final String TARGET_TABLE = """
            CREATE TABLE IF NOT EXISTS target (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                label TEXT NOT NULL,
                domain TEXT NOT NULL COLLATE NOCASE UNIQUE,
                scope_cidrs TEXT NOT NULL DEFAULT '',
                profile TEXT NOT NULL DEFAULT 'quick',
                created_at TEXT NOT NULL
            )""";

    private final Path file;

    public Database(Path file) {
        this.file = file;
    }

    /** Default location: ~/.argus/argus.db */
    public static Database inUserHome() {
        return new Database(Path.of(System.getProperty("user.home"), ".argus", "argus.db"));
    }

    /**
     * Open a connection: create the parent directory, apply pragmas, create
     * missing tables. WAL is persistent; busy_timeout and foreign_keys are
     * per connection, so all pragmas run on every open.
     */
    public Connection connect() throws SQLException {
        createParentDir();
        Connection c = DriverManager.getConnection(url());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute(TARGET_TABLE);
            s.execute(OPERATOR_TABLE);
            s.execute(AUDIT_TABLE);
            s.execute(API_KEYS_TABLE);
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    private String url() {
        return "jdbc:sqlite:" + file;
    }

    private void createParentDir() throws SQLException {
        Path dir = file.getParent();
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new SQLException("cannot create directory " + dir, e);
        }
    }
}
