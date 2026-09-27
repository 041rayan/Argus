package com.argus.core.export;

import com.argus.core.json.Json;
import com.argus.core.model.Finding;
import com.argus.core.model.PortResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the target package (JSON.md): Markdown or JSON. Pure string
 * functions, no JavaFX, no I/O — the caller writes the result to a file.
 */
public final class ExportService {

    public record EntryPoint(int rank, String hostPort, String service, String severity,
                             String kev, String vt, int score) {
    }

    public record FindingOut(String type, String severity, String detail) {
    }

    private record ExportDoc(String target, String scannedAt,
                             List<EntryPoint> entryPoints, List<FindingOut> findings) {
    }

    public String markdown(String target, Instant scannedAt, String operator,
                           List<EntryPoint> points, List<FindingOut> findings) {
        StringBuilder md = new StringBuilder();
        md.append("# Target package: ").append(clean(target)).append("\n\n")
                .append("- Date: ").append(scannedAt == null ? "not yet scanned" : scannedAt).append("\n")
                .append("- Operator: ").append(clean(operator)).append("\n\n")
                .append("## Entry points\n\n")
                .append("| Rank | Host:Port | Service | Severity | KEV | VT | Score |\n")
                .append("|---|---|---|---|---|---|---|\n");
        for (EntryPoint p : points) {
            md.append("| ").append(p.rank())
                    .append(" | ").append(clean(p.hostPort()))
                    .append(" | ").append(clean(p.service()))
                    .append(" | ").append(clean(p.severity()))
                    .append(" | ").append(clean(p.kev()))
                    .append(" | ").append(clean(p.vt()))
                    .append(" | ").append(p.score()).append(" |\n");
        }
        md.append("\n## Findings\n\n");
        if (findings.isEmpty()) {
            md.append("(none)\n");
        }
        for (FindingOut f : findings) {
            md.append("- [").append(clean(f.severity())).append("] ")
                    .append(clean(f.type())).append(": ").append(clean(f.detail())).append("\n");
        }
        return md.toString();
    }

    public String json(String target, Instant scannedAt,
                       List<EntryPoint> points, List<FindingOut> findings) throws JsonProcessingException {
        ExportDoc doc = new ExportDoc(target,
                scannedAt == null ? null : scannedAt.toString(), points, findings);
        return Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
    }

    /** Table cells: pipes would break the Markdown table. */
    private static String clean(String s) {
        return s == null ? "" : s.replace("|", "\\|");
    }

    /**
     * Entry points of one scan (CORE.md priority score): every open port with
     * its KEV verdicts and VT engine counts, score = sum of signal weights
     * capped at 100, ranked descending (ties by host:port). The view and the
     * export share this.
     */
    public static List<EntryPoint> entryPoints(List<PortResult> ports, List<Finding> findings) {
        Map<String, List<Finding>> byPort = new HashMap<>();
        for (Finding f : findings) {
            String key = hostPortOf(f);
            if (key != null) {
                byPort.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
            }
        }
        List<EntryPoint> ranked = new ArrayList<>();
        for (PortResult p : ports) {
            List<Finding> own = byPort.getOrDefault(p.host() + ":" + p.port(), List.of());
            ranked.add(new EntryPoint(0, p.host() + ":" + p.port(), service(p),
                    worstSeverity(own), kev(own), vt(own), score(p, own)));
        }
        ranked.sort(Comparator.comparingInt(EntryPoint::score).reversed()
                .thenComparing(EntryPoint::hostPort));
        List<EntryPoint> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            EntryPoint e = ranked.get(i);
            out.add(new EntryPoint(i + 1, e.hostPort(), e.service(), e.severity(),
                    e.kev(), e.vt(), e.score()));
        }
        return out;
    }

    /** One readable finding line. The detail contract lives here, not in a view. */
    public static FindingOut findingOut(Finding f) {
        return new FindingOut(f.type(), f.severity(), detailText(f));
    }

    /** Readable finding lines for markdown and JSON export. */
    public static List<FindingOut> findingOuts(List<Finding> findings) {
        return findings.stream().map(ExportService::findingOut).toList();
    }

    /**
     * "host:port" from a finding's detail_json, or null when it carries no
     * host. A corrupt or absent detail costs the finding its host, never a
     * throw: the view shows one less row, the export is untouched.
     */
    public static String hostPortOf(Finding f) {
        JsonNode d = detail(f);
        if (d == null || !d.has("host") || !d.has("port")) {
            return null;
        }
        return d.path("host").asText() + ":" + d.path("port").asInt();
    }

    /** Finding lines for one host:port, in input order (view detail panel). */
    public static List<FindingOut> findingOutsFor(List<Finding> findings, String hostPort) {
        return findings.stream()
                .filter(f -> hostPort.equals(hostPortOf(f)))
                .map(ExportService::findingOut)
                .toList();
    }

    /**
     * Signal weights (CORE.md): each KEV verdict on the port contributes its
     * weight, ransomware +10, VirusTotal verdicts from 3+ engines +10,
     * internet-facing web or admin service +10.
     */
    private static int score(PortResult port, List<Finding> own) {
        int score = 0;
        for (Finding f : own) {
            JsonNode d = detail(f);
            if (d == null) {
                continue;
            }
            String confidence = d.path("confidence").asText("");
            if ("CONFIRMED".equals(confidence)) {
                score += 35;
            } else if ("CANDIDATE".equals(confidence)) {
                score += 15;
            }
            if (d.path("ransomware").asBoolean(false)) {
                score += 10;
            }
            if (d.path("malicious").asInt(0) >= 3) {
                score += 10; // VT_FLAGGED: 3+ engines flag it (CORE.md)
            }
        }
        if (isWebOrAdmin(port.port())) {
            score += 10;
        }
        return Math.min(100, score);
    }

    /** Web ports from the CORE.md banner spec; admin remotes an operator logs into. */
    private static boolean isWebOrAdmin(int port) {
        return switch (port) {
            case 80, 443, 8080, 8443, 22, 23, 3389, 5900, 5901 -> true;
            default -> false;
        };
    }

    private static String service(PortResult p) {
        return p.service() == null || p.service().isBlank() ? "-" : p.service();
    }

    private static String kev(List<Finding> own) {
        if (own.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Finding f : own) {
            JsonNode d = detail(f);
            if (d == null || !d.has("cve")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(d.path("cve").asText()).append(' ')
                    .append(d.path("confidence").asText());
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static String detailText(Finding f) {
        JsonNode d = detail(f);
        if (d == null) {
            return f.detailJson() == null ? "" : f.detailJson();
        }
        if (d.has("cve")) {
            return d.path("host").asText("") + ":" + d.path("port").asInt(0)
                    + " " + d.path("cve").asText()
                    + " (" + d.path("confidence").asText("") + ")";
        }
        return f.detailJson() == null ? "" : f.detailJson();
    }

    /**
     * VT engine counts for the port's VT_FLAGGED findings, rendered
     * malicious/suspicious ("12/4"); "-" when no engine flagged it.
     */
    private static String vt(List<Finding> own) {
        StringBuilder sb = new StringBuilder();
        for (Finding f : own) {
            JsonNode d = detail(f);
            if (d == null || !"VT_FLAGGED".equals(f.type())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(d.path("malicious").asInt(0))
                    .append('/').append(d.path("suspicious").asInt(0));
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** Highest severity among the port's findings; "-" when the port has none. */
    private static String worstSeverity(List<Finding> own) {
        String worst = "-";
        for (Finding f : own) {
            if (severityRank(f.severity()) > severityRank(worst)) {
                worst = f.severity();
            }
        }
        return worst;
    }

    private static int severityRank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            case "INFO" -> 0;
            default -> -1;
        };
    }

    /**
     * Lenient: an unparseable or absent detail_json costs a signal, not the
     * export. Jackson throws IllegalArgumentException (not IOException) on a
     * null content, so the null case is guarded before the read.
     */
    private static JsonNode detail(Finding f) {
        if (f == null || f.detailJson() == null) {
            return null;
        }
        try {
            return Json.MAPPER.readTree(f.detailJson());
        } catch (IOException e) {
            return null;
        }
    }
}
