package com.argus.ui.row;

import com.argus.core.model.Target;

/** Targets table row: plain getters for PropertyValueFactory. */
public final class TargetRow {

    private final Target target;

    public TargetRow(Target target) {
        this.target = target;
    }

    public Long getId() {
        return target.id();
    }

    public String getLabel() {
        return target.label();
    }

    public String getDomain() {
        return target.domain();
    }

    public String getScopeCidrs() {
        return String.join(", ", target.scopeCidrs());
    }

    public String getProfile() {
        return target.profile();
    }

    public Target target() {
        return target;
    }
}
