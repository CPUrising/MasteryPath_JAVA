package com.masterypath.adapter.llm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型请求的一次工具调用（参考实现 {@code agents/llm.py} 的 {@code ToolCall}）。
 *
 * <p>{@link #parseError()} 为真表示供应商返回的 {@code arguments} 不是合法 JSON 对象；
 * 此时 {@link #arguments()} 为空 map，回合循环据此回一个失败结果让模型自我纠正，
 * 而不是让整回合崩掉。
 */
public record ToolCall(String name, Map<String, Object> arguments, String id, boolean parseError) {

    public ToolCall {
        name = name == null ? "" : name;
        arguments = arguments == null ? Map.of() : new LinkedHashMap<>(arguments);
        id = id == null ? "" : id;
    }

    /** 无参数、无 id 的调用（测试与兜底用）。 */
    public static ToolCall of(String name) {
        return new ToolCall(name, Map.of(), "", false);
    }
}
