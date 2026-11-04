package com.masterypath.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 题目事务，判分幂等键为 {@code (path_id, interaction_id)}（spec 01 §二 / {@code INV-21/22}）。
 *
 * <p>终态（已判分/已作废）事务 MUST 保留可查。
 */
public class MasteryInteraction {

    private String interactionId;
    private String pathId;
    private PendingQuestion question;
    private InteractionStatus status = InteractionStatus.REGISTERED;
    private String sessionId = "";
    private String turnId = "";
    private String userAnswer = "";
    private Map<String, Object> result = new LinkedHashMap<>();
    private double createdAt = TimeSupport.nowSeconds();
    private double updatedAt = TimeSupport.nowSeconds();

    public MasteryInteraction() {
    }

    public String getInteractionId() {
        return interactionId;
    }

    public void setInteractionId(String interactionId) {
        this.interactionId = interactionId;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public PendingQuestion getQuestion() {
        return question;
    }

    public void setQuestion(PendingQuestion question) {
        this.question = question;
    }

    public InteractionStatus getStatus() {
        return status;
    }

    public void setStatus(InteractionStatus status) {
        this.status = status == null ? InteractionStatus.REGISTERED : status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId == null ? "" : sessionId;
    }

    public String getTurnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId == null ? "" : turnId;
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer == null ? "" : userAnswer;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result == null ? new LinkedHashMap<>() : result;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public double getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(double updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 终态判定（不参与序列化）。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isTerminal() {
        return status.isTerminal();
    }

    /** 未终态判定（不参与序列化）。 */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isOpen() {
        return status.isOpen();
    }
}
