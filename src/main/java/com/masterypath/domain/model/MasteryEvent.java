package com.masterypath.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 领域事件，<b>只追加</b>（spec 01 §二 / {@code 05-api-contract.md} §四）。
 *
 * <p>{@code INV-37}：只在提交成功时发出；{@code INV-38}：{@code id} 与 {@code revision} 单调递增。
 */
public class MasteryEvent {

    private long id;
    private String pathId;
    private int revision;
    private String eventType;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private String sessionId = "";
    private String turnId = "";
    private double createdAt = TimeSupport.nowSeconds();

    public MasteryEvent() {
    }

    public MasteryEvent(String pathId, int revision, String eventType, Map<String, Object> payload) {
        this.pathId = pathId;
        this.revision = revision;
        this.eventType = eventType;
        this.payload = payload == null ? new LinkedHashMap<>() : payload;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload == null ? new LinkedHashMap<>() : payload;
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
