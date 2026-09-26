package com.argus.core.kev;

import com.argus.core.api.dto.VersionRange;

/**
 * Component-wise version compare (CORE.md): "2.4.49" → 2, 4, 49. Non-numeric
 * tails ("7p1") compare by their leading digits; missing components count 0.
 */
public final class VersionCompare {

    private VersionCompare() {
    }

    /** introduced <= version < fixed — fixed is exclusive. */
    public static boolean inRange(String version, VersionRange range) {
        return compare(version, range.introduced()) >= 0
                && compare(version, range.fixed()) < 0;
    }

    public static int compare(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int x = i < left.length ? num(left[i]) : 0;
            int y = i < right.length ? num(right[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** Leading digits of one component, 0 when it starts non-numeric. */
    private static int num(String component) {
        int value = 0;
        int i = 0;
        while (i < component.length() && Character.isDigit(component.charAt(i))) {
            value = value * 10 + (component.charAt(i) - '0');
            i++;
            if (value > 1_000_000) {
                break;
            }
        }
        return value;
    }
}
