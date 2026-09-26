package com.argus.core.kev;

import com.argus.core.api.dto.VersionRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionCompareTest {

    @Test
    void comparesComponentWise() {
        assertEquals(0, VersionCompare.compare("2.4.49", "2.4.49"));
        assertTrue(VersionCompare.compare("2.4.49", "2.4.50") < 0);
        assertTrue(VersionCompare.compare("1.18.0", "1.9") > 0,
                "1.18 > 1.9 component-wise, not lexicographically");
        assertEquals(0, VersionCompare.compare("2.4", "2.4.0"),
                "missing components count 0");
        assertTrue(VersionCompare.compare("9.7p1", "9.8") < 0,
                "non-numeric tail compares by leading digits");
    }

    @Test
    void rangeIsInclusiveLowExclusiveHigh() {
        VersionRange range = new VersionRange("CVE-X", "apache_httpd", "2.4.49", "2.4.50");
        assertTrue(VersionCompare.inRange("2.4.49", range), "introduced included");
        assertFalse(VersionCompare.inRange("2.4.50", range), "fixed excluded");
        assertFalse(VersionCompare.inRange("2.4.48", range));
        assertFalse(VersionCompare.inRange("2.4.51", range));
    }
}
