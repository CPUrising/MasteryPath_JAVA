package com.masterypath.domain.model;

/**
 * 学习证据，<b>只追加</b>（spec 01 §二 / {@code INV-12}）。
 *
 * <p>修复题目不物理删除证据，而是标记 {@code voided}；门槛与必需证据校验会忽略它（ADR-020）。
 */
public class LearningEvidence {

    private String evidenceId = "";
    private String questionId = "";
    private String knowledgePointId;
    private AssessmentType assessmentType = AssessmentType.QUIZ;
    private AssessmentResult result = AssessmentResult.INCORRECT;
    private Double quality;
    private int hintsUsed;
    private int attemptCount = 1;
    private String sessionId = "";
    private String turnId = "";
    private String source = "mastery_path";
    private boolean voided;
    private String voidReason = "";
    private double timestamp = TimeSupport.nowSeconds();

    public LearningEvidence() {
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId == null ? "" : evidenceId;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId == null ? "" : questionId;
    }

    public String getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(String knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public AssessmentType getAssessmentType() {
        return assessmentType;
    }

    public void setAssessmentType(AssessmentType assessmentType) {
        this.assessmentType = assessmentType == null ? AssessmentType.QUIZ : assessmentType;
    }

    public AssessmentResult getResult() {
        return result;
    }

    public void setResult(AssessmentResult result) {
        this.result = result == null ? AssessmentResult.INCORRECT : result;
    }

    public Double getQuality() {
        return quality;
    }

    public void setQuality(Double quality) {
        this.quality = quality;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public void setHintsUsed(int hintsUsed) {
        this.hintsUsed = hintsUsed;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source == null ? "mastery_path" : source;
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
