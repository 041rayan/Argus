package com.argus.core.export;

import com.argus.core.json.Json;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.Instant;
import java.util.List;

/**
 * Builds the target package (JSON.md): Markdown or JSON. Pure string
 * functions, no JavaFX, no I/O — the caller writes the result to a file.
 */
public final class ExportService {

    public record EntryPoint(int rank, String hostPort, String service, String kev, int score) {
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
                .append("| Rank | Host:Port | Service | KEV | Score |\n")
                .append("|---|---|---|---|---|\n");
        for (EntryPoint p : points) {
            md.append("| ").append(p.rank())
                    .append(" | ").append(clean(p.hostPort()))
                    .append(" | ").append(clean(p.service()))
                    .append(" | ").append(clean(p.kev()))
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
}
