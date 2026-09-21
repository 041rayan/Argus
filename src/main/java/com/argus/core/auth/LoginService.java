package com.argus.core.auth;

import com.argus.core.crypto.PasswordHasher;
import com.argus.core.model.Operator;
import com.argus.db.AuditDAO;
import com.argus.db.OperatorDAO;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Credential check with the crypto spec throttle: five failures lock for 30 s,
 * each further lock doubles up to 15 minutes, every attempt is audited.
 * Synchronous by design — the caller runs it off the FX thread. The caller
 * owns the password char[] and wipes it after the call.
 */
public final class LoginService {

    static final int MAX_FAILURES = 5;
    static final int BASE_LOCK_SECONDS = 30;
    static final int MAX_LOCK_SECONDS = 900;

    public record LoginResult(boolean success, String message, long operatorId, byte[] vaultKey) {
    }

    private final OperatorDAO operators;
    private final AuditDAO audit;

    public LoginService(OperatorDAO operators, AuditDAO audit) {
        this.operators = operators;
        this.audit = audit;
    }

    public boolean hasAccounts() throws SQLException {
        return operators.count() > 0;
    }

    /** First-run account creation: fresh salts, verifier, no audit event in the spec list. */
    public long createAccount(String username, char[] password) throws SQLException {
        byte[] authSalt = PasswordHasher.newSalt();
        byte[] authHash = PasswordHasher.hash(password, authSalt);
        byte[] vaultSalt = PasswordHasher.newSalt();
        return operators.create(username.trim(), authSalt, authHash, vaultSalt);
    }

    public LoginResult login(String username, char[] password) throws SQLException {
        Optional<Operator> found = operators.findByUsername(username.trim());
        if (found.isEmpty()) {
            audit.write(null, "LOGIN_FAILURE", "unknown username");
            return new LoginResult(false, "Unknown account.", -1, null);
        }
        Operator op = found.get();
        Instant now = Instant.now();
        if (op.isLocked(now)) {
            audit.write(op.id(), "LOGIN_FAILURE", "account locked");
            return new LoginResult(false, "Account locked until " + op.lockedUntil() + ".", op.id(), null);
        }
        if (!PasswordHasher.verify(password, op.authSalt(), op.authHash())) {
            return failure(op);
        }
        operators.clearLockState(op.id());
        byte[] vaultKey = PasswordHasher.deriveVaultKey(password, op.vaultSalt());
        audit.write(op.id(), "LOGIN_SUCCESS", null);
        return new LoginResult(true, null, op.id(), vaultKey);
    }

    private LoginResult failure(Operator op) throws SQLException {
        int failures = op.failedAttempts() + 1;
        Instant lockedUntil = failures >= MAX_FAILURES
                ? Instant.now().plus(lockDuration(failures))
                : null;
        operators.updateLockState(op.id(), failures, lockedUntil);
        audit.write(op.id(), "LOGIN_FAILURE", "bad password");
        if (lockedUntil != null) {
            audit.write(op.id(), "LOCKED", "after " + failures + " failures");
            return new LoginResult(false, "Account locked until " + lockedUntil + ".", op.id(), null);
        }
        int left = MAX_FAILURES - failures;
        return new LoginResult(false, "Wrong password (" + left + " attempt(s) left).", op.id(), null);
    }

    /** 5th failure: 30 s, then doubles per failure, capped at 15 min. */
    static Duration lockDuration(int failures) {
        int doublings = failures - MAX_FAILURES;
        long seconds = doublings >= 31 ? MAX_LOCK_SECONDS
                : Math.min((long) BASE_LOCK_SECONDS << doublings, MAX_LOCK_SECONDS);
        return Duration.ofSeconds(seconds);
    }
}
