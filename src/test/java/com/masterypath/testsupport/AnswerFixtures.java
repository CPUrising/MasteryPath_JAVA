package com.masterypath.testsupport;

import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.PendingOption;
import com.masterypath.domain.model.PendingQuestion;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 13 条脱敏改写的真实风格作答（{@code fixtures/real-answers-13.json}）。
 *
 * <p>{@code expected} 是 {@code Grading.grade(...).result()} 的期望。掌握路径与练习题卷
 * 共用同一个判分器（{@code 0043}），因此只有一列期望。
 *
 * <p>用 Map 手工映射而不是直接反序列化 record：全局 ObjectMapper 配置了 snake_case，
 * 手工映射可以避免未来命名策略调整时静默读空。
 */
public final class AnswerFixtures {

    private static final String RESOURCE = "/fixtures/real-answers-13.json";

    private AnswerFixtures() {
    }

    /** 一条 fixture。 */
    public record Case(
            String id,
            String questionType,
            String prompt,
            List<PendingOption> options,
            String expectedAnswer,
            List<String> requiredKeywords,
            List<String> optionalKeywords,
            String learnerAnswer,
            String expected,
            String note) {
    }

    /** 读取全部 13 条；资源缺失或格式错误直接抛错（测试应当响亮地失败）。 */
    public static List<Case> load() {
        try (InputStream stream = AnswerFixtures.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing test fixture: " + RESOURCE);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = JsonSupport.objectMapper().readValue(stream, Map.class);
            List<Case> cases = new ArrayList<>();
            for (Object item : list(root.get("cases"))) {
                if (item instanceof Map<?, ?> raw) {
                    cases.add(toCase(raw));
                }
            }
            return cases;
        } catch (Exception e) {
            throw new IllegalStateException("cannot read test fixture: " + RESOURCE, e);
        }
    }

    /** 组装成服务端完整题目（含隐藏答案与关键词）。 */
    public static PendingQuestion toQuestion(Case item) {
        PendingQuestion pending = new PendingQuestion();
        pending.setQuestionId(item.id());
        pending.setKnowledgePointId("k");
        pending.setQuestionType(PendingQuestion.QuestionType.fromValue(item.questionType()));
        pending.setPrompt(item.prompt());
        pending.setExpectedAnswer(item.expectedAnswer());
        pending.setOptions(new ArrayList<>(item.options()));
        pending.setRequiredKeywords(new ArrayList<>(item.requiredKeywords()));
        pending.setOptionalKeywords(new ArrayList<>(item.optionalKeywords()));
        return pending;
    }

    private static Case toCase(Map<?, ?> raw) {
        List<PendingOption> options = new ArrayList<>();
        for (Object option : list(raw.get("options"))) {
            if (option instanceof Map<?, ?> value) {
                options.add(new PendingOption(text(value.get("label")), text(value.get("body"))));
            }
        }
        return new Case(
                text(raw.get("id")),
                text(raw.get("question_type")),
                text(raw.get("prompt")),
                options,
                text(raw.get("expected_answer")),
                strings(raw.get("required_keywords")),
                strings(raw.get("optional_keywords")),
                text(raw.get("learner_answer")),
                text(raw.get("expected")),
                text(raw.get("note")));
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        for (Object item : list(value)) {
            result.add(text(item));
        }
        return result;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
