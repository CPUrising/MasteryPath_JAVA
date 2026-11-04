package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 缺口方向（spec 01 §一 / ADR-011）。 */
public enum GapKind {
    GOAL_GAP("goal_gap"),
    SOURCE_OMISSION("source_omission");

    private final String value;

    GapKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static GapKind fromValue(String raw) {
        for (GapKind kind : values()) {
            if (kind.value.equalsIgnoreCase(raw)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown gap kind: " + raw);
    }

    @JsonValue
    public String toJson() {
        return value;
    }
}
