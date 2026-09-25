package com.argus.core.api;

import java.io.IOException;

/** API.md status handling: the provider degraded, the scan continues. */
public class ProviderDegradedException extends IOException {

    private final int status;

    public ProviderDegradedException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ProviderDegradedException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status, or -1 when the provider is already disabled. */
    public int status() {
        return status;
    }
}
