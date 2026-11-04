package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 缺口处理结果（spec 01 §一 / ADR-011）。 */
public enum GapResolution {
    ACCEPTED("accepted"),
    REGENERATED("regenerated");

    private final String value;

    GapResolution(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static GapResolution fromValue(String raw) {
        for (GapResolution resolution : values()) {
            if (resolution.value.equalsIgnoreCase(raw)) {
                return resolution;
            }
        }
        throw new IllegalArgumentException("unknown gap resolution: " + raw);
    }

    @JsonValue
    public String toJson() {
        return value;
    }
}
