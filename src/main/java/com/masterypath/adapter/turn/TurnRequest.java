package com.masterypath.adapter.turn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一次回合的请求（参考实现 {@code TurnRequest}）。
 *
 * <p><b>可变</b>：{@code TurnRunner} 会把解析后的 {@code pathId} 写回
 * （参考实现 `request.path_id = self._resolve_path_id(request)`），
 * 持久化时读的就是这个值。因此不能用 record。
 */
public class TurnRequest {

    /** 能力名：掌握式学习。 */
    public static final String CAPABILITY_MASTERY = "mastery_path";
    /** 能力名：普通聊天（属 {@code 0020}）。 */
    public static final String CAPABILITY_CHAT = "chat";
    /** 能力名：生成型练习题卷（{@code 0033}）。 */
    public static final String CAPABILITY_DEEP_QUESTION = "deep_question";

    private String capability = CAPABILITY_CHAT;
    private String sessionId = "";
    private String message = "";
    private String pathId = "";
    private String mode = "";
    private String turnId = "";
    private Object llmSelection;
    private List<Map<String, Object>> attachments = new ArrayList<>();
    private int numQuestions = 3;
    private String difficulty = "";
    private List<String> questionTypes = new ArrayList<>();
    private Map<String, Object> masteryAnswer = new java.util.LinkedHashMap<>();
    private Map<String, Object> masterySkip = new java.util.LinkedHashMap<>();

    public String capability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability == null ? CAPABILITY_CHAT : capability;
    }

    public String sessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public String message() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public String pathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId == null ? "" : pathId;
    }

    public String mode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode == null ? "" : mode;
    }

    public String turnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId == null ? "" : turnId;
    }

    public Object llmSelection() {
        return llmSelection;
    }

    public void setLlmSelection(Object llmSelection) {
        this.llmSelection = llmSelection;
    }

    public List<Map<String, Object>> attachments() {
        return attachments;
    }

    public void setAttachments(List<Map<String, Object>> attachments) {
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
    }

    public int numQuestions() {
        return numQuestions;
    }

    public void setNumQuestions(int numQuestions) {
        this.numQuestions = numQuestions;
    }

    public String difficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty == null ? "" : difficulty;
    }

    public List<String> questionTypes() {
        return questionTypes;
    }

    public void setQuestionTypes(List<String> questionTypes) {
        this.questionTypes = questionTypes == null ? new ArrayList<>() : new ArrayList<>(questionTypes);
    }

    /** 作答卡片消息：{@code {question_id, text}}；空 map 表示本轮不是作答。 */
    public Map<String, Object> masteryAnswer() {
        return masteryAnswer;
    }

    public void setMasteryAnswer(Map<String, Object> masteryAnswer) {
        this.masteryAnswer = masteryAnswer == null
                ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(masteryAnswer);
    }

    /** 跳过卡片消息：{@code {question_id}}；空 map 表示本轮不是跳过。 */
    public Map<String, Object> masterySkip() {
        return masterySkip;
    }

    public void setMasterySkip(Map<String, Object> masterySkip) {
        this.masterySkip = masterySkip == null
                ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(masterySkip);
    }
}
