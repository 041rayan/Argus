package com.argus.core.api.dto;

/** ip-api batch entry (JSON.md fields used, plus `query` to map results back to the IP). */
public record IpApiResult(String status, String country, String as, String org,
                          String isp, String query) {
}
