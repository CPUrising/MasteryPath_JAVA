package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 评估类型（spec 01 §一）。 */
public enum AssessmentType {
    QUIZ("quiz"),
    QUALITATIVE("qualitative"),
    REVIEW("review");

    private final String value;

    AssessmentType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static AssessmentType fromValue(String raw) {
        for (AssessmentType type : values()) {
            if (type.value.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unknown assessment type: " + raw);
    }

    @JsonValue
    public String toJson() {
        return value;
    }
}
