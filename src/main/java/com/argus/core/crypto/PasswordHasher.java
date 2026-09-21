package com.argus.core.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * Password verifier: PBKDF2WithHmacSHA256, two derivations with different
 * salts. Never logs or returns the password; callers own the
 * char[] and should wipe it after the call.
 */
public final class PasswordHasher {

    /** OWASP 2023+ for PBKDF2-HMAC-SHA256 (ADR-011). */
    static final int ITERATIONS = 600_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    /** 32-byte verifier stored on operator.auth_hash. */
    public static byte[] hash(char[] password, byte[] salt) {
        return derive(password, salt);
    }

    /** Constant-time comparison. */
    public static boolean verify(char[] password, byte[] salt, byte[] expected) {
        byte[] actual = derive(password, salt);
        try {
            return MessageDigest.isEqual(actual, expected);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    /** Second derivation with the vault salt — key separation. In-memory only; wipe after use. */
    public static byte[] deriveVaultKey(char[] password, byte[] salt) {
        return derive(password, salt);
    }

    private static byte[] derive(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
