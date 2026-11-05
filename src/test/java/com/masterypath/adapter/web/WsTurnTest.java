package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.adapter.llm.FakeLlmClient;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmError;
import com.masterypath.adapter.llm.LlmResponse;
import com.masterypath.adapter.llm.ToolCall;
import com.masterypath.adapter.turn.TurnRunner;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.service.LearningService;
import com.masterypath.storage.LearningStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /ws} 端到端（{@code test_ws_runtime.py} + {@code test_turn_runner.py} 的传输层部分）。
 *
 * <p>必须走真实端口：{@code MockMvc} 不处理 WebSocket 升级。因此用
 * {@code RANDOM_PORT} + {@link StandardWebSocketClient}。
 *
 * <p>本类同时证明「WS 实现已安装」——{@code spring-boot-starter-websocket} 在依赖里，
 * 且端点真的能完成一次握手与逐帧推送。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(WsTurnTest.FakeClientConfig.class)
class WsTurnTest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();

    /** 每个用例替换一次脚本化客户端。 */
    private static final AtomicReference<LlmClient> CLIENT = new AtomicReference<>();

    @LocalServerPort
    private int port;

    @Autowired
    private LearningService service;

    /**
     * 本次运行的唯一后缀。
     *
     * <p>本类走完整 Spring 上下文，工作目录是 {@code target/test-workspace}（跨运行保留），
     * 因此固定 id 会让上一次运行的会话残留、{@code message_count} 累加。
     */
    private String run;

    @BeforeEach
    void setUp() {
        run = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        CLIENT.set(new FakeLlmClient(List.of(LlmResponse.text("(unset)"))));
    }

    @AfterEach
    void tearDown() {
        CLIENT.set(null);
    }

    @Test
    @DisplayName("一次 mastery 回合逐帧推送，且会话落库两条消息")
    void streamsMasteryTurnFrameByFrame() throws Exception {
        CLIENT.set(new FakeLlmClient(List.of(
                LlmResponse.calls(new ToolCall("mastery_build", Map.of(
                        "modules", List.of(Map.of(
                                "name", "Basics",
                                "knowledge_points", List.of(
                                        Map.of("name", "objective", "type", "memory"))))),
                        "call-1", false)),
                LlmResponse.text("Outline ready."))));

        String sessionId = "ws-s1-" + run;
        List<Map<String, Object>> frames = exchange(Map.of(
                "session_id", sessionId, "path_id", "ws-p-" + run, "mode", "outline",
                "message", "teach me"));

        assertThat(types(frames))
                .containsExactly("tool_call", "tool_result", "text_delta", "turn_end");
        Map<String, Object> detail = service.getSessionDetail(sessionId);
        @SuppressWarnings("unchecked")
        Map<String, Object> session = (Map<String, Object>) detail.get("session");
        assertThat(session).containsEntry("message_count", 2);
    }

    @Test
    @DisplayName("模型错误映射成 error 帧，socket 不被静默关闭")
    void mapsLlmErrorWithoutDroppingTheSocket() throws Exception {
        CLIENT.set(new LlmClient() {
            @Override
            public LlmResponse chat(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools) {
                throw new LlmError("The configured model was not found at this provider.",
                        "model_not_found", false);
            }
        });

        List<Map<String, Object>> frames = exchange(Map.of(
                "session_id", "ws-s2-" + run, "path_id", "ws-p-" + run,
                "mode", "study", "message", "hi"));

        assertThat(types(frames)).containsExactly("error");
        assertThat(data(frames.get(0))).containsEntry("error_code", "model_not_found");
    }

    @Test
    @DisplayName("正文按片推送，多帧到达")
    void forwardsIncrementalTextFrames() throws Exception {
        CLIENT.set(new FakeLlmClient(List.of(LlmResponse.text("hello world")), 3));

        List<Map<String, Object>> frames = exchange(Map.of(
                "session_id", "ws-s3-" + run, "path_id", "ws-p-" + run,
                "mode", "outline", "message", "hi"));

        List<String> deltas = new ArrayList<>();
        for (Map<String, Object> frame : frames) {
            if ("text_delta".equals(frame.get("type"))) {
                deltas.add(String.valueOf(data(frame).get("text")));
            }
        }
        assertThat(String.join("", deltas)).isEqualTo("hello world");
        assertThat(deltas).hasSizeGreaterThanOrEqualTo(2);
    }

    // -- 辅助 -----------------------------------------------------------------

    /** 连一次 {@code /ws}，发一条请求，收完所有帧直到服务端关闭。 */
    private List<Map<String, Object>> exchange(Map<String, Object> payload) throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        CountDownLatch closed = new CountDownLatch(1);
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession ignored, TextMessage message) {
                received.add(message.getPayload());
            }

            @Override
            public void afterConnectionClosed(WebSocketSession ignored, CloseStatus status) {
                closed.countDown();
            }
        }, "ws://localhost:" + port + "/ws").get(10, TimeUnit.SECONDS);

        session.sendMessage(new TextMessage(MAPPER.writeValueAsString(payload)));
        assertThat(closed.await(20, TimeUnit.SECONDS)).isTrue();

        List<Map<String, Object>> frames = new ArrayList<>();
        String raw;
        while ((raw = received.poll()) != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> frame = MAPPER.readValue(raw, Map.class);
            frames.add(frame);
        }
        return frames;
    }

    private static List<String> types(List<Map<String, Object>> frames) {
        List<String> types = new ArrayList<>();
        for (Map<String, Object> frame : frames) {
            types.add(String.valueOf(frame.get("type")));
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> frame) {
        Object data = frame.get("data");
        return data instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
    }

    /** 用脚本化客户端替换生产 {@link TurnRunner}（生产实现要读设置，属 {@code 0022}）。 */
    @TestConfiguration
    static class FakeClientConfig {

        @Bean
        @Primary
        TurnRunner fakeTurnRunner(LearningService service, LearningStore store) {
            return new TurnRunner(service, store,
                    (selection, sessionKey) -> CLIENT.get(), null);
        }
    }
}
