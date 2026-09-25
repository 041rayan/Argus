package com.argus.core.event;

/**
 * Core → UI events (CORE.md). One sealed type, records nested — the UI
 * matches on them; core never imports JavaFX.
 */
public sealed interface ScanEvent {

    record ScanStarted(String target) implements ScanEvent {
    }

    record HostFound(String subdomain, String ip, boolean alive) implements ScanEvent {
    }

    record PortFound(String host, int port, String service, String banner) implements ScanEvent {
    }

    record FindingEmitted(String type, String severity) implements ScanEvent {
    }

    record StageProgress(String stage, int done) implements ScanEvent {
    }

    record ScanFinished(String status, String message) implements ScanEvent {
    }

    record ProviderDegraded(String provider, int status) implements ScanEvent {
    }
}
