package com.argus.core.model;

import java.time.Instant;

/** One scan run: who, which target, which profile, lifecycle status. */
public record ScanSummary(Long id, long operatorId, String target, String profile,
                          Status status, Instant startedAt, Instant finishedAt) {

    public enum Status { RUNNING, COMPLETED, CANCELLED, FAILED }
}
