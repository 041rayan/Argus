package com.argus.ui.row;

import com.argus.core.model.Host;

/** Results table row: plain getters for PropertyValueFactory. */
public final class HostRow {

    private final Host host;

    public HostRow(Host host) {
        this.host = host;
    }

    public String getSubdomain() {
        return host.subdomain();
    }

    public String getIp() {
        return host.ip();
    }

    public String getAlive() {
        return host.alive() ? "alive" : "dead";
    }

    public Host host() {
        return host;
    }
}
