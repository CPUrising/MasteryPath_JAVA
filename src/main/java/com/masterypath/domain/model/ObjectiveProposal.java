package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习中提议的候选目标（spec 01 §二 / ADR-017）。
 *
 * <p>候选<b>不是知识点</b>：不影响地图、下一目标与门槛；仅当 outline 接受时经 {@code revise}
 * 才成为知识点（{@code INV-53/54}）。
 */
public class ObjectiveProposal {

    private String id;
    private String pathId = "";
    private String name;
    private KnowledgeType type;
    private String reason = "";
    private List<String> sourceIds = new ArrayList<>();
    private String moduleId = "";
    private String status = "pending";
    private String proposedBy = "tutor";
    private String sessionId = "";
    private String turnId = "";
    private double createdAt = TimeSupport.nowSeconds();

    public ObjectiveProposal() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId == null ? "" : pathId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KnowledgeType getType() {
        return type;
    }

    public void setType(KnowledgeType type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<String> sourceIds) {
        this.sourceIds = sourceIds == null ? new ArrayList<>() : sourceIds;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId == null ? "" : moduleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isEmpty() ? "pending" : status;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public void setProposedBy(String proposedBy) {
        this.proposedBy = proposedBy == null || proposedBy.isEmpty() ? "tutor" : proposedBy;
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

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }
}
