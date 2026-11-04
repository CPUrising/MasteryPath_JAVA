package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 题目事务状态（spec 01 §一）。 */
public enum InteractionStatus {
    REGISTERED("registered"),
    AWAITING_INPUT("awaiting_input"),
    ANSWERED("answered"),
    GRADED("graded"),
    ABANDONED("abandoned");

    private final String value;

    InteractionStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @JsonCreator
    public static InteractionStatus fromValue(String raw) {
        for (InteractionStatus status : values()) {
            if (status.value.equalsIgnoreCase(raw)) {
                return status;
            }
        }
        throw new IllegalArgumentException("unknown interaction status: " + raw);
    }

    @JsonValue
    public String toJson() {
        return value;
    }

    /** 终态：已判分或已作废（{@code INV-22} 终态可查）。 */
    public boolean isTerminal() {
        return this == GRADED || this == ABANDONED;
    }

    /** 未终态：仍占据单槽位（{@code INV-02}）。 */
    public boolean isOpen() {
        return this == REGISTERED || this == AWAITING_INPUT || this == ANSWERED;
    }
}
