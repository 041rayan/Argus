package com.argus.db;

import com.argus.core.crypto.VaultCipher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * api_keys rows: the key is AES-GCM-encrypted with the caller's in-memory
 * vault key before it touches the database; find() decrypts it back.
 * The caller wipes the returned plaintext.
 */
public final class ApiKeysDAO {

    private final Database db;

    public ApiKeysDAO(Database db) {
        this.db = db;
    }

    /** Insert or replace the key for (operator, provider). */
    public void upsert(long operatorId, String provider, byte[] plaintextKey, byte[] vaultKey) throws SQLException {
        byte[][] sealed = VaultCipher.encrypt(vaultKey, plaintextKey);
        String sql = """
                INSERT INTO api_keys (operator_id, provider, encrypted_key, iv) VALUES (?, ?, ?, ?)
                ON CONFLICT(operator_id, provider) DO UPDATE SET encrypted_key = excluded.encrypted_key, iv = excluded.iv""";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, operatorId);
            p.setString(2, provider);
            p.setBytes(3, sealed[1]);
            p.setBytes(4, sealed[0]);
            p.executeUpdate();
        }
    }

    /** Decrypted plaintext key, or empty. Throws if the vault key is wrong — GCM tag fails. */
    public Optional<byte[]> find(long operatorId, String provider, byte[] vaultKey) throws SQLException {
        String sql = "SELECT encrypted_key, iv FROM api_keys WHERE operator_id = ? AND provider = ?";
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, operatorId);
            p.setString(2, provider);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(VaultCipher.decrypt(vaultKey, rs.getBytes("iv"), rs.getBytes("encrypted_key")));
            }
        }
    }

    /** Providers with a stored key for this operator — for "configured" status, never the key itself. */
    public Set<String> configuredProviders(long operatorId) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement("SELECT provider FROM api_keys WHERE operator_id = ?")) {
            p.setLong(1, operatorId);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("provider"));
                }
            }
        }
        return out;
    }

    public boolean delete(long operatorId, String provider) throws SQLException {
        try (Connection c = db.connect();
             PreparedStatement p = c.prepareStatement(
                     "DELETE FROM api_keys WHERE operator_id = ? AND provider = ?")) {
            p.setLong(1, operatorId);
            p.setString(2, provider);
            return p.executeUpdate() > 0;
        }
    }
}
