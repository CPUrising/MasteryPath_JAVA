package com.masterypath.adapter.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 把工具表转成 OpenAI 工具定义（参考实现 {@code tool_definitions}）。
 *
 * <p><b>按名字升序</b>：参考实现 `for name in sorted(tools)`。升序让同一组工具每次生成的
 * schema 数组完全一致，便于断言与缓存。
 *
 * <p>刻意不写 {@code strict}：并非所有 OpenAI 兼容供应商都支持该字段。
 */
public final class ToolDefinitions {

    private ToolDefinitions() {
    }

    /**
     * 生成工具定义列表。
     *
     * @param tools 工具名 → 工具
     * @return 按名字升序的 OpenAI 工具定义
     */
    public static List<Map<String, Object>> of(Map<String, Tool> tools) {
        List<Map<String, Object>> definitions = new ArrayList<>();
        for (Map.Entry<String, Tool> entry : new TreeMap<>(tools).entrySet()) {
            Tool tool = entry.getValue();
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", entry.getKey());
            function.put("description",
                    tool.description() == null || tool.description().isEmpty()
                            ? entry.getKey() : tool.description());
            function.put("parameters", tool.parameters() == null
                    ? Map.of("type", "object", "properties", Map.of()) : tool.parameters());
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", "function");
            definition.put("function", function);
            definitions.add(definition);
        }
        return definitions;
    }
}
