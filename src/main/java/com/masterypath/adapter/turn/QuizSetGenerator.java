package com.masterypath.adapter.turn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masterypath.adapter.llm.LlmClient;
import com.masterypath.adapter.llm.LlmResponse;
import com.masterypath.domain.model.JsonSupport;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuizItem;
import com.masterypath.domain.model.TimeSupport;
import com.masterypath.domain.quiz.QuizSpec;
import com.masterypath.domain.quizset.QuizSetSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成一套练习题（{@code 0033}）。
 *
 * <p>一次结构化生成调用；失败时带错误信息修复一次；仍失败抛
 * {@link QuizGenerationException}，由 {@code QuizSetTurn} 转成 {@code error} 事件，零落库。
 */
final class QuizSetGenerator {

    private static final String SYSTEM = """
            You generate practice quizzes for a learner.
            Return ONLY one valid JSON object, no markdown fences, no prose outside JSON.
            Shape:
            {"questions":[
              {"question_type":"choice|short|open",
               "question":"...",
               "options":[{"label":"A","body":"..."}],
               "expected_answer":"...",
               "accepted_answers":["..."],
               "required_keywords":["..."],
               "optional_keywords":["..."],
               "explanation":"...",
               "difficulty":"easy|medium|hard",
               "knowledge_point_id":""}
            ]}
            For choice: options have unique labels A, B, C...; expected_answer is the correct label.
            For short: expected_answer is the canonical answer; accepted_answers should list equivalent answers.
            For open: expected_answer is a reference answer; required_keywords must be non-empty.
            """;

    private final LlmClient client;
    private final ObjectMapper mapper = JsonSupport.objectMapper();

    QuizSetGenerator(LlmClient client) {
        this.client = client;
    }

    /**
     * 生成并校验题卷的题目。
     *
     * @return 恰好 {@code spec.count()} 道通过校验的题目
     * @throws QuizGenerationException 解析/校验失败且修复一次后仍失败
     */
    List<QuizItem> generate(QuizSetSpec spec, String setId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM));
        messages.add(Map.of("role", "user", "content", userPrompt(spec)));
        String raw = ask(messages);
        try {
            return parse(raw, spec, setId);
        } catch (RuntimeException first) {
            messages.add(Map.of("role", "assistant", "content", raw));
            messages.add(Map.of("role", "user", "content",
                    "That JSON was invalid: " + first.getMessage()
                            + ". Return the corrected JSON only, same shape, exactly "
                            + spec.count() + " questions."));
            String repaired = ask(messages);
            try {
                return parse(repaired, spec, setId);
            } catch (RuntimeException second) {
                throw new QuizGenerationException(
                        "quiz generation failed after one repair: " + second.getMessage());
            }
        }
    }

    private String userPrompt(QuizSetSpec spec) {
        return "Topic: " + spec.topic()
                + "\nNumber of questions: " + spec.count()
                + "\nAllowed question types: " + String.join(", ", spec.questionTypes())
                + "\nDifficulty: " + spec.difficulty()
                + "\nReturn exactly " + spec.count() + " questions.";
    }

    private String ask(List<Map<String, Object>> messages) {
        LlmResponse response = client.chat(messages, List.of());
        String text = response == null ? "" : response.text().strip();
        if (text.isEmpty()) {
            throw new QuizGenerationException("model returned an empty quiz");
        }
        return text;
    }

    private List<QuizItem> parse(String raw, QuizSetSpec spec, String setId) {
        JsonNode root = readJson(raw);
        JsonNode questions = root.isArray() ? root : root.get("questions");
        if (questions == null || !questions.isArray()) {
            throw new QuizGenerationException("JSON must contain a questions array");
        }
        List<QuizItem> items = new ArrayList<>();
        for (int index = 0; index < questions.size() && items.size() < spec.count(); index++) {
            items.add(toItem(questions.get(index), spec, setId, index));
        }
        if (items.size() < spec.count()) {
            throw new QuizGenerationException(
                    "expected " + spec.count() + " questions but got " + items.size());
        }
        return items;
    }

    private JsonNode readJson(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');
        int start = objectStart < 0 ? arrayStart
                : arrayStart < 0 ? objectStart : Math.min(objectStart, arrayStart);
        int objectEnd = text.lastIndexOf('}');
        int arrayEnd = text.lastIndexOf(']');
        int end = Math.max(objectEnd, arrayEnd);
        if (start < 0 || end <= start) {
            throw new QuizGenerationException("no JSON object/array found");
        }
        try {
            return mapper.readTree(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new QuizGenerationException("invalid JSON: " + e.getMessage());
        }
    }

    private QuizItem toItem(JsonNode node, QuizSetSpec spec, String setId, int index) {
        if (node == null || !node.isObject()) {
            throw new QuizGenerationException("question " + (index + 1) + " is not an object");
        }
        String questionType = text(node, "question_type");
        if (questionType.isEmpty()) {
            questionType = spec.questionTypes().get(index % spec.questionTypes().size());
        }
        if (!spec.questionTypes().contains(questionType)) {
            throw new QuizGenerationException("question " + (index + 1)
                    + " uses disallowed question_type: " + questionType);
        }
        String question = text(node, "question");
        if (question.isEmpty()) {
            question = text(node, "prompt");
        }
        if (question.isEmpty()) {
            throw new QuizGenerationException("question " + (index + 1) + " is empty");
        }
        String expected = text(node, "expected_answer");
        if (expected.isEmpty()) {
            throw new QuizGenerationException("question " + (index + 1) + " has no expected_answer");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("question_type", questionType);
        params.put("expected_answer", expected);
        params.put("options", options(node.get("options")));
        List<String> accepted = strings(node.get("accepted_answers"));
        List<String> required = strings(node.get("required_keywords"));
        List<String> optional = strings(node.get("optional_keywords"));
        params.put("accepted_answers", accepted);
        params.put("required_keywords", required);
        params.put("optional_keywords", optional);

        QuizSpec.Normalized normalized = QuizSpec.normalize(params);
        if ("short".equals(normalized.questionType()) && accepted.isEmpty()
                && !QuizSpec.isSelfGradingShort(normalized.expectedAnswer())) {
            throw new QuizGenerationException(
                    "question " + (index + 1) + " is a natural-language short question "
                            + "without accepted_answers");
        }
        List<String> openRequired = QuizSpec.openRequiredKeywords(
                normalized.questionType(), normalized.requiredKeywords(),
                normalized.expectedAnswer());
        List<String> openOptional = "open".equals(normalized.questionType())
                ? normalized.optionalKeywords() : List.of();

        PendingQuestion pending = QuizSpec.toPending(
                new QuizSpec.Normalized(normalized.questionType(), normalized.options(),
                        normalized.expectedAnswer(), openRequired, openOptional),
                UUID.randomUUID().toString().replace("-", ""),
                text(node, "knowledge_point_id"),
                "",
                question,
                text(node, "explanation"),
                text(node, "difficulty").isEmpty() ? spec.difficulty() : text(node, "difficulty"),
                accepted);

        QuizItem item = new QuizItem();
        item.setItemId(UUID.randomUUID().toString().replace("-", ""));
        item.setSetId(setId);
        item.setSeq(index + 1);
        item.setQuestion(pending);
        item.setUpdatedAt(TimeSupport.nowSeconds());
        return item;
    }

    private List<Map<String, Object>> options(JsonNode node) {
        List<Map<String, Object>> options = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return options;
        }
        for (JsonNode entry : node) {
            Map<String, Object> option = new LinkedHashMap<>();
            if (entry.isObject()) {
                option.put("label", text(entry, "label"));
                option.put("body", text(entry, "body"));
            } else {
                option.put("label", "");
                option.put("body", entry.asText(""));
            }
            options.add(option);
        }
        return options;
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode entry : node) {
                String value = entry.asText("").strip();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").strip();
    }

    /** 生成失败（解析或校验无法修复）。 */
    static final class QuizGenerationException extends RuntimeException {
        QuizGenerationException(String message) {
            super(message);
        }
    }
}
