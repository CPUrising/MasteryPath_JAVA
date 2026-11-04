package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 缺口记录（spec 01 §二 / ADR-011 / {@code INV-23}）。
 *
 * <p>缺口 MUST 显式生成并交用户选择；禁止静默重建或静默丢弃。
 */
public class SourceGap {

    private String id;
    private GapKind kind;
    private String ref = "";
    private String reason = "";
    private List<String> candidateSourceIds = new ArrayList<>();
    private GapResolution resolution;
    private double createdAt = TimeSupport.nowSeconds();

    public SourceGap() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GapKind getKind() {
        return kind;
    }

    public void setKind(GapKind kind) {
        this.kind = kind;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref == null ? "" : ref;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason;
    }

    public List<String> getCandidateSourceIds() {
        return candidateSourceIds;
    }

    public void setCandidateSourceIds(List<String> candidateSourceIds) {
        this.candidateSourceIds = candidateSourceIds == null ? new ArrayList<>() : candidateSourceIds;
    }

    public GapResolution getResolution() {
        return resolution;
    }

    public void setResolution(GapResolution resolution) {
        this.resolution = resolution;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }
}
