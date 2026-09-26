package com.argus.ui.controller;

import com.argus.core.model.ScanSummary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** The one scan label for both history combos: #id target · status · stamp. */
final class ScanLabel {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private ScanLabel() {
    }

    static String of(ScanSummary s) {
        return "#" + s.id() + " " + s.target() + " · " + s.status() + " · " + STAMP.format(s.startedAt());
    }
}
