package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 待答题目（spec 01 §二）。
 *
 * <p>{@code expectedAnswer} 与 {@code explanation} <b>只存服务端</b>，MUST NOT 进入学习者可见投影
 * （{@code INV-07}）。投影由 {@code 0006} 的 {@code QuestionCardProjector} 负责，本类只保证字段存在。
 *
 * <p>开放题的关键词在<b>注册时固定</b>（{@code INV-35}），因此判分不调用 LLM。
 */
public class PendingQuestion {

    private String questionId;
    private String knowledgePointId;
    private String moduleId = "";
    private String prompt = "";
    private QuestionType questionType = QuestionType.SHORT;
    private String expectedAnswer = "";
    private List<PendingOption> options = new ArrayList<>();
    private String explanation = "";
    private String difficulty = "";
    private List<String> acceptedAnswers = new ArrayList<>();
    private List<String> requiredKeywords = new ArrayList<>();
    private List<String> optionalKeywords = new ArrayList<>();
    private double createdAt = TimeSupport.nowSeconds();

    public PendingQuestion() {
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public String getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(String knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId == null ? "" : moduleId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt == null ? "" : prompt;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType == null ? QuestionType.SHORT : questionType;
    }

    public String getExpectedAnswer() {
        return expectedAnswer;
    }

    public void setExpectedAnswer(String expectedAnswer) {
        this.expectedAnswer = expectedAnswer == null ? "" : expectedAnswer;
    }

    public List<PendingOption> getOptions() {
        return options;
    }

    /**
     * 选项 setter：同时接受新式对象与旧式字符串（{@code TC-A5}）。
     *
     * <p>旧数据形如 {@code ["A: x", "B: y"]}（参考实现拆分选项之前写入的形态）。
     * 这里保留原始元素类型判断，交由 {@link OptionParser} 按与 {@code pending.py} 相同的规则解析。
     */
    @SuppressWarnings("unchecked")
    public void setOptions(List<?> rawOptions) {
        List<PendingOption> parsed = new ArrayList<>();
        if (rawOptions == null) {
            this.options = parsed;
            return;
        }
        boolean allStrings = !rawOptions.isEmpty()
                && rawOptions.stream().allMatch(entry -> entry instanceof String);
        if (allStrings) {
            Map<String, String> labelToBody = OptionParser.parse((List<String>) rawOptions);
            labelToBody.forEach((label, body) -> parsed.add(new PendingOption(label, body)));
        } else {
            for (Object entry : rawOptions) {
                if (entry instanceof PendingOption option) {
                    parsed.add(option);
                } else if (entry instanceof Map<?, ?> map) {
                    Object label = map.get("label");
                    Object body = map.get("body");
                    parsed.add(new PendingOption(
                            label == null ? "" : String.valueOf(label),
                            body == null ? "" : String.valueOf(body)));
                }
            }
        }
        this.options = parsed;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation == null ? "" : explanation;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty == null ? "" : difficulty;
    }

    public List<String> getAcceptedAnswers() {
        return acceptedAnswers;
    }

    public void setAcceptedAnswers(List<String> acceptedAnswers) {
        this.acceptedAnswers = acceptedAnswers == null ? new ArrayList<>() : acceptedAnswers;
    }

    public List<String> getRequiredKeywords() {
        return requiredKeywords;
    }

    public void setRequiredKeywords(List<String> requiredKeywords) {
        this.requiredKeywords = requiredKeywords == null ? new ArrayList<>() : requiredKeywords;
    }

    public List<String> getOptionalKeywords() {
        return optionalKeywords;
    }

    public void setOptionalKeywords(List<String> optionalKeywords) {
        this.optionalKeywords = optionalKeywords == null ? new ArrayList<>() : optionalKeywords;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    /** {@code {label: body}}，等价于参考实现的 {@code choice_map}。 */
    public Map<String, String> choiceMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (PendingOption option : options) {
            map.put(option.getLabel(), option.getBody());
        }
        return map;
    }

    /** 题型（spec 01 §二 / {@code 06} §五）。 */
    public enum QuestionType {
        CHOICE("choice"),
        SHORT("short"),
        OPEN("open");

        private final String value;

        QuestionType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        @com.fasterxml.jackson.annotation.JsonCreator
        public static QuestionType fromValue(String raw) {
            for (QuestionType type : values()) {
                if (type.value.equalsIgnoreCase(raw)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("unknown question type: " + raw);
        }

        @com.fasterxml.jackson.annotation.JsonValue
        public String toJson() {
            return value;
        }
    }

    /** 旧式选项字符串解析（与 {@code masterypath/learning/pending.py} 逐条对齐）。 */
    static final class OptionParser {

        /** 前导标签：{@code "A: body"} / {@code "A. body"} / {@code "A) body"} / {@code "A、body"}。 */
        private static final Pattern LABEL = Pattern.compile("^\\s*([A-Za-z])\\s*[:.)、]\\s*(.+)$");

        private OptionParser() {
        }

        /**
         * 解析选项字符串为 {@code {label: body}}。
         *
         * <p>谨慎判断何为标签：{@code "x - 1 = 0"} 不是选项 X（字母后没有冒号/点/括号/顿号）。
         * 所有字符串都带标签时采用其标签，否则按位置编号。
         */
        static Map<String, String> parse(List<String> values) {
            Map<String, String> result = new LinkedHashMap<>();
            List<Matcher> matches = new ArrayList<>();
            boolean allMatched = true;
            for (String value : values) {
                Matcher matcher = LABEL.matcher(value == null ? "" : value);
                matches.add(matcher);
                if (!matcher.matches()) {
                    allMatched = false;
                }
            }
            if (allMatched) {
                for (Matcher matcher : matches) {
                    result.put(matcher.group(1).toUpperCase(), matcher.group(2).trim());
                }
                return result;
            }
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                result.put(positionalLabel(i), value == null ? "" : value.trim());
            }
            return result;
        }

        /** 位置标签：0→A，1→B，…（等价于参考实现的 {@code positional_label}）。 */
        static String positionalLabel(int index) {
            return String.valueOf((char) ('A' + index));
        }
    }
}
