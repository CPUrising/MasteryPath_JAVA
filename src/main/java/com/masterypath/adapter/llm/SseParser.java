package com.masterypath.adapter.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.domain.model.JsonSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * OpenAI 兼容 SSE 行解析（参考实现 {@code parse_stream_lines}）。
 *
 * <p>供应商把工具调用拆成碎片流式下发：{@code index} 标识是哪一个调用，
 * {@code id} / {@code name} 通常只出现一次，{@code arguments} 分多片到达。
 * 本类按 {@code index} 归并，文本片即时产出，工具调用在流末尾一次性产出。
 *
 * <p>用法两种：
 * <ul>
 *   <li>{@link #parse(Iterable)}：一次性解析（测试与简单场景）。</li>
 *   <li>{@link #feed(String)} + {@link #finish()}：逐行解析，保留流式语义。</li>
 * </ul>
 */
public final class SseParser {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    private final ObjectMapper mapper = JsonSupport.objectMapper();
    /** 按 {@code index} 归并的分片；用 {@link TreeMap} 保证输出按 index 升序。 */
    private final Map<Integer, Fragment> fragments = new TreeMap<>();
    /** 收到 {@code [DONE]} 后置真：后续行一律忽略（参考实现 {@code break}）。 */
    private boolean done;

    /** 一个工具调用的累积分片。 */
    private static final class Fragment {
        private String id = "";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
    }

    /**
     * 一次性解析全部行。
     *
     * @param lines SSE 原始行（可含空行、{@code event:} 行、注释行）
     * @return 文本片按到达顺序，工具调用片（若有）在最后
     */
    public static List<StreamChunk> parse(Iterable<String> lines) {
        SseParser parser = new SseParser();
        List<StreamChunk> chunks = new ArrayList<>();
        for (String line : lines) {
            chunks.addAll(parser.feed(line));
        }
        chunks.addAll(parser.finish());
        return chunks;
    }

    /**
     * 喂入一行，返回该行产生的文本片（0 或 1 个）。
     *
     * <p>工具调用不在此处产出——它们要等流结束才能拼装完整。
     *
     * @param line 一行原始文本，可为 {@code null}
     * @return 文本片列表
     */
    public List<StreamChunk> feed(String line) {
        if (done || line == null || !line.startsWith(DATA_PREFIX)) {
            return List.of();
        }
        String data = line.substring(DATA_PREFIX.length()).strip();
        if (DONE.equals(data)) {
            done = true;
            return List.of();
        }
        Map<String, Object> choice = firstChoice(data);
        if (choice == null) {
            return List.of();
        }
        Object rawDelta = choice.get("delta");
        Map<?, ?> delta = rawDelta instanceof Map<?, ?> map ? map : Map.of();
        List<StreamChunk> chunks = new ArrayList<>();
        Object content = delta.get("content");
        if (content instanceof String text && !text.isEmpty()) {
            chunks.add(StreamChunk.text(text));
        }
        Object rawCalls = delta.get("tool_calls");
        if (rawCalls instanceof List<?> calls) {
            for (Object raw : calls) {
                if (raw instanceof Map<?, ?> call) {
                    accumulate(call);
                }
            }
        }
        return chunks;
    }

    /**
     * 流结束：产出拼装完成的工具调用片。
     *
     * @return 含全部工具调用的单元素列表；无任何调用时返回空列表
     */
    public List<StreamChunk> finish() {
        if (fragments.isEmpty()) {
            return List.of();
        }
        List<ToolCall> assembled = new ArrayList<>();
        for (Map.Entry<Integer, Fragment> entry : fragments.entrySet()) {
            Fragment fragment = entry.getValue();
            String rawArguments = fragment.arguments.toString();
            Map<String, Object> arguments = Map.of();
            boolean parseError = false;
            if (!rawArguments.isEmpty()) {
                Object parsed = tryParse(rawArguments);
                if (parsed instanceof Map<?, ?> map) {
                    arguments = castMap(map);
                } else {
                    parseError = true;
                }
            }
            assembled.add(new ToolCall(
                    fragment.name,
                    arguments,
                    fragment.id.isEmpty() ? "call-" + entry.getKey() : fragment.id,
                    parseError || fragment.name.isEmpty()));
        }
        return List.of(StreamChunk.calls(assembled));
    }

    // -- 内部 -----------------------------------------------------------------

    /** 取 {@code payload.choices[0]}；结构不符时返回 {@code null}（参考实现吞掉异常继续）。 */
    private Map<String, Object> firstChoice(String data) {
        Object parsed = tryParse(data);
        if (!(parsed instanceof Map<?, ?> payload)) {
            return null;
        }
        Object rawChoices = payload.get("choices");
        if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        return first instanceof Map<?, ?> choice ? castMap(choice) : null;
    }

    /** 把一个工具调用分片并入累积表。 */
    private void accumulate(Map<?, ?> call) {
        int index = toIndex(call.get("index"));
        Fragment fragment = fragments.computeIfAbsent(index, key -> new Fragment());
        Object id = call.get("id");
        if (id != null && !String.valueOf(id).isEmpty()) {
            fragment.id = String.valueOf(id);
        }
        Object rawFunction = call.get("function");
        if (rawFunction instanceof Map<?, ?> function) {
            Object name = function.get("name");
            if (name != null && !String.valueOf(name).isEmpty()) {
                fragment.name = String.valueOf(name);
            }
            Object arguments = function.get("arguments");
            if (arguments != null && !String.valueOf(arguments).isEmpty()) {
                fragment.arguments.append(arguments);
            }
        }
    }

    /** Python 的 {@code int(raw.get("index", 0))}：无法解析时归 0。 */
    private static int toIndex(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Object tryParse(String json) {
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
