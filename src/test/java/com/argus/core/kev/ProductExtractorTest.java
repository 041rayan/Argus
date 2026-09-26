package com.argus.core.kev;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The seed pattern table from CORE.md, one case per row. */
class ProductExtractorTest {

    private final ProductExtractor extractor = new ProductExtractor();

    @Test
    void seedPatternsExtractProductAndVersion() {
        assertEquals(Optional.of(new ProductExtractor.Extracted("apache_httpd", "2.4.49")),
                extractor.extract("Server: Apache/2.4.49 (Debian)"));
        assertEquals(Optional.of(new ProductExtractor.Extracted("nginx", "1.18.0")),
                extractor.extract("Server: nginx/1.18.0"));
        assertEquals(Optional.of(new ProductExtractor.Extracted("openssh", "9.2")),
                extractor.extract("SSH-2.0-OpenSSH_9.2"));
        assertEquals(Optional.of(new ProductExtractor.Extracted("vsftpd", "3.0.5")),
                extractor.extract("220 (vsFTPd 3.0.5)"));
    }

    @Test
    void coyoteIdentifiesTomcatWithoutVersion() {
        assertEquals(Optional.of(new ProductExtractor.Extracted("tomcat", null)),
                extractor.extract("Apache-Coyote/1.1"));
    }

    @Test
    void unknownAndBlankTextMatchNothing() {
        assertEquals(Optional.empty(), extractor.extract("HTTP/1.1 200 OK"));
        assertEquals(Optional.empty(), extractor.extract(""));
        assertEquals(Optional.empty(), extractor.extract(null));
    }

    @Test
    void matchWorksOnPartialBanner() {
        assertTrue(extractor.extract("server: apache/2.4.57 (Ubuntu)").isPresent(),
                "case-insensitive prefix, no anchoring");
    }
}
