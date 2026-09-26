package com.argus.core.kev;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Banner → product and version, following the seed table in CORE.md: first
 * rule that finds a match wins. Version stays null where the banner does not
 * carry one (Apache-Coyote), which makes the matcher answer CANDIDATE.
 */
public final class ProductExtractor {

    /** Version is null, not empty, when the pattern identifies a product only. */
    public record Extracted(String product, String version) {
    }

    private record Rule(String product, Pattern pattern) {
    }

    private static final Rule[] RULES = {
        new Rule("apache_httpd",
                Pattern.compile("Server:\\s*Apache/([0-9][0-9.]*)", Pattern.CASE_INSENSITIVE)),
        new Rule("nginx",
                Pattern.compile("Server:\\s*nginx/([0-9][0-9.]*)", Pattern.CASE_INSENSITIVE)),
        new Rule("tomcat",
                Pattern.compile("Apache-Coyote", Pattern.CASE_INSENSITIVE)),
        new Rule("openssh",
                Pattern.compile("SSH-2\\.0-OpenSSH[_-]([0-9][0-9.p]*)", Pattern.CASE_INSENSITIVE)),
        new Rule("vsftpd",
                Pattern.compile("\\(vsFTPd\\s+([0-9][0-9.]*)\\)", Pattern.CASE_INSENSITIVE)),
    };

    public Optional<Extracted> extract(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern.matcher(text);
            if (matcher.find()) {
                String version = matcher.groupCount() > 0 && matcher.group(1) != null
                        ? matcher.group(1) : null;
                return Optional.of(new Extracted(rule.product, version));
            }
        }
        return Optional.empty();
    }
}
