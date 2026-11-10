package com.masterypath.adapter.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.adapter.llm.ModelResolution;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.service.LearningService;
import com.masterypath.storage.SettingsStore;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实模型全链路：经真实 Tomcat、真实 WebSocket/REST、真实 SQLite 走完一条掌握之路。
 *
 * <p><b>只在显式开启时运行</b>（{@code MASTERY_E2E=1}，由 {@code scripts/e2e-mastery.ps1} 设置），
 * 因此默认回归仍然零网络、零费用。模型来自 {@code data/settings.json}，
 * 测试把该文件复制进独立数据目录，不打印任何 Key。
 *
 * <p>与真实使用的唯一区别是不截图：驱动与浏览器走同一套协议；交谈内容通过 WS 帧、
 * 会话 API 与落盘 transcript 读取。收到题目后，测试从本地库读取期望答案再以学习者口吻提交
 * ——这是夹具手段，产品接口不暴露答案键。
 */
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "MASTERY_E2E", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MasteryJourneyE2ETest {

    private static final ObjectMapper MAPPER = JsonSupport.objectMapper();
    private static final String STAMP =
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    private static final Path WORKSPACE = prepareWorkspace();

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) {
        registry.add("masterypath.data-dir", WORKSPACE::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private LearningService service;

    @Autowired
    private SettingsStore settingsStore;

    @Test
    @Timeout(600)
    @DisplayName("真实模型走完整条掌握之路：出题 → 判分 → 清门槛 → 下一目标 → 完成")
    void walksAWholeMasteryPath() throws Exception {
        assumeModelConfigured();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String pathId = "e2e-p-" + suffix;
        String sessionId = "e2e-s-" + suffix;
        List<Map<String, Object>> steps = new ArrayList<>();
        List<Map<String, Object>> grades = new ArrayList<>();

        rest.postForEntity("/learning/paths", Map.of(
                "path_id", pathId,
                "name", "并发基础",
                "goal", "掌握 Java 并发基础"), Map.class);

        buildOutlineThroughConversation(pathId, sessionId, steps);
        setMode(pathId, sessionId, "study");

        int maxTurns = Integer.parseInt(env("MASTERY_E2E_MAX_TURNS", "18"));
        boolean wrongAnswerUsed = false;
        int assessAttempts = 0;
        for (int turn = 1; turn <= maxTurns; turn++) {
            Map<String, Object> state = state(pathId);
            Map<String, Object> next = map(state.get("next"));
            String action = str(next.get("action"));
            if ("complete".equals(action)) {
                break;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("session_id", sessionId);
            payload.put("path_id", pathId);
            payload.put("mode", "study");

            MasteryInteraction open = service.store().getOpenInteraction(pathId);
            String message;
            if (open != null && open.getQuestion() != null) {
                String answer = wrongAnswerUsed ? expectedAnswer(open.getQuestion()) : "这题我还没想清楚";
                wrongAnswerUsed = true;
                message = answer;
                payload.put("mastery_answer", Map.of(
                        "question_id", open.getInteractionId(), "text", answer));
            } else if ("assess".equals(action)) {
                assessAttempts++;
                message = "我的理解是：" + str(next.get("knowledge_point_name"))
                        + " 的核心在于它解决的问题和适用边界。";
                if (assessAttempts > 3) {
                    rest.postForEntity("/learning/paths/" + pathId + "/assess", Map.of(
                            "knowledge_point_id", str(next.get("knowledge_point_id")),
                            "passed", true,
                            "session_id", sessionId), Map.class);
                }
            } else if ("review".equals(action)) {
                message = "我们复习一下。";
            } else {
                message = "请出题考我。";
            }
            payload.put("message", message);

            List<Map<String, Object>> frames = exchange(payload);
            collectGrades(frames, grades);
            steps.add(step(turn, message, action, frames));
        }

        Map<String, Object> finalState = state(pathId);
        Map<String, Object> sessionDetail = rest.getForObject(
                "/learning/sessions/" + sessionId, Map.class);
        Path transcript = writeArtifacts(steps, sessionDetail, grades);

        Map<String, Object> next = map(finalState.get("next"));
        assertThat(str(next.get("action")))
                .as("掌握之路未在轮次预算内完成；交谈记录见 %s", transcript)
                .isEqualTo("complete");
        assertThat(grades).hasSizeGreaterThanOrEqualTo(4);
        for (Map<String, Object> grade : grades) {
            assertThat(str(grade.get("result")))
                    .as("掌握路径不产生 ungraded：%s", grade)
                    .isIn("correct", "incorrect");
        }
        assertThat(grades).anySatisfy(grade -> {
            assertThat(((Number) grade.get("mastery_after")).doubleValue()).isEqualTo(1.0);
            assertThat(grade.get("gate_cleared")).isEqualTo(true);
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> stages = (Map<String, Object>) finalState.get("learning_stages");
        assertThat(stages).isNotEmpty();
        assertThat(stages.values()).contains("independently_demonstrated");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviews = (List<Map<String, Object>>) finalState.get("reviews");
        assertThat(reviews).isNotEmpty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) sessionDetail.get("messages");
        assertThat(messages).isNotEmpty();
        assertThat(messages).anySatisfy(row ->
                assertThat(String.valueOf(row.get("role"))).isEqualTo("user"));
    }

    // -- 大纲 -----------------------------------------------------------------

    private void buildOutlineThroughConversation(String pathId, String sessionId,
                                                 List<Map<String, Object>> steps)
            throws Exception {
        setMode(pathId, sessionId, "outline");
        String[] prompts = {
                "我是有 Java 基础的后端工程师，每晚一小时，想先理解原理再练习；"
                        + "请直接给出 1 个模块、2 个知识点（一个记忆型、一个概念型）的大纲，然后开始学。",
                "不用再问我了，按合理估计直接建大纲。",
                "请直接调用工具把大纲建好。"
        };
        for (int index = 0; index < prompts.length; index++) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("session_id", sessionId);
            payload.put("path_id", pathId);
            payload.put("mode", "outline");
            payload.put("message", prompts[index]);
            List<Map<String, Object>> frames = exchange(payload);
            steps.add(step(index + 1, prompts[index], "outline", frames));
            if (!map(state(pathId).get("map")).isEmpty()) {
                return;
            }
        }
        // 真实模型可能长时间停留在追问：用固定大纲兜底，并在 transcript 里留痕。
        service.build(pathId, Map.of("modules", List.of(Map.of(
                "name", "并发基础",
                "knowledge_points", List.of(
                        Map.of("name", "线程与进程的区别", "type", "memory"),
                        Map.of("name", "synchronized 的可见性与互斥", "type", "concept"))))), false);
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("turn", 0);
        fallback.put("fallback", "build");
        fallback.put("reason", "模型 3 轮内未建大纲，使用固定大纲继续验证");
        steps.add(fallback);
    }

    // -- 辅助 -----------------------------------------------------------------

    private void assumeModelConfigured() {
        try {
            ModelResolution.resolveLlmConfig(settingsStore, null);
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false,
                    "no real model configured: " + e.getMessage());
        }
    }

    private void setMode(String pathId, String sessionId, String mode) {
        rest.exchange("/learning/paths/{pathId}/sessions/{sessionId}/mode", HttpMethod.PUT,
                new HttpEntity<>(Map.of("mode", mode)), Map.class, pathId, sessionId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> state(String pathId) {
        return rest.getForObject("/learning/paths/" + pathId, Map.class);
    }

    private static String expectedAnswer(PendingQuestion question) {
        String expected = question.getExpectedAnswer() == null
                ? "" : question.getExpectedAnswer().strip();
        if (!expected.isEmpty()) {
            return expected;
        }
        if (question.getQuestionType() == PendingQuestion.QuestionType.CHOICE
                && !question.getOptions().isEmpty()) {
            return question.getOptions().get(0).getLabel();
        }
        return "不知道";
    }

    private static void collectGrades(List<Map<String, Object>> frames,
                                      List<Map<String, Object>> grades) {
        for (Map<String, Object> frame : frames) {
            if (!"tool_result".equals(frame.get("type"))) {
                continue;
            }
            Map<String, Object> grade = map(map(map(frame.get("data")).get("metadata"))
                    .get("mastery_grade"));
            if (!grade.isEmpty()) {
                grades.add(grade);
            }
        }
    }

    private static Map<String, Object> step(int turn, String message, String action,
                                            List<Map<String, Object>> frames) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("turn", turn);
        step.put("learner_message", message);
        step.put("next_action_before", action);
        step.put("frames", frames);
        return step;
    }

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
        assertThat(closed.await(120, TimeUnit.SECONDS)).isTrue();

        List<Map<String, Object>> frames = new ArrayList<>();
        String raw;
        while ((raw = received.poll()) != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> frame = MAPPER.readValue(raw, Map.class);
            frames.add(frame);
        }
        return frames;
    }

    private Path writeArtifacts(List<Map<String, Object>> steps,
                                Map<String, Object> sessionDetail,
                                List<Map<String, Object>> grades) throws IOException {
        Path dir = Path.of("target", "e2e-artifacts");
        Files.createDirectories(dir);
        Path transcript = dir.resolve("mastery-journey-" + STAMP + ".jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(transcript)) {
            for (Map<String, Object> step : steps) {
                writer.write(MAPPER.writeValueAsString(step));
                writer.newLine();
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("type", "summary");
            summary.put("grade_count", grades.size());
            summary.put("grades", grades);
            writer.write(MAPPER.writeValueAsString(summary));
            writer.newLine();
        }
        Files.writeString(dir.resolve("mastery-journey-" + STAMP + ".session.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sessionDetail));
        if ("1".equals(env("MASTERY_E2E_KEEP", "0"))) {
            copyWorkspace(Path.of("artifacts", "e2e", STAMP));
        }
        return transcript;
    }

    private static void copyWorkspace(Path target) throws IOException {
        Files.createDirectories(target);
        try (var paths = Files.walk(WORKSPACE)) {
            paths.forEach(source -> {
                try {
                    Path destination = target.resolve(WORKSPACE.relativize(source).toString());
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("cannot keep e2e workspace", e);
                }
            });
        }
    }

    private static Path prepareWorkspace() {
        try {
            Path base = Path.of("target", "test-workspace");
            Files.createDirectories(base);
            Path dir = Files.createDirectory(base.resolve("e2e-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
            Path source = Path.of(env("MASTERY_E2E_DATA_DIR", "data")).resolve("settings.json");
            if (Files.isRegularFile(source)) {
                Files.copy(source, dir.resolve("settings.json"));
            }
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("cannot prepare e2e workspace", e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
