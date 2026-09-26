package com.argus.core.api.dto;

/** VirusTotal IP verdict (JSON.md): engine counts from last_analysis_stats. */
public record VtIpResult(int malicious, int suspicious) {
}
