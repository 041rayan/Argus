package com.argus.core.api.dto;

import java.util.List;

/** CISA KEV feed envelope (JSON.md): fields used, parsed leniently. */
public record KevCatalog(String title, String catalogVersion, List<KevEntry> vulnerabilities) {
}
