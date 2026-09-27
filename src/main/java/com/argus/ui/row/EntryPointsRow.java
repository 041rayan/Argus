package com.argus.ui.row;

import com.argus.core.export.ExportService.EntryPoint;

/** Entry Points table row: plain getters for PropertyValueFactory. */
public final class EntryPointsRow {

    private final EntryPoint entry;

    public EntryPointsRow(EntryPoint entry) {
        this.entry = entry;
    }

    public int getRank() {
        return entry.rank();
    }

    public String getHostPort() {
        return entry.hostPort();
    }

    public String getService() {
        return entry.service();
    }

    public String getKev() {
        return entry.kev();
    }

    public String getVt() {
        return entry.vt();
    }

    public int getScore() {
        return entry.score();
    }

    public String getSeverity() {
        return entry.severity();
    }

    public EntryPoint entry() {
        return entry;
    }
}
