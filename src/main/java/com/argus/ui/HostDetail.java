package com.argus.ui;

import com.argus.core.export.ExportService;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One host's slice of an already loaded scan: its open ports and its
 * findings. Pure grouping for the Results inspector, so the controller
 * never parses detail_json and this is testable without a toolkit
 * (THREAD.md: the load stays off the FX thread, the selection does not).
 *
 * <p>Ports carry the host as {@code PortResult.host()}, the subdomain
 * string. Findings carry it inside detail_json, read through
 * {@link ExportService#hostPortOf} so that contract lives in one place.
 */
public record HostDetail(String host, List<PortResult> openPorts,
                         List<Finding> findings) {

    /** A host with nothing to show, used when the scan has no rows for it. */
    public static HostDetail empty(String host) {
        return new HostDetail(host, List.of(), List.of());
    }

    /**
     * One entry per host that has at least one open port or one finding.
     * Insertion order follows the ports, then the findings.
     */
    public static Map<String, HostDetail> byHost(List<PortResult> ports, List<Finding> findings) {
        Map<String, List<PortResult>> open = new LinkedHashMap<>();
        for (PortResult p : ports) {
            if (p.open()) {
                open.computeIfAbsent(p.host(), k -> new ArrayList<>()).add(p);
            }
        }
        Map<String, List<Finding>> found = new LinkedHashMap<>();
        for (Finding f : findings) {
            String host = hostOf(f);
            if (host != null) {
                found.computeIfAbsent(host, k -> new ArrayList<>()).add(f);
            }
        }
        Map<String, HostDetail> out = new LinkedHashMap<>();
        open.forEach((host, own) ->
                out.put(host, new HostDetail(host, List.copyOf(own), List.of())));
        found.forEach((host, own) -> out.merge(host,
                new HostDetail(host, List.of(), List.copyOf(own)),
                (portsFirst, findingsNow) -> new HostDetail(host,
                        portsFirst.openPorts(), findingsNow.findings())));
        return out;
    }

    public int findingCount() {
        return findings.size();
    }

    /** The host half of the shared "host:port" key, or null when unreadable. */
    private static String hostOf(Finding f) {
        String hostPort = ExportService.hostPortOf(f);
        if (hostPort == null) {
            return null;
        }
        int colon = hostPort.lastIndexOf(':');
        return colon < 0 ? hostPort : hostPort.substring(0, colon);
    }
}
