package com.masterypath.adapter.loop;

import com.masterypath.adapter.llm.FakeLlmClient;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmResponse;
import com.masterypath.adapter.llm.ToolCall;
import com.masterypath.adapter.tool.Tool;
import com.masterypath.adapter.tool.ToolContext;
import com.masterypath.adapter.tool.ToolResult;
import com.masterypath.domain.mode.BehaviorBudget;
import com.masterypath.domain.mode.SessionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回合循环（参考实现 {@code agents/loop.py}）。
 *
 * <p>对照标准：{@code ../MasteryPath/tests/test_loop.py}。
 * 用假工具而非真实 mastery 工具，因此不依赖服务层——真实工具的用例属 {@code 0017}。
 */
class AgentLoopTest {

    // -- 基本流程 -------------------------------------------------------------

    @Test
    @DisplayName("模型不再请求工具时以 done 结束")
    void finishesWithDone() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("hello")));

        List<StreamEvent> events = loop(Map.of()).run(client, user("hi"), ctx());

        assertThat(types(events)).containsExactly("text_delta", "turn_end");
        assertThat(events.get(1).data()).containsEntry("reason", "done");
    }

    @Test
    @DisplayName("工具执行后进入下一轮，事件顺序为 tool_call → tool_result → text_delta → turn_end")
    void executesToolThenContinues() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("echo", Map.of("v", 1), "c1", false)),
                LlmResponse.text("done")));

        List<StreamEvent> events = loop(Map.of("echo", echoTool()))
                .run(client, user("go"), ctx());

        assertThat(types(events)).containsExactly(
                "tool_call", "tool_result", "text_delta", "turn_end");
        assertThat(events.get(1).data()).containsEntry("ok", true)
                .containsEntry("content", "echo:1");
        assertThat(events.get(3).data()).containsEntry("reason", "done");
    }

    @Test
    @DisplayName("系统提示词按模式框定")
    void systemPromptFramesMode() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("ok")));
        ToolContext ctx = ctx();
        ctx.setSessionMode(SessionMode.OUTLINE);

        loop(Map.of()).run(client, user("x"), ctx);

        String system = String.valueOf(client.calls().get(0).get(0).get("content"));
        assertThat(system).contains("Current mode: outline")
                .contains("This mode is for designing the learning outline");
    }

    @Test
    @DisplayName("未记录模式时提示词按 study 框定")
    void unrecordedModeFramesAsStudy() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("ok")));

        loop(Map.of()).run(client, user("x"), ctx());

        String system = String.valueOf(client.calls().get(0).get(0).get("content"));
        assertThat(system).contains("Current mode: study");
    }

    @Test
    @DisplayName("extraContext 追加在提示词末尾")
    void extraContextAppended() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("ok")));

        loop(Map.of()).run(client, user("x"), ctx(), null, "SNAPSHOT");

        String system = String.valueOf(client.calls().get(0).get(0).get("content"));
        assertThat(system).endsWith("\n\nSNAPSHOT");
    }

    @Test
    @DisplayName("自定义系统提示词覆盖默认")
    void customSystemPromptWins() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("ok")));

        loop(Map.of()).run(client, user("x"), ctx(), "CUSTOM", "");

        assertThat(client.calls().get(0).get(0)).containsEntry("content", "CUSTOM");
    }

    // -- 工具定义 -------------------------------------------------------------

    @Test
    @DisplayName("工具定义按名字升序传给客户端")
    void toolDefinitionsSorted() {
        FakeLlmClient client = new FakeLlmClient(List.of(LlmResponse.text("ok")));
        Map<String, Tool> tools = new LinkedHashMap<>();
        tools.put("zeta", echoTool());
        tools.put("alpha", echoTool());

        loop(tools).run(client, user("x"), ctx());

        List<Map<String, Object>> seen = client.toolsSeen().get(0);
        assertThat(seen).extracting(item -> function(item).get("name"))
                .containsExactly("alpha", "zeta");
        assertThat(seen).allSatisfy(item ->
                assertThat(function(item)).containsKeys("name", "description", "parameters"));
    }

    // -- 预算 -----------------------------------------------------------------

    @Test
    @DisplayName("单回合工具调用超预算时以 budget 结束，且超限那次不执行")
    void budgetStopsBeforeExecuting() {
        // 一轮内请求 7 次调用（上限 6）：第 7 次不执行，直接以 budget 结束。
        int over = BehaviorBudget.MAX_TOOL_CALLS_PER_TURN + 1;
        List<ToolCall> calls = new ArrayList<>();
        for (int index = 0; index < over; index++) {
            calls.add(new ToolCall("echo", Map.of("v", index), "c" + index, false));
        }
        FakeLlmClient client = new FakeLlmClient(List.of(
                new LlmResponse("", calls),
                LlmResponse.text("should not be reached")));
        CountingTool tool = new CountingTool();

        List<StreamEvent> events = loop(Map.of("echo", tool)).run(client, user("x"), ctx());

        assertThat(events.get(events.size() - 1).data()).containsEntry("reason", "budget");
        assertThat(tool.calls).isEqualTo(BehaviorBudget.MAX_TOOL_CALLS_PER_TURN);
        assertThat(types(events)).filteredOn("tool_call"::equals)
                .hasSize(BehaviorBudget.MAX_TOOL_CALLS_PER_TURN);
        // 第二轮没有被调用。
        assertThat(client.calls()).hasSize(1);
    }

    @Test
    @DisplayName("预算跨轮累计，不是每轮重置")
    void budgetAccumulatesAcrossRounds() {
        // 每轮 3 次调用，两轮共 6 次（恰好用满），第三轮第 1 次即超限。
        List<LlmResponse> script = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            script.add(LlmResponse.calls(
                    new ToolCall("echo", Map.of(), "a" + round, false),
                    new ToolCall("echo", Map.of(), "b" + round, false),
                    new ToolCall("echo", Map.of(), "c" + round, false)));
        }
        FakeLlmClient client = new FakeLlmClient(script);
        CountingTool tool = new CountingTool();

        List<StreamEvent> events = loop(Map.of("echo", tool)).run(client, user("x"), ctx());

        assertThat(events.get(events.size() - 1).data()).containsEntry("reason", "budget");
        assertThat(tool.calls).isEqualTo(BehaviorBudget.MAX_TOOL_CALLS_PER_TURN);
    }

    // -- 出题结束回合 ---------------------------------------------------------

    @Test
    @DisplayName("工具调用 endTurn 后先交付 tool_result，再以 card 结束")
    void endTurnYieldsCardAfterResult() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("quiz", Map.of(), "c1", false)),
                LlmResponse.text("should not be reached")));

        List<StreamEvent> events = loop(Map.of("quiz", quizTool()))
                .run(client, user("x"), ctx());

        assertThat(types(events)).containsExactly("tool_call", "tool_result", "turn_end");
        assertThat(events.get(1).data()).containsEntry("ok", true);
        assertThat(events.get(2).data()).containsEntry("reason", "card");
        // 第二轮没有被调用。
        assertThat(client.calls()).hasSize(1);
    }

    // -- 轮数耗尽 -------------------------------------------------------------

    @Test
    @DisplayName("轮数耗尽时以 max_rounds 结束")
    void stopsAtMaxRounds() {
        List<LlmResponse> script = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            script.add(LlmResponse.calls(new ToolCall("echo", Map.of(), "c" + index, false)));
        }
        FakeLlmClient client = new FakeLlmClient(script);

        List<StreamEvent> events = new AgentLoop(Map.of("echo", echoTool()), 2)
                .run(client, user("x"), ctx());

        assertThat(events.get(events.size() - 1).data()).containsEntry("reason", "max_rounds");
        assertThat(client.calls()).hasSize(2);
    }

    // -- 工具失败三降级 -------------------------------------------------------

    @Test
    @DisplayName("坏参数降级为 invalid tool arguments，回合继续")
    void badArgumentsDegrade() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("echo", Map.of(), "c1", true)),
                LlmResponse.text("recovered")));

        List<StreamEvent> events = loop(Map.of("echo", echoTool()))
                .run(client, user("x"), ctx());

        assertThat(events.get(1).data()).containsEntry("ok", false)
                .containsEntry("content", "invalid tool arguments");
        assertThat(events.get(events.size() - 1).data()).containsEntry("reason", "done");
    }

    @Test
    @DisplayName("未知工具降级为 unknown tool，回合继续")
    void unknownToolDegrades() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("nope", Map.of(), "c1", false)),
                LlmResponse.text("recovered")));

        List<StreamEvent> events = loop(Map.of()).run(client, user("x"), ctx());

        assertThat(events.get(1).data()).containsEntry("ok", false)
                .containsEntry("content", "unknown tool: nope");
        assertThat(events.get(events.size() - 1).data()).containsEntry("reason", "done");
    }

    @Test
    @DisplayName("工具抛异常降级为 tool failed，回合继续")
    void raisingToolDegrades() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("boom", Map.of(), "c1", false)),
                LlmResponse.text("recovered")));

        List<StreamEvent> events = loop(Map.of("boom", boomTool()))
                .run(client, user("x"), ctx());

        assertThat(events.get(1).data()).containsEntry("ok", false);
        assertThat(String.valueOf(events.get(1).data().get("content")))
                .startsWith("tool failed: ").contains("kaboom");
        assertThat(events.get(events.size() - 1).type()).isEqualTo("turn_end");
    }

    // -- 工具历史 -------------------------------------------------------------

    @Test
    @DisplayName("第二轮历史含 assistant tool_calls 与配对的 tool 消息")
    void writesOpenAiToolHistory() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("echo", Map.of("v", 1), "call-1", false)),
                LlmResponse.text("done")));

        loop(Map.of("echo", echoTool())).run(client, user("x"), ctx());

        List<Map<String, Object>> secondRound = client.calls().get(1);
        Map<String, Object> assistant = secondRound.stream()
                .filter(message -> "assistant".equals(message.get("role")))
                .findFirst()
                .orElseThrow();
        assertThat(assistant).containsKey("tool_calls");
        List<?> toolCalls = (List<?>) assistant.get("tool_calls");
        Map<?, ?> first = (Map<?, ?>) toolCalls.get(0);
        assertThat(first.get("id")).isEqualTo("call-1");
        assertThat(((Map<?, ?>) first.get("function")).get("name")).isEqualTo("echo");

        Map<String, Object> toolMessage = secondRound.stream()
                .filter(message -> "tool".equals(message.get("role")))
                .findFirst()
                .orElseThrow();
        assertThat(toolMessage).containsEntry("tool_call_id", "call-1");
    }

    @Test
    @DisplayName("assistant 消息正文为空时写 null")
    void emptyAssistantContentIsNull() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("echo", Map.of(), "c1", false)),
                LlmResponse.text("done")));

        loop(Map.of("echo", echoTool())).run(client, user("x"), ctx());

        Map<String, Object> assistant = client.calls().get(1).stream()
                .filter(message -> "assistant".equals(message.get("role")))
                .findFirst()
                .orElseThrow();
        assertThat(assistant).containsEntry("content", null);
    }

    @Test
    @DisplayName("有正文时 assistant 消息保留正文")
    void assistantContentKept() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                new LlmResponse("Let me check. ",
                        List.of(new ToolCall("echo", Map.of(), "c1", false))),
                LlmResponse.text("done")));

        loop(Map.of("echo", echoTool())).run(client, user("x"), ctx());

        Map<String, Object> assistant = client.calls().get(1).stream()
                .filter(message -> "assistant".equals(message.get("role")))
                .findFirst()
                .orElseThrow();
        assertThat(assistant).containsEntry("content", "Let me check. ");
    }

    // -- 惰性流 ---------------------------------------------------------------

    @Test
    @DisplayName("text_delta 在工具执行之前到达调用方")
    void textArrivesBeforeToolRuns() {
        FakeLlmClient client = new FakeLlmClient(List.of(
                new LlmResponse("Let me check. ",
                        List.of(new ToolCall("echo", Map.of(), "c1", false))),
                LlmResponse.text("done")), 4);
        CountingTool tool = new CountingTool();

        Iterator<StreamEvent> stream = loop(Map.of("echo", tool))
                .stream(client, user("x"), ctx());

        StreamEvent first = stream.next();
        assertThat(first.type()).isEqualTo("text_delta");
        assertThat(first.data()).containsEntry("text", "Let ");
        // 引导语已到达调用方，但工具尚未执行。
        assertThat(tool.calls).isZero();

        List<StreamEvent> rest = new ArrayList<>();
        stream.forEachRemaining(rest::add);
        assertThat(types(rest)).contains("tool_call", "tool_result");
        assertThat(tool.calls).isEqualTo(1);
        assertThat(rest.get(rest.size() - 1).type()).isEqualTo("turn_end");
    }

    @Test
    @DisplayName("只实现 chat 的客户端也能跑完回合")
    void plainClientWorks() {
        LlmClient plain = (messages, tools) -> LlmResponse.text("plain answer");

        List<StreamEvent> events = loop(Map.of()).run(plain, user("x"), ctx());

        assertThat(types(events)).containsExactly("text_delta", "turn_end");
        assertThat(events.get(0).data()).containsEntry("text", "plain answer");
    }

    // -- 辅助 -----------------------------------------------------------------

    private static AgentLoop loop(Map<String, Tool> tools) {
        return new AgentLoop(tools);
    }

    private static ToolContext ctx() {
        return new ToolContext(null, "p");
    }

    private static List<Map<String, Object>> user(String text) {
        return List.of(Map.of("role", "user", "content", text));
    }

    private static List<String> types(List<StreamEvent> events) {
        List<String> types = new ArrayList<>();
        for (StreamEvent event : events) {
            types.add(event.type());
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> function(Map<String, Object> definition) {
        return (Map<String, Object>) definition.get("function");
    }

    /** 回显工具：把 {@code v} 拼进结果。 */
    private static Tool echoTool() {
        return new Tool() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String description() {
                return "echo the value";
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of("v", Map.of("type", "integer")));
            }

            @Override
            public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
                return ToolResult.ok("echo:" + params.get("v"));
            }
        };
    }

    /** 出题工具：调用 {@code endTurn}。 */
    private static Tool quizTool() {
        return new Tool() {
            @Override
            public String name() {
                return "quiz";
            }

            @Override
            public String description() {
                return "pose a question";
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
                ctx.endTurn();
                return ToolResult.ok("question posed");
            }
        };
    }

    /** 抛异常的工具。 */
    private static Tool boomTool() {
        return new Tool() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "always raises";
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
                throw new IllegalStateException("kaboom");
            }
        };
    }

    /** 计数工具：记录被调用次数。 */
    private static final class CountingTool implements Tool {
        private int calls;

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "count calls";
        }

        @Override
        public Map<String, Object> parameters() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
            calls++;
            return ToolResult.ok("ok");
        }
    }
}
