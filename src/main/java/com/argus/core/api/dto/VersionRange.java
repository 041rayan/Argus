package com.argus.core.api.dto;

/**
 * One affected-version window from {@code ranges.json} (JSON.md): vulnerable
 * when {@code introduced <= version < fixed}. A CVE with several windows
 * appears in several rows.
 */
public record VersionRange(String cveId, String product, String introduced, String fixed) {
}
