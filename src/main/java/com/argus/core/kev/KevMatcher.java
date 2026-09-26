package com.argus.core.kev;

import com.argus.core.api.dto.KevCatalog;
import com.argus.core.api.dto.KevEntry;
import com.argus.core.api.dto.VersionRange;
import com.argus.core.json.Json;
import com.argus.core.model.KevMatch;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The KEV engine (CORE.md flow): extractor → alias → candidate CVEs → range
 * verdict. Alias and ranges are the bundled tables; a product the alias table
 * does not know simply produces no match, and a CVE with no range data stays
 * a CANDIDATE. Call it once per text — banner and title separately.
 */
public final class KevMatcher {

    private record Alias(String vendorProject, String product) {
    }

    private final KevCatalog catalog;
    private final Map<String, Alias> alias;
    private final List<VersionRange> ranges;
    private final ProductExtractor extractor = new ProductExtractor();

    public KevMatcher(KevCatalog catalog) {
        if (catalog == null || catalog.vulnerabilities() == null) {
            throw new IllegalArgumentException("KEV catalog missing");
        }
        this.catalog = catalog;
        this.alias = read("/kev/alias.json", new TypeReference<Map<String, Alias>>() {
        });
        this.ranges = read("/kev/ranges.json", new TypeReference<List<VersionRange>>() {
        });
    }

    /** Verdicts for one banner/title line; empty when nothing identifies. */
    public List<KevMatch> match(String text) {
        ProductExtractor.Extracted found = extractor.extract(text).orElse(null);
        if (found == null) {
            return List.of();
        }
        Alias kevSpelling = alias.get(found.product());
        if (kevSpelling == null) {
            return List.of();
        }
        List<KevMatch> matches = new ArrayList<>();
        for (KevEntry entry : catalog.vulnerabilities()) {
            if (!kevSpelling.vendorProject().equals(entry.vendorProject())
                    || !kevSpelling.product().equals(entry.product())) {
                continue;
            }
            KevMatch.Confidence confidence =
                    verdict(entry.cveID(), found.product(), found.version());
            if (confidence != null) {
                boolean ransomware = "Known".equals(entry.knownRansomwareCampaignUse());
                matches.add(new KevMatch(entry.cveID(), entry.vulnerabilityName(),
                        kevSpelling.product(), confidence, ransomware));
            }
        }
        return matches;
    }

    /**
     * Version in a range → CONFIRMED; range data exists but the version sits
     * outside → null (patched, no match); no range or no version → CANDIDATE.
     */
    private KevMatch.Confidence verdict(String cveId, String product, String version) {
        if (version == null) {
            return KevMatch.Confidence.CANDIDATE;
        }
        boolean hasRange = false;
        for (VersionRange range : ranges) {
            if (!range.cveId().equals(cveId) || !range.product().equals(product)) {
                continue;
            }
            hasRange = true;
            if (VersionCompare.inRange(version, range)) {
                return KevMatch.Confidence.CONFIRMED;
            }
        }
        return hasRange ? null : KevMatch.Confidence.CANDIDATE;
    }

    private static <T> T read(String resource, TypeReference<T> type) {
        try (InputStream in = KevMatcher.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + resource);
            }
            return Json.MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException("unreadable resource " + resource, e);
        }
    }
}
