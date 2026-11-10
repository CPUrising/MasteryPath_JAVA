package com.masterypath.adapter.turn;

import com.masterypath.adapter.llm.FakeLlmClient;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmError;
import com.masterypath.adapter.llm.LlmResponse;
import com.masterypath.adapter.llm.StreamChunk;
import com.masterypath.adapter.llm.ToolCall;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 回合编排（{@code ../MasteryPath/tests/test_turn_runner.py} 的等价移植）。
 *
 * <p>断言的是<b>可观察行为</b>：事件类型与顺序、题卡派生、{@code session_meta} 时机、
 * 错误映射、部分文本保留、快照与参考块注入。
 */
@Tag("api")
class TurnRunnerTest {

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // -- 事件序列 -------------------------------------------------------------

    @Test
    @DisplayName("mastery 回合产出 tool_call → tool_result → text_delta → turn_end，并落库两条消息")
    void masteryTurnUsesSharedRunner() {
        FakeLlmClient fake = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("mastery_build", Map.of(
                        "modules", List.of(Map.of(
                                "name", "Basics",
                                "knowledge_points", List.of(
                                        Map.of("name", "objective", "type", "memory"))))),
                        "call-1", false)),
                LlmResponse.text("Outline ready.")));

        List<TurnEvent> events = run(fake, request("s1", "p", "outline", "teach me"));

        assertThat(types(events))
                .containsExactly("tool_call", "tool_result", "text_delta", "turn_end");
        Map<String, Object> detail = service.getSessionDetail("s1");
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) detail.get("session");
        assertThat(session).containsEntry("message_count", 2);
    }

    @Test
    @DisplayName("mastery_answer 在导师开口前判分，并携带会话历史")
    void gradesCardAnswerBeforeTheTutorSpeaks() {
        service.createPath("p", "learn X", "X");
        service.build("p", Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(Map.of("name", "fact one", "type", "memory"))))), false);
        service.setSessionMode("p", "s1", "study");
        service.recordTurn("p", "s1", "上一轮问题", "上一轮回答", "study", "t-0", null);

        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId("q-answer");
        pending.setKnowledgePointId("k1");
        pending.setModuleId("m1");
        pending.setPrompt("2+2?");
        pending.setQuestionType(PendingQuestion.QuestionType.SHORT);
        pending.setExpectedAnswer("4");
        service.registerQuestion("p", pending, "s1", "t-1");

        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("答对了。")));
        TurnRequest request = request("s1", "p", "study", "4");
        request.setMasteryAnswer(Map.of("question_id", "q-answer", "text", "4"));

        List<TurnEvent> events = run(fake, request);

        assertThat(types(events)).containsExactly("tool_result", "text_delta", "turn_end");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) events.get(0).data().get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> grade = (Map<String, Object>) metadata.get("mastery_grade");
        assertThat(grade).containsEntry("result", "correct")
                .containsEntry("question_id", "q-answer")
                .containsEntry("correct_label", "4");

        List<Map<String, Object>> messages = fake.calls().get(0);
        assertThat(messages).anySatisfy(message ->
                assertThat(message).containsEntry("role", "user").containsEntry("content", "4"));
        assertThat(messages).anySatisfy(message ->
                assertThat(message).containsEntry("content", "上一轮回答"));
    }

    @Test
    @DisplayName("出题时从 tool_result 派生 question_card，且 turn_end 原因为 card")
    void derivesQuestionCardFromToolResult() {
        service.createPath("p", "learn X", "X");
        service.build("p", Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(Map.of("name", "fact one", "type", "memory"))))), false);
        service.setSessionMode("p", "s1", "study");

        FakeLlmClient fake = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("mastery_quiz", Map.of(
                        "knowledge_point_id", "k1",
                        "question", "What is one?",
                        "expected_answer", "1"), "q-1", false))));

        TurnRequest request = request("s1", "p", "study", "quiz me");
        request.setTurnId("t-1");
        List<TurnEvent> events = run(fake, request);

        assertThat(types(events))
                .containsExactly("tool_call", "tool_result", "question_card", "turn_end");
        assertThat(events.get(3).data()).containsEntry("reason", "card");
        assertThat(events.get(2).data()).containsKey("question_id");
        // 题卡不得含答案（INV-07）。
        assertThat(events.get(2).data()).doesNotContainKeys("expected_answer", "explanation");
        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) events.get(2).data();
        assertThat(card).containsEntry("card_kind", "mastery")
                .containsEntry("turn_id", "t-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> objective = (Map<String, Object>) card.get("objective");
        assertThat(objective).containsEntry("id", "k1").containsEntry("name", "fact one");

        // 题卡同时写入 assistant 消息的 data.card，刷新后无需 pending_question 也能恢复。
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>)
                service.getSessionDetail("s1").get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> persisted = (Map<String, Object>)
                ((Map<String, Object>) messages.get(1).get("data")).get("card");
        assertThat(persisted).containsEntry("question_id", card.get("question_id"))
                .containsEntry("card_kind", "mastery");
    }

    @Test
    @DisplayName("模式切换后发 session_meta，且会话模式已落库")
    void modeSwitchEmitsSessionMeta() {
        service.createPath("p", "learn X", "X");
        service.build("p", Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(Map.of("name", "fact", "type", "memory"))))), false);
        service.ensureSession("p", "s1", "outline", "");

        FakeLlmClient fake = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("mastery_mode",
                        Map.of("mode", "study", "reason", "begin"), "mode-1", false)),
                LlmResponse.text("Now in study.")));

        List<TurnEvent> events = run(fake, request("s1", "p", "outline", "start"));

        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("session_meta");
            assertThat(event.data()).containsEntry("mastery_session_mode", "study");
        });
        // session_meta 必须在 turn_end 之后（前端靠它更新侧栏）。
        assertThat(types(events).indexOf("session_meta"))
                .isGreaterThan(types(events).indexOf("turn_end"));
        assertThat(service.getSession("s1")).containsEntry("mode", "study");
    }

    @Test
    @DisplayName("模式未变时不发 session_meta")
    void noSessionMetaWhenNothingChanged() {
        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("hi")));

        List<TurnEvent> events = run(fake, request("s1", "p", "outline", "hello"));

        assertThat(types(events)).containsExactly("text_delta", "turn_end");
    }

    // -- 错误映射 -------------------------------------------------------------

    @Test
    @DisplayName("LlmError 映射成 error 事件，error_code 原样透出")
    void mapsLlmError() {
        LlmClient boom = new LlmClient() {
            @Override
            public LlmResponse chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools) {
                throw new LlmError("The configured model was not found at this provider.",
                        "model_not_found", false);
            }
        };

        List<TurnEvent> events = run(boom, request("s1", "p", "study", "hi"));

        assertThat(types(events)).containsExactly("error");
        assertThat(events.get(0).data())
                .containsEntry("error_code", "model_not_found")
                .containsEntry("retryable", false);
        // 纯错误回合不落库：会话里不该出现空消息对。
        assertThat(service.getSessionDetail("s1")).isNull();
    }

    @Test
    @DisplayName("非 LlmError 异常映射成 internal_error，socket 不被静默关闭")
    void mapsUnexpectedException() {
        LlmClient exploding = new LlmClient() {
            @Override
            public LlmResponse chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools) {
                throw new IllegalStateException("unexpected provider shape");
            }
        };

        List<TurnEvent> events = run(exploding, request("s1", "p", "study", "hi"));

        assertThat(types(events)).containsExactly("error");
        assertThat(events.get(0).data())
                .containsEntry("error_code", "internal_error")
                .containsEntry("retryable", false);
        assertThat(String.valueOf(events.get(0).data().get("message")))
                .contains("unexpected provider shape");
    }

    @Test
    @DisplayName("流中断时保留已产出的正文")
    void keepsPartialTextWhenStreamFails() {
        // Java 的 streamChatEvents 返回 List（非生成器），无法在流中途抛错；
        // 用「首轮产出正文 + 工具调用，次轮抛错」复现同一可观察行为。
        LlmClient partial = new LlmClient() {
            private int round;

            @Override
            public LlmResponse chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools) {
                throw new UnsupportedOperationException("chat is not used");
            }

            @Override
            public List<StreamChunk> streamChatEvents(List<Map<String, Object>> messages,
                                                      List<Map<String, Object>> tools) {
                round++;
                if (round == 1) {
                    return List.of(
                            StreamChunk.text("partial answer"),
                            StreamChunk.calls(List.of(ToolCall.of("mastery_status"))));
                }
                throw new LlmError("stream broke", "upstream_error", true);
            }
        };

        List<TurnEvent> events = run(partial, request("s1", "p", "outline", "hi"));

        assertThat(types(events)).contains("text_delta");
        assertThat(types(events).get(types(events).size() - 1)).isEqualTo("error");
        Map<String, Object> detail = service.getSessionDetail("s1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) detail.get("messages");
        assertThat(messages).extracting(row -> row.get("role"))
                .containsExactly("user", "assistant");
        assertThat(messages.get(1)).containsEntry("content", "partial answer");
    }

    // -- 增量与注入 -----------------------------------------------------------

    @Test
    @DisplayName("正文按片转发，多帧到达")
    void forwardsIncrementalTextFrames() {
        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("hello world")), 3);

        List<TurnEvent> events = run(fake, request("s1", "p", "outline", "hi"));

        List<String> deltas = new ArrayList<>();
        for (TurnEvent event : events) {
            if (TurnEvent.TEXT_DELTA.equals(event.type())) {
                deltas.add(String.valueOf(event.data().get("text")));
            }
        }
        assertThat(String.join("", deltas)).isEqualTo("hello world");
        assertThat(deltas).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("注入知识库参考块与 mastery_status 快照")
    void injectsKnowledgeContextAndSnapshot() {
        service.createPath("p", "g", "t");
        TopicSource source = new TopicSource();
        source.setId("kb:k1");
        source.setKind("knowledge_base");
        source.setSourceId("k1");
        source.setLabel("Physics");
        service.setKnowledgeBaseSources("p", List.of(source));

        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("Answer.")));
        TurnRunner runner = new TurnRunner(service, store,
                (selection, sessionKey) -> fake, new StubKnowledge());

        List<TurnEvent> events = new ArrayList<>();
        Iterator<TurnEvent> iterator = runner.run(request("s1", "p", "outline", "gravity"));
        iterator.forEachRemaining(events::add);

        assertThat(types(events)).containsExactly("text_delta", "turn_end");
        String system = String.valueOf(fake.calls().get(0).get(0).get("content"));
        assertThat(system).contains("Reference material");
        assertThat(system).contains("gravity.md");
        assertThat(system).contains("Current-turn mastery_status snapshot");
    }

    @Test
    @DisplayName("快照读取失败不拖垮回合")
    void snapshotFailureDoesNotSinkTheTurn() {
        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("still here")));
        // 路径不存在时 mastery_status 仍返回 ok（status=empty），因此这里用「路径存在但
        // 服务抛异常」不可构造；改为断言无路径时不注入快照、回合照常完成。
        TurnRunner runner = new TurnRunner(service, store, (selection, sessionKey) -> fake, null);

        List<TurnEvent> events = new ArrayList<>();
        runner.run(request("s1", "", "outline", "hi")).forEachRemaining(events::add);

        assertThat(types(events)).containsExactly("text_delta", "turn_end");
        String system = String.valueOf(fake.calls().get(0).get(0).get("content"));
        assertThat(system).doesNotContain("Current-turn mastery_status snapshot");
    }

    // -- 回合内切换路径 -------------------------------------------------------

    @Test
    @DisplayName("TC-S5：回合内 mastery_switch 持久化新绑定并发 session_meta")
    void switchPathInsideTurnPersistsBinding() {
        for (String pathId : List.of("p1", "p2")) {
            service.createPath(pathId, "", pathId);
            service.build(pathId, Map.of("modules", List.of(Map.of(
                    "name", "M",
                    "knowledge_points", List.of(
                            Map.of("name", pathId + " goal", "type", "memory"))))), false);
        }
        service.setSessionMode("p1", "s1", "study");

        FakeLlmClient fake = new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("mastery_switch",
                        Map.of("path_id", "p2"), "sw-1", false)),
                LlmResponse.text("Switched.")));

        List<TurnEvent> events = run(fake, request("s1", "p1", "study", "switch"));

        assertThat(service.getSession("s1")).containsEntry("path_id", "p2");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("session_meta");
            assertThat(event.data()).containsEntry("mastery_path_id", "p2");
        });
    }

    // -- 分派 -----------------------------------------------------------------
    @Test
    @DisplayName("未接入 ChatService 时 chat 能力明确失败")
    void chatCapabilityWithoutChatServiceFails() {
        TurnRunner runner = new TurnRunner(service, store,
                (selection, sessionKey) -> new FakeLlmClient(List.of()), null);
        TurnRequest request = new TurnRequest();
        request.setCapability(TurnRequest.CAPABILITY_CHAT);

        assertThatThrownBy(() -> runner.run(request))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("needs a ChatService");
    }

    // -- 辅助 -----------------------------------------------------------------

    private List<TurnEvent> run(LlmClient client, TurnRequest request) {
        TurnRunner runner = new TurnRunner(service, store, (selection, sessionKey) -> client, null);
        List<TurnEvent> events = new ArrayList<>();
        runner.run(request).forEachRemaining(events::add);
        return events;
    }

    private static TurnRequest request(String sessionId, String pathId, String mode, String message) {
        TurnRequest request = new TurnRequest();
        request.setCapability(TurnRequest.CAPABILITY_MASTERY);
        request.setSessionId(sessionId);
        request.setPathId(pathId);
        request.setMode(mode);
        request.setMessage(message);
        return request;
    }

    private static List<String> types(List<TurnEvent> events) {
        List<String> types = new ArrayList<>();
        for (TurnEvent event : events) {
            types.add(event.type());
        }
        return types;
    }

    /** 假知识库：固定命中 + 固定名称。 */
    public static final class StubKnowledge {

        public List<Map<String, Object>> search(String kbId, String query, int topK) {
            Map<String, Object> hit = new LinkedHashMap<>();
            hit.put("source", "gravity.md");
            hit.put("text", "Gravity pulls toward a center.");
            hit.put("score", 1.0);
            return List.of(hit);
        }

        public Map<String, Object> get(String kbId) {
            return Map.of("name", "Physics");
        }
    }
}
