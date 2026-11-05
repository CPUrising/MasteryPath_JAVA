package com.masterypath.service;

import com.masterypath.adapter.llm.FakeLlmClient;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmResponse;
import com.masterypath.adapter.llm.StreamChunk;
import com.masterypath.adapter.llm.ToolCall;
import com.masterypath.storage.ChatStore;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.SettingsStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 普通聊天服务（{@code ../MasteryPath/tests/test_chat_nav.py} +
 * {@code test_knowledge_chat.py} 的 chat 部分 + {@code test_preferences.py}）。
 */
class ChatServiceTest {

    @TempDir
    Path tempDir;

    private LearningStore learningStore;
    private LearningService learningService;
    private ChatStore chatStore;
    private SettingsStore settings;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        learningStore = new LearningStore(tempDir);
        learningService = new LearningService(learningStore);
        chatStore = new ChatStore(tempDir);
        settings = new SettingsStore(tempDir);
    }

    @AfterEach
    void tearDown() {
        chatStore.close();
        learningStore.close();
    }

    // -- 导航工具 -------------------------------------------------------------

    @Test
    @DisplayName("TC-CN1：普通聊天可调只读导航工具，metadata 原样透出")
    void navToolCallAndHandoffSurface() {
        learningService.createPath("p1", "learn stats", "Statistics");
        learningService.build("p1", Map.of("modules", List.of(Map.of(
                "name", "M",
                "knowledge_points", List.of(Map.of("name", "mean", "type", "memory"))))), false);
        configureModels();
        client = new NavClient();
        String sessionId = createSession();

        List<Map<String, Object>> events = run(sessionId, "what am I studying?");

        List<String> types = types(events);
        assertThat(types).contains("tool_call", "tool_result");
        assertThat(types.get(types.size() - 1)).isEqualTo("turn_end");

        Map<String, Object> result = events.stream()
                .filter(event -> "tool_result".equals(event.get("type")))
                .findFirst().orElseThrow();
        assertThat(result).containsEntry("name", "mastery_topics");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> topics = (Map<String, Object>) metadata.get("mastery_topics");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) topics.get("topics");
        assertThat(rows.get(0)).containsEntry("path_id", "p1");
    }

    // -- 落库与标题 -----------------------------------------------------------

    @Test
    @DisplayName("回合落库 user + assistant，标题取首条消息前 40 字")
    void turnPersistsMessagesAndTitle() {
        configureModels();
        client = new FakeLlmClient(List.of(LlmResponse.text("Hello learner")));
        String sessionId = createSession();

        List<Map<String, Object>> events = run(sessionId, "What is gravity?");

        assertThat(types(events)).contains("text_delta", "turn_end");
        Map<String, Object> detail = chatService().sessionDetail(sessionId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) detail.get("messages");
        assertThat(messages).extracting(row -> row.get("role"))
                .containsExactly("user", "assistant");
        assertThat(messages.get(1)).containsEntry("content", "Hello learner");
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) detail.get("session");
        assertThat(session).containsEntry("title", "What is gravity?");
    }

    @Test
    @DisplayName("turn_end 带 session_id 与最新 title")
    void turnEndCarriesSessionAndTitle() {
        configureModels();
        client = new FakeLlmClient(List.of(LlmResponse.text("ok")));
        String sessionId = createSession();

        Map<String, Object> last = run(sessionId, "hi there").getLast();

        assertThat(last).containsEntry("type", "turn_end")
                .containsEntry("reason", "done")
                .containsEntry("session_id", sessionId)
                .containsEntry("title", "hi there");
    }

    // -- 短路 -----------------------------------------------------------------

    @Test
    @DisplayName("空消息 → error，且不落库")
    void blankMessageReportsErrorWithoutPersisting() {
        configureModels();
        client = new FakeLlmClient(List.of());
        String sessionId = createSession();

        List<Map<String, Object>> events = run(sessionId, "   ");

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("type", "error")
                .containsEntry("message", "Type a message first.");
        assertThat(chatStore.listMessages(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("未知会话 → error，不建档")
    void unknownSessionReportsError() {
        configureModels();
        client = new FakeLlmClient(List.of());

        List<Map<String, Object>> events = run("ghost", "hi");

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("type", "error")
                .containsEntry("message", "This conversation no longer exists.");
    }

    @Test
    @DisplayName("无模型配置 → error{error_code: bad_request}，文案含 No language model")
    void missingModelReportsStructuredError() {
        client = new FakeLlmClient(List.of());
        String sessionId = createSession();

        List<Map<String, Object>> events = run(sessionId, "hi");

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("type", "error")
                .containsEntry("error_code", "bad_request")
                .containsEntry("retryable", false);
        assertThat(String.valueOf(events.get(0).get("message")))
                .contains("No language model");
        // 用户消息已落库（参考实现先写 user 再解析模型），但没有 assistant 行。
        assertThat(chatStore.listMessages(sessionId)).hasSize(1);
    }

    @Test
    @DisplayName("模型错误时保留已产出的正文")
    void keepsPartialTextOnError() {
        configureModels();
        client = new PartialClient();
        String sessionId = createSession();

        List<Map<String, Object>> events = run(sessionId, "hi");

        assertThat(types(events)).contains("text_delta");
        assertThat(types(events).getLast()).isEqualTo("error");
        List<Map<String, Object>> messages = chatStore.listMessages(sessionId);
        assertThat(messages).extracting(row -> row.get("role"))
                .containsExactly("user", "assistant");
        assertThat(messages.get(1)).containsEntry("content", "partial answer");
    }

    // -- 历史与附件 -----------------------------------------------------------

    @Test
    @DisplayName("历史只带 user / assistant 且跳过空正文")
    void historySkipsEmptyAssistantRows() {
        configureModels();
        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("first")));
        client = fake;
        String sessionId = createSession();
        run(sessionId, "one");
        chatStore.addMessage(sessionId, "assistant", "", List.of());

        client = new FakeLlmClient(List.of(LlmResponse.text("second")));
        FakeLlmClient second = (FakeLlmClient) client;
        run(sessionId, "two");

        List<Map<String, Object>> history = second.calls().get(0);
        List<String> contents = new ArrayList<>();
        for (Map<String, Object> message : history) {
            if (!"system".equals(message.get("role"))) {
                contents.add(String.valueOf(message.get("content")));
            }
        }
        assertThat(contents).containsExactly("one", "first", "two");
    }

    @Test
    @DisplayName("附件正文进入 system 消息，并记录 name")
    void attachmentsBecomeSystemBlock() {
        configureModels();
        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text("ok")));
        client = fake;
        String sessionId = createSession();

        ChatService service = chatService();
        List<Map<String, Object>> events = new ArrayList<>();
        service.streamTurn(sessionId, "read this",
                        List.of(Map.of("name", "note.md", "text", "binary search halves")))
                .forEachRemaining(events::add);

        String system = String.join("\n", fake.calls().get(0).stream()
                .filter(message -> "system".equals(message.get("role")))
                .map(message -> String.valueOf(message.get("content")))
                .toList());
        assertThat(system).contains("Attachments provided for this message")
                .contains("[note.md]")
                .contains("binary search halves");

        List<Map<String, Object>> messages = chatStore.listMessages(sessionId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stored = (List<Map<String, Object>>) messages.get(0)
                .get("attachments");
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0)).containsEntry("name", "note.md");
    }

    // -- 会话 CRUD ------------------------------------------------------------

    @Test
    @DisplayName("会话 CRUD：列表 / 详情 / 更新 / 删除")
    void sessionCrud() {
        ChatService service = chatService();
        Map<String, Object> created = service.createSession("T", "", List.of());
        String sessionId = String.valueOf(created.get("session_id"));

        assertThat(service.listSessions()).hasSize(1);
        assertThat(service.sessionDetail(sessionId)).containsKeys("session", "messages");
        assertThat(service.updateSession(sessionId, Map.of("title", "New")))
                .containsEntry("title", "New");

        service.deleteSession(sessionId);
        assertThat(service.listSessions()).isEmpty();
    }

    @Test
    @DisplayName("resolveModel 无配置时抛 bad_request")
    void resolveModelWithoutSettings() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> chatService().resolveModel(null)))
                .isInstanceOf(com.masterypath.adapter.llm.LlmError.class)
                .hasMessageContaining("No language model");
    }

    // -- 辅助 -----------------------------------------------------------------

    private ChatService chatService() {
        return new ChatService(chatStore, settings, null,
                config -> client, learningService);
    }

    private String createSession() {
        Map<String, Object> session = chatStore.createSession("", "", List.of(), Map.of());
        return String.valueOf(session.get("session_id"));
    }

    private List<Map<String, Object>> run(String sessionId, String message) {
        List<Map<String, Object>> events = new ArrayList<>();
        chatService().streamTurn(sessionId, message, List.of()).forEachRemaining(events::add);
        return events;
    }

    private static List<String> types(List<Map<String, Object>> events) {
        List<String> types = new ArrayList<>();
        for (Map<String, Object> event : events) {
            types.add(String.valueOf(event.get("type")));
        }
        return types;
    }

    private void configureModels() {
        settings.writeSection("providers", List.of(Map.of(
                "id", "fake",
                "name", "Fake",
                "base_url", "http://127.0.0.1:1/v1",
                "api_key", "",
                "protocol", "openai")));
        settings.writeSection("llm", Map.of(
                "models", List.of(Map.of(
                        "id", "m1", "provider_id", "fake", "model", "fake", "name", "fake")),
                "active_id", "m1"));
    }

    /** 首轮调工具、次轮出正文（对应参考实现的 {@code FakeNavClient}）。 */
    private static final class NavClient implements LlmClient {
        private int calls;

        @Override
        public LlmResponse chat(List<Map<String, Object>> messages,
                                List<Map<String, Object>> tools) {
            throw new UnsupportedOperationException("chat is not used");
        }

        @Override
        public List<StreamChunk> streamChatEvents(List<Map<String, Object>> messages,
                                                  List<Map<String, Object>> tools) {
            calls++;
            if (calls == 1) {
                return List.of(StreamChunk.calls(List.of(ToolCall.of("mastery_topics"))));
            }
            return List.of(StreamChunk.text("Here are your topics."));
        }
    }

    /** 首轮产出正文 + 工具调用，次轮抛错（复现「流中途失败」的可观察行为）。 */
    private static final class PartialClient implements LlmClient {
        private int calls;

        @Override
        public LlmResponse chat(List<Map<String, Object>> messages,
                                List<Map<String, Object>> tools) {
            throw new UnsupportedOperationException("chat is not used");
        }

        @Override
        public List<StreamChunk> streamChatEvents(List<Map<String, Object>> messages,
                                                  List<Map<String, Object>> tools) {
            calls++;
            if (calls == 1) {
                return List.of(
                        StreamChunk.text("partial answer"),
                        StreamChunk.calls(List.of(ToolCall.of("mastery_topics"))));
            }
            throw new com.masterypath.adapter.llm.LlmError(
                    "stream broke", "upstream_error", true);
        }
    }
}
