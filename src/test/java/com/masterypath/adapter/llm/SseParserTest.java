package com.masterypath.adapter.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 解析（参考实现 {@code parse_stream_lines}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_llm_client.py} 的两个流式用例。
 */
class SseParserTest {

    @Test
    @DisplayName("文本增量按到达顺序产出，工具调用在末尾一次性产出")
    void textThenAssembledCalls() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\","
                        + "\"function\":{\"name\":\"mastery_quiz\",\"arguments\":\"{\\\"knowledge\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"_point_id\\\": \\\"k1\\\"}\"}}]}}]}"));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).text()).isEqualTo("Hel");
        assertThat(chunks.get(1).text()).isEqualTo("lo");
        ToolCall call = chunks.get(2).toolCalls().get(0);
        assertThat(call.name()).isEqualTo("mastery_quiz");
        assertThat(call.id()).isEqualTo("call-1");
        assertThat(call.arguments()).containsEntry("knowledge_point_id", "k1");
        assertThat(call.parseError()).isFalse();
    }

    @Test
    @DisplayName("坏参数标记 parseError，arguments 归空，无 id 时兜底 call-{index}")
    void invalidArgumentsAndFallbackId() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":2,"
                        + "\"function\":{\"name\":\"mastery_grade\",\"arguments\":\"not json\"}}]}}]}"));

        ToolCall call = chunks.get(0).toolCalls().get(0);
        assertThat(call.parseError()).isTrue();
        assertThat(call.arguments()).isEmpty();
        assertThat(call.id()).isEqualTo("call-2");
    }

    @Test
    @DisplayName("没有名字也算 parseError")
    void missingNameIsParseError() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"arguments\":\"{}\"}}]}}]}"));

        assertThat(chunks.get(0).toolCalls().get(0).parseError()).isTrue();
    }

    @Test
    @DisplayName("空字符串参数不算错误")
    void emptyArgumentsIsNotAnError() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\","
                        + "\"function\":{\"name\":\"mastery_status\",\"arguments\":\"\"}}]}}]}"));

        ToolCall call = chunks.get(0).toolCalls().get(0);
        assertThat(call.parseError()).isFalse();
        assertThat(call.arguments()).isEmpty();
    }

    @Test
    @DisplayName("[DONE] 之后的行一律忽略")
    void doneStopsParsing() {
        List<String> lines = new ArrayList<>(sse(
                "{\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}"));
        lines.add("data: [DONE]");
        lines.add("data: {\"choices\":[{\"delta\":{\"content\":\"ignored\"}}]}");

        List<StreamChunk> chunks = SseParser.parse(lines);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("hi");
    }

    @Test
    @DisplayName("无工具调用时不产出空的工具片")
    void noCallsMeansNoCallChunk() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"content\":\"only text\"}}]}"));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).toolCalls()).isEmpty();
    }

    @Test
    @DisplayName("非 data: 行、空行、坏 JSON 全部跳过")
    void ignoresNonDataAndMalformedLines() {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("event: message");
        lines.add(": comment");
        lines.add("data: not json");
        lines.add("data: {\"choices\":[]}");
        lines.add("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}");

        List<StreamChunk> chunks = SseParser.parse(lines);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("ok");
    }

    @Test
    @DisplayName("多个工具调用按 index 升序输出")
    void multipleCallsSortedByIndex() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":1,\"id\":\"b\","
                        + "\"function\":{\"name\":\"mastery_grade\",\"arguments\":\"{}\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"a\","
                        + "\"function\":{\"name\":\"mastery_status\",\"arguments\":\"{}\"}}]}}]}"));

        List<ToolCall> calls = chunks.get(0).toolCalls();
        assertThat(calls).extracting(ToolCall::name)
                .containsExactly("mastery_status", "mastery_grade");
    }

    @Test
    @DisplayName("后到的空 name 不覆盖先到的 name")
    void laterEmptyNameDoesNotOverwrite() {
        List<StreamChunk> chunks = SseParser.parse(sse(
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\","
                        + "\"function\":{\"name\":\"mastery_quiz\",\"arguments\":\"{\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,"
                        + "\"function\":{\"name\":\"\",\"arguments\":\"}\"}}]}}]}"));

        ToolCall call = chunks.get(0).toolCalls().get(0);
        assertThat(call.name()).isEqualTo("mastery_quiz");
        assertThat(call.arguments()).isEmpty();
        assertThat(call.parseError()).isFalse();
    }

    @Test
    @DisplayName("逐行 feed 与一次性 parse 结果一致")
    void feedMatchesParse() {
        List<String> lines = sse(
                "{\"choices\":[{\"delta\":{\"content\":\"a\"}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c\","
                        + "\"function\":{\"name\":\"mastery_status\",\"arguments\":\"{}\"}}]}}]}");

        SseParser parser = new SseParser();
        List<StreamChunk> streamed = new ArrayList<>();
        for (String line : lines) {
            streamed.addAll(parser.feed(line));
        }
        streamed.addAll(parser.finish());

        assertThat(streamed).hasSize(2);
        assertThat(streamed.get(0).text()).isEqualTo("a");
        assertThat(streamed.get(1).toolCalls()).hasSize(1);
    }

    /** 构造 SSE 行：每个 payload 一行 {@code data:}，末尾补 {@code [DONE]}。 */
    private static List<String> sse(String... payloads) {
        List<String> lines = new ArrayList<>();
        for (String payload : payloads) {
            lines.add("data: " + payload);
            lines.add("");
        }
        lines.add("data: [DONE]");
        lines.add("");
        return lines;
    }
}
