package com.argus.core.pipeline;

import com.argus.core.model.Target;

import java.util.List;
import java.util.Locale;

/**
 * Scope authority (CORE.md): a host is in scope when its name is the target
 * domain or a subdomain of it, or its IPv4 sits inside a scope CIDR.
 * Modules call {@link #inScope(String)} before every network action —
 * out of scope means skip, no exceptions.
 */
public final class TargetContext {

    private final String domain;
    private final List<String> scopeCidrs;

    public TargetContext(String domain, List<String> scopeCidrs) {
        this.domain = domain.toLowerCase(Locale.ROOT);
        this.scopeCidrs = List.copyOf(scopeCidrs);
    }

    public static TargetContext of(Target target) {
        return new TargetContext(target.domain(), target.scopeCidrs());
    }

    public String domain() {
        return domain;
    }

    public boolean inScope(String hostOrIp) {
        if (hostOrIp == null || hostOrIp.isBlank()) {
            return false;
        }
        String h = hostOrIp.toLowerCase(Locale.ROOT);
        // endsWith alone would wrongly match "notexample.com"
        if (h.equals(domain) || h.endsWith("." + domain)) {
            return true;
        }
        for (String cidr : scopeCidrs) {
            if (inCidr(h, cidr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inCidr(String ip, String cidr) {
        String[] parts = cidr.trim().split("/");
        if (parts.length != 2) {
            return false;
        }
        long base = ipv4(parts[0]);
        long addr = ipv4(ip);
        if (base == -1 || addr == -1) {
            return false;
        }
        try {
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            int shift = 32 - prefix;
            return (addr >>> shift) == (base >>> shift);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** IPv4 as a 32-bit long, or -1 when unparseable. */
    private static long ipv4(String s) {
        String[] octets = s.trim().split("\\.", -1);
        if (octets.length != 4) {
            return -1;
        }
        long value = 0;
        for (String octet : octets) {
            try {
                int n = Integer.parseInt(octet);
                if (n < 0 || n > 255) {
                    return -1;
                }
                value = (value << 8) | n;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return value;
    }
}
