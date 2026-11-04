package com.masterypath.domain.model;

/**
 * 一次作答记录（spec 01 §二）。
 *
 * <p>{@code voided=true} 的记录 MUST NOT 计入任何账（{@code INV-14}）；
 * 修复题目时以标记代替物理删除（ADR-020）。
 */
public class QuizAttempt {

    private String questionId;
    private String knowledgePointId;
    private String moduleId = "";
    private boolean correct;
    private Object userAnswer;
    private ErrorType errorType;
    private boolean independent = true;
    private double masteryEstimate;
    private boolean voided;
    private String voidReason = "";
    private double timestamp = TimeSupport.nowSeconds();

    public QuizAttempt() {
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

    /** 显式命名：Python 侧字段是 {@code is_correct}，而 {@code isCorrect()} 会被推断为 {@code correct}。 */
    @com.fasterxml.jackson.annotation.JsonProperty("is_correct")
    public boolean isCorrect() {
        return correct;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("is_correct")
    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public Object getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(Object userAnswer) {
        this.userAnswer = userAnswer;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("independent")
    public boolean isIndependent() {
        return independent;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("independent")
    public void setIndependent(boolean independent) {
        this.independent = independent;
    }

    public double getMasteryEstimate() {
        return masteryEstimate;
    }

    public void setMasteryEstimate(double masteryEstimate) {
        this.masteryEstimate = masteryEstimate;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("voided")
    public boolean isVoided() {
        return voided;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("voided")
    public void setVoided(boolean voided) {
        this.voided = voided;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason == null ? "" : voidReason;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }
}
