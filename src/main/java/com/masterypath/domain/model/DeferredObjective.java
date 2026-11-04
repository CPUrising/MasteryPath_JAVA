package com.masterypath.domain.model;

/** 被暂时跳过的目标（spec 01 §二）。deferred 的 KP 不进入 {@code next_objective}。 */
public class DeferredObjective {

    private String knowledgePointId;
    private String note = "";
    private double createdAt = TimeSupport.nowSeconds();

    public DeferredObjective() {
    }

    public DeferredObjective(String knowledgePointId, String note) {
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
