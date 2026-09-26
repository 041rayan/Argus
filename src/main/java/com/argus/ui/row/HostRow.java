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

    public String getCountry() {
        return orEmpty(host.country());
    }

    public String getAsn() {
        return orEmpty(host.asn());
    }

    public String getOrg() {
        return orEmpty(host.org());
    }

    public String getAlive() {
        return host.alive() ? "alive" : "dead";
    }

    /** DB columns are nullable; the table and the search want "". */
    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    public Host host() {
        return host;
    }
}
