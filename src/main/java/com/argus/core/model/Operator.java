package com.argus.core.model;

import java.time.Instant;

/** Operator account: login credentials, vault salts, lock state. */
public record Operator(Long id, String username, byte[] authSalt, byte[] authHash,
                       byte[] vaultSalt, int failedAttempts, Instant lockedUntil, Instant createdAt) {

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
