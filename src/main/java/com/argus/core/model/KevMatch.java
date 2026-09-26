package com.argus.core.model;

/**
 * One KEV verdict for a banner (CORE.md): CONFIRMED when the version sits in
 * a known vulnerable range, CANDIDATE when there is no range data or no
 * version. A version outside every known range produces no match at all —
 * that banner is patched.
 */
public record KevMatch(String cve, String name, String product,
                       Confidence confidence, boolean ransomware) {

    public enum Confidence {
        CONFIRMED, CANDIDATE
    }
}
