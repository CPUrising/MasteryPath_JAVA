package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 评估结果（spec 01 §一）。 */
public enum AssessmentResult {
    CORRECT("correct"),
    INCORRECT("incorrect"),
    PARTIAL("partial");

    private final String value;

    AssessmentResult(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static AssessmentResult fromValue(String raw) {
        for (AssessmentResult result : values()) {
            if (result.value.equalsIgnoreCase(raw)) {
                return result;
            }
        }
        throw new IllegalArgumentException("unknown assessment result: " + raw);
    }

    @JsonValue
    public String toJson() {
        return value;
    }
}
