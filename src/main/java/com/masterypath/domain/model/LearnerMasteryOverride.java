package com.masterypath.domain.model;

/** 学习者声明掌握（spec 01 §二 / ADR-002）。声明 MUST NOT 写分数、MUST NOT 产证据。 */
public class LearnerMasteryOverride {

    private String knowledgePointId;
    private String note = "";
    private double createdAt = TimeSupport.nowSeconds();

    public LearnerMasteryOverride() {
    }

    public LearnerMasteryOverride(String knowledgePointId, String note) {
        this.knowledgePointId = knowledgePointId;
        this.note = note == null ? "" : note;
    }

    public String getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(String knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note == null ? "" : note;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }
}
