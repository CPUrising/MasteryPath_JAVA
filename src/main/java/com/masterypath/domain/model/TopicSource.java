package com.masterypath.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 主题来源（spec 01 §二）。
 *
 * <p>{@code metadata} 是本地读取用的元数据（如扫描文件的路径），MUST NOT 原样发给模型供应商。
 */
public class TopicSource {

    private String id;
    private String kind = "file";
    private String sourceId = "";
    private String label = "";
    private String excerpt = "";
    private int position;
    private boolean available = true;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public TopicSource() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind == null || kind.isEmpty() ? "file" : kind;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId == null ? "" : sourceId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt == null ? "" : excerpt;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("available")
    public boolean isAvailable() {
        return available;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("available")
    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
    }
}
