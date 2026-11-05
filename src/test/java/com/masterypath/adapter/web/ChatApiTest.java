package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.adapter.llm.FakeLlmClient;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmResponse;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.ChatService;
import com.masterypath.service.LearningService;
import com.masterypath.storage.ChatStore;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.SettingsStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 聊天 REST + SSE（{@code ../MasteryPath/tests/test_knowledge_chat.py} 的 chat 部分）。
 *
 * <p>用 {@code standaloneSetup}：控制器只依赖 {@link ChatService}，手工装配即可隔离。
 */
class ChatApiTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    @TempDir
    Path tempDir;

    private LearningStore learningStore;
    private ChatStore chatStore;
    private SettingsStore settings;
    private MockMvc mvc;
    private final AtomicReference<LlmClient> client = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        learningStore = new LearningStore(tempDir);
        chatStore = new ChatStore(tempDir);
        settings = new SettingsStore(tempDir);
        LearningService learningService = new LearningService(learningStore);
        ChatService service = new ChatService(chatStore, settings, null,
                config -> client.get(), learningService);
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(MAPPER))
                .build();
    }

    @AfterEach
    void tearDown() {
        chatStore.close();
        learningStore.close();
    }

    // -- 会话 CRUD ------------------------------------------------------------

    @Test
    @DisplayName("创建返回 201；列表含 message_count / last_message")
    void createAndList() throws Exception {
        String sessionId = createSession();

        List<Map<String, Object>> listed = jsonList(
                mvc.perform(get("/chat/sessions")).andReturn());
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0)).containsEntry("session_id", sessionId)
                .containsEntry("message_count", 0)
                .containsEntry("last_message", "");
    }

    @Test
    @DisplayName("详情为 {session, messages}；未知会话 404")
    void detailAndUnknown() throws Exception {
        String sessionId = createSession();

        Map<String, Object> detail = json(
                mvc.perform(get("/chat/sessions/" + sessionId)).andReturn());
        assertThat(detail).containsKeys("session", "messages");

        assertThat(mvc.perform(get("/chat/sessions/ghost"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("PATCH 更新标题；未知会话 404")
    void patchAndUnknown() throws Exception {
        String sessionId = createSession();

        Map<String, Object> updated = json(mvc.perform(patch("/chat/sessions/" + sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\"}"))
                .andReturn());
        assertThat(updated).containsEntry("title", "Renamed");

        assertThat(mvc.perform(patch("/chat/sessions/ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("DELETE 返回 204；再删 404")
    void deleteThen404() throws Exception {
        String sessionId = createSession();

        assertThat(mvc.perform(delete("/chat/sessions/" + sessionId))
                .andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(mvc.perform(delete("/chat/sessions/" + sessionId))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    // -- SSE 回合 -------------------------------------------------------------

    @Test
    @DisplayName("SSE 流含 text_delta 与 turn_end，且落库两条消息")
    void streamPersistsMessages() throws Exception {
        configureModels();
        client.set(new FakeLlmClient(List.of(LlmResponse.text("Hello learner"))));
        String sessionId = createSession();

        String body = streamTurn(sessionId, "What is gravity?");

        assertThat(body).contains("\"type\":\"text_delta\"")
                .contains("\"type\":\"turn_end\"");

        Map<String, Object> detail = json(
                mvc.perform(get("/chat/sessions/" + sessionId)).andReturn());
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
    @DisplayName("无模型配置的回合返回结构化 error 帧")
    void streamWithoutModelReportsError() throws Exception {
        client.set(new FakeLlmClient(List.of()));
        String sessionId = createSession();

        String body = streamTurn(sessionId, "hi");

        assertThat(body).contains("\"type\":\"error\"").contains("No language model");
    }

    @Test
    @DisplayName("SSE 响应头防缓冲")
    void streamSetsNoBufferingHeaders() throws Exception {
        configureModels();
        client.set(new FakeLlmClient(List.of(LlmResponse.text("ok"))));
        String sessionId = createSession();

        MvcResult result = mvc.perform(post("/chat/sessions/" + sessionId + "/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(result.getResponse().getContentType()).contains("text/event-stream");
    }

    // -- 辅助 -----------------------------------------------------------------

    /**
     * 跑一个 SSE 回合并取回响应体。
     *
     * <p>{@link StreamingResponseBody} 是异步的：必须 {@code asyncDispatch} 才能读到正文。
     */
    private String streamTurn(String sessionId, String message) throws Exception {
        MvcResult started = mvc.perform(post("/chat/sessions/" + sessionId + "/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(Map.of("message", message))))
                .andReturn();
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .asyncDispatch(started))
                .andReturn().getResponse().getContentAsString();
    }

    private String createSession() throws Exception {
        Map<String, Object> created = json(mvc.perform(post("/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn());
        return String.valueOf(created.get("session_id"));
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

    private static Map<String, Object> json(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    private static List<Map<String, Object>> jsonList(MvcResult result) throws Exception {
        return MAPPER.readValue(result.getResponse().getContentAsString(), List.class);
    }
}
