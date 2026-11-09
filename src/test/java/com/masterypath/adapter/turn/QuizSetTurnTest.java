package com.masterypath.adapter.turn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masterypath.adapter.llm.FakeLlmClient;
import com.masterypath.adapter.llm.LlmResponse;
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
import java.util.List;
import java.util.Map;

@Tag("api")
class QuizSetTurnTest {

    private static final String VALID = """
            {"questions":[
              {"question_type":"choice","question":"Pick A",
               "options":[{"label":"A","body":"one"},{"label":"B","body":"two"}],
               "expected_answer":"A","explanation":"because A","difficulty":"easy"},
              {"question_type":"short","question":"2+2?","expected_answer":"4",
               "accepted_answers":["4"],"difficulty":"easy"}
            ]}
            """;

    @TempDir
    Path tempDir;

    private LearningStore store;
    private LearningService service;

    @BeforeEach
    void setUp() {
        store = new LearningStore(tempDir);
        service = new LearningService(store);
        service.createPath("p", "learn caching", "Caching");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    @DisplayName("deep_question 事件顺序正确，题卷落库且公开事件不含答案")
    void generatesQuizSetWithEvents() {
        FakeLlmClient fake = new FakeLlmClient(List.of(LlmResponse.text(VALID)));

        List<TurnEvent> events = run(fake, request());

        assertThat(types(events)).containsExactly(
                "quiz_plan", "quiz_question", "quiz_question", "quiz_set", "turn_end");
        for (TurnEvent event : events) {
            if ("quiz_question".equals(event.type())) {
                assertThat(event.data()).doesNotContainKeys(
                        "expected_answer", "explanation", "accepted_answers", "required_keywords");
            }
        }
        assertThat(store.listQuizSets("p")).hasSize(1);
        String setId = store.listQuizSets("p").get(0).getSetId();
        assertThat(store.listQuizItems(setId)).hasSize(2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>)
                service.getSessionDetail("s1").get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) messages.get(1).get("data");
        assertThat(data).containsKey("quiz_set");
    }

    @Test
    @DisplayName("非法 JSON 修复一次后仍失败：只发 error + turn_end，零落库")
    void invalidGenerationFailsClosed() {
        FakeLlmClient fake = new FakeLlmClient(List.of(
                LlmResponse.text("not json"),
                LlmResponse.text("still not json")));

        List<TurnEvent> events = run(fake, request());

        assertThat(types(events)).containsExactly("error", "turn_end");
        assertThat(store.listQuizSets("p")).isEmpty();
    }

    @Test
    @DisplayName("首次非法、第二次合法时修复成功")
    void repairsOnce() {
        FakeLlmClient fake = new FakeLlmClient(List.of(
                LlmResponse.text("not json"),
                LlmResponse.text(VALID)));

        List<TurnEvent> events = run(fake, request());

        assertThat(types(events)).contains("quiz_set");
        assertThat(store.listQuizSets("p")).hasSize(1);
    }

    @Test
    @DisplayName("未知 capability 明确失败")
    void unknownCapabilityThrows() {
        TurnRunner runner = new TurnRunner(service, store,
                (selection, sessionKey) -> new FakeLlmClient(List.of()), null);
        TurnRequest request = request();
        request.setCapability("no_such_capability");

        assertThatThrownBy(() -> runner.run(request))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private List<TurnEvent> run(FakeLlmClient fake, TurnRequest request) {
        TurnRunner runner = new TurnRunner(service, store,
                (selection, sessionKey) -> fake, null);
        List<TurnEvent> events = new ArrayList<>();
        runner.run(request).forEachRemaining(events::add);
        return events;
    }

    private static TurnRequest request() {
        TurnRequest request = new TurnRequest();
        request.setCapability(TurnRequest.CAPABILITY_DEEP_QUESTION);
        request.setSessionId("s1");
        request.setPathId("p");
        request.setMode("study");
        request.setMessage("caching");
        request.setNumQuestions(2);
        request.setDifficulty("easy");
        request.setQuestionTypes(List.of("choice", "short"));
        return request;
    }

    private static List<String> types(List<TurnEvent> events) {
        return events.stream().map(TurnEvent::type).toList();
    }
}
