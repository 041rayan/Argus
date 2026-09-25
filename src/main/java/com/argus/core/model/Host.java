package com.argus.core.model;

/** Resolved host of a scan: name, IP, liveness, enrichment. */
public record Host(Long id, long scanId, String subdomain, String ip, boolean alive,
                   String country, String asn, String org) {
}
