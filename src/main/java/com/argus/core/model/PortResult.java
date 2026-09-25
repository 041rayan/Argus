package com.argus.core.model;

/** One probed port. No ids: the writer maps {@code host} to the host row on insert. */
public record PortResult(String host, int port, String protocol, String service,
                         String version, String banner, String title, boolean open) {
}
