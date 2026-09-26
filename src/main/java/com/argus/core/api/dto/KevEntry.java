package com.argus.core.api.dto;

/** One KEV entry (JSON.md fields used). */
public record KevEntry(String cveID, String vendorProject, String product,
                       String vulnerabilityName, String knownRansomwareCampaignUse) {
}
