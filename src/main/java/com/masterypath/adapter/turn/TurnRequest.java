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

    private String capability = CAPABILITY_CHAT;
    private String sessionId = "";
    private String message = "";
    private String pathId = "";
    private String mode = "";
    private String turnId = "";
    private Object llmSelection;
    private List<Map<String, Object>> attachments = new ArrayList<>();

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
}
