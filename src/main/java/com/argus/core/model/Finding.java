package com.argus.core.model;

/** Module output. hostId is null while the scan is in memory — assigned only on read-back. */
public record Finding(long scanId, Long hostId, String moduleId, String type,
                      String severity, String detailJson) {
}
