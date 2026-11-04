package com.masterypath.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

/**
 * 知识点类型（spec 01 §一）。
 *
 * <p>{@code INV-16}：未知字符串 MUST 拒绝；历史中文值 MUST 映射为对应英文枚举，
 * 语义等价于参考实现 Pydantic 枚举的 {@code _missing_}。
 */
public enum KnowledgeType {
    MEMORY("memory"),
    CONCEPT("concept"),
    PROCEDURE("procedure"),
    DESIGN("design");

    /** 历史中文值 → 英文枚举名。仅用于读取旧数据。 */
    private static final Map<String, String> LEGACY = Map.of(
            "记忆型", "memory",
            "概念型", "concept",
            "程序型", "procedure",
            "设计型", "design");

    private final String value;

    KnowledgeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 解析知识类型。
     *
     * @throws IllegalArgumentException 当值既不是已知英文值也不是已知历史中文值
     */
    @JsonCreator
    public static KnowledgeType fromValue(Object raw) {
        if (raw == null) {
            throw new IllegalArgumentException("knowledge type must not be null");
        }
        String text = String.valueOf(raw).trim();
        for (KnowledgeType type : values()) {
            if (type.value.equalsIgnoreCase(text)) {
                return type;
            }
        }
        String mapped = LEGACY.get(text);
        if (mapped != null) {
            for (KnowledgeType type : values()) {
                if (type.value.equals(mapped)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("unknown knowledge type: " + text);
    }

    /** 序列化为英文小写值（与旧库、前端契约一致）。 */
    @com.fasterxml.jackson.annotation.JsonValue
    public String toJson() {
        return value;
    }
}
