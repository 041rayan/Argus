package com.argus.core.model;

import java.time.Instant;
import java.util.List;

/** Scan target: label, root domain, scope CIDRs, port profile. */
public record Target(Long id, String label, String domain, List<String> scopeCidrs,
                     String profile, Instant createdAt) {
}
