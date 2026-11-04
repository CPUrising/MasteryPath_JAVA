package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习模块（spec 01 §二）。
 *
 * <p>{@code displayTarget} 默认 {@code 0.7}，<b>仅用于展示</b>，MUST NOT 参与推进判定
 * （{@code INV-18} / {@code TC-F1}）。
 */
public class LearningModule {

    private String id;
    private String name;
    private int order;
    private String objective = "";
    private double displayTarget = 0.7;
    private List<KnowledgePoint> knowledgePoints = new ArrayList<>();

    public LearningModule() {
    }

    public LearningModule(String id, String name, int order) {
        this.id = id;
        this.name = name;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective == null ? "" : objective;
    }

    public double getDisplayTarget() {
        return displayTarget;
    }

    public void setDisplayTarget(double displayTarget) {
        this.displayTarget = displayTarget;
    }

    public List<KnowledgePoint> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KnowledgePoint> knowledgePoints) {
        this.knowledgePoints = knowledgePoints == null ? new ArrayList<>() : knowledgePoints;
    }
}
