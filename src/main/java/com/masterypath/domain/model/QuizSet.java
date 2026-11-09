package com.masterypath.domain.model;

/**
 * 一套生成型练习题卷的元数据（{@code 0033}）。
 *
 * <p>与掌握题不同：题卷是练习，不进入 {@code interactions} / 掌握度 / 待答题槽位。
 * 题目本身在 {@link QuizItem} 中，答案键只存服务端。
 */
public class QuizSet {

    public static final String STATUS_OPEN = "open";
    public static final String STATUS_SUBMITTED = "submitted";

    private String setId = "";
    private String pathId = "";
    private String sessionId = "";
    private String turnId = "";
    private String title = "";
    private String topic = "";
    private String status = STATUS_OPEN;
    private int questionCount;
    private double score;
    private double createdAt = TimeSupport.nowSeconds();
    private double submittedAt;

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId == null ? "" : setId;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId == null ? "" : pathId;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic == null ? "" : topic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isEmpty() ? STATUS_OPEN : status;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = Math.max(0, questionCount);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public double getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(double submittedAt) {
        this.submittedAt = submittedAt;
    }
}
