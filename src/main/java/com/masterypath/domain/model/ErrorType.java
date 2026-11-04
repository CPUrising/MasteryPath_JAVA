package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * 作答错误类型（spec 01 §一）。
 *
 * <p>历史中文值 MUST 映射；未知值 MUST 拒绝（与 {@link KnowledgeType} 同策略）。
 */
public enum ErrorType {
    STRUCTURAL("structural"),
    DEVIATION("deviation"),
    APPLICATION("application"),
    METACOGNITIVE("metacognitive");

    private static final Map<String, String> LEGACY = Map.of(
            "知识结构性", "structural",
            "理解偏差型", "deviation",
            "应用错误", "application",
            "元认知型", "metacognitive");

    private final String value;

    ErrorType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static ErrorType fromValue(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("error type must not be null");
        }
        String text = String.valueOf(raw).trim();
        for (ErrorType type : values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        String mapped = LEGACY.get(text);
        if (mapped != null) {
            for (ErrorType type : values()) {
                if (type.value.equals(mapped)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("unknown error type: " + text);
    }

    @JsonValue
    public String toJson() {
        return value;
    }
}
