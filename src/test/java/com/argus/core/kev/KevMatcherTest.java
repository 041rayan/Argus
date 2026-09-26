package com.argus.core.kev;

import com.argus.core.api.KevClient;
import com.argus.core.api.dto.KevCatalog;
import com.argus.core.model.KevMatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Against the bundled real tables: fixture catalog, alias.json, ranges.json. */
class KevMatcherTest {

    private static KevCatalog catalog;
    private static KevMatcher matcher;

    @BeforeAll
    static void loadCatalog() throws Exception {
        try (java.io.InputStream in =
                     KevMatcherTest.class.getResourceAsStream("/kev/kev-snapshot.json")) {
            catalog = com.argus.core.json.Json.MAPPER.readValue(in, KevCatalog.class);
        }
        matcher = new KevMatcher(catalog);
    }

    @Test
    void vulnerableApacheVersionConfirmsItsCves() {
        List<KevMatch> matches = matcher.match("Server: Apache/2.4.49 (Debian)");

        Set<String> cves = matches.stream().map(KevMatch::cve).collect(Collectors.toSet());
        assertTrue(cves.contains("CVE-2021-41773"), "path traversal confirmed");
        assertTrue(cves.contains("CVE-2024-38475"), "mapping 2.4.49 < 2.4.60 confirmed");
        assertTrue(matches.stream().allMatch(m -> m.confidence() == KevMatch.Confidence.CONFIRMED));
        assertTrue(matches.stream().anyMatch(KevMatch::ransomware),
                "CVE-2021-41773 is a Known ransomware campaign");
    }

    @Test
    void patchedApacheVersionProducesNoMatch() {
        assertTrue(matcher.match("Server: Apache/2.4.62 (Ubuntu)").isEmpty(),
                "outside every range → patched, no candidate");
    }

    @Test
    void versionlessCoyoteStaysCandidate() {
        List<KevMatch> matches = matcher.match("Apache-Coyote/1.1");

        assertFalse(matches.isEmpty());
        assertTrue(matches.stream().allMatch(m -> m.confidence() == KevMatch.Confidence.CANDIDATE),
                "no version → CANDIDATE for every KEV entry of that product");
        assertTrue(matches.stream().allMatch(m -> m.product().equals("Tomcat")));
    }

    @Test
    void productsWithoutKeveEntriesMatchNothing() {
        assertEquals(List.of(), matcher.match("Server: nginx/1.18.0"),
                "nginx has no KEV entries, so no candidates exist");
        assertEquals(List.of(), matcher.match("SSH-2.0-OpenSSH_9.2"));
    }

    @Test
    void textWithoutExtractorMatchMatchesNothing() {
        assertEquals(List.of(), matcher.match("HTTP/1.1 200 OK"));
        assertEquals(List.of(), matcher.match(null));
    }
}
