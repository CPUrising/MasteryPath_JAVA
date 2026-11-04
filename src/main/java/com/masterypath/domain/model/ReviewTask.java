package com.masterypath.domain.model;

/** 复习队列中的一项（spec 01 §二）。 */
public class ReviewTask {

    private String id;
    private String knowledgePointId;
    private KnowledgeType knowledgeType;
    private double dueAt;
    private int priority;
    private RepetitionState state;
    private double forgettingRisk;
    private String reason = "";
    private String evidenceSource = "";
    private String evidenceId = "";

    public ReviewTask() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(String knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public KnowledgeType getKnowledgeType() {
        return knowledgeType;
    }

    public void setKnowledgeType(KnowledgeType knowledgeType) {
        this.knowledgeType = knowledgeType;
    }

    public double getDueAt() {
        return dueAt;
    }

    public void setDueAt(double dueAt) {
        this.dueAt = dueAt;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public RepetitionState getState() {
        return state;
    }

    public void setState(RepetitionState state) {
        this.state = state;
    }

    public double getForgettingRisk() {
        return forgettingRisk;
    }

    public void setForgettingRisk(double forgettingRisk) {
        this.forgettingRisk = forgettingRisk;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public String getEvidenceSource() {
        return evidenceSource;
    }

    public void setEvidenceSource(String evidenceSource) {
        this.evidenceSource = evidenceSource == null ? "" : evidenceSource;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(String evidenceId) {
        this.evidenceId = evidenceId == null ? "" : evidenceId;
    }
}
