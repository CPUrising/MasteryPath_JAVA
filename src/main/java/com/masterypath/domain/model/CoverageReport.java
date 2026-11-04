package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 大纲生成质量覆盖报告（spec 01 §二 / ADR-018 / {@code INV-56}）。
 *
 * <p>{@code reported=false} 表示模型未声明材料，此时 MUST NOT 把未用来源当作缺失（{@code TC-N3}）。
 */
public class CoverageReport {

    private int documents;
    private int covered;
    private List<CoverageGap> missing = new ArrayList<>();
    private boolean reported;
    private double groundingRatio = 1.0;
    private boolean acyclic = true;

    public CoverageReport() {
    }

    public int getDocuments() {
        return documents;
    }

    public void setDocuments(int documents) {
        this.documents = documents;
    }

    public int getCovered() {
        return covered;
    }

    public void setCovered(int covered) {
        this.covered = covered;
    }

    public List<CoverageGap> getMissing() {
        return missing;
    }

    public void setMissing(List<CoverageGap> missing) {
        this.missing = missing == null ? new ArrayList<>() : missing;
    }

    public boolean isReported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public double getGroundingRatio() {
        return groundingRatio;
    }

    public void setGroundingRatio(double groundingRatio) {
        this.groundingRatio = groundingRatio;
    }

    public boolean isAcyclic() {
        return acyclic;
    }

    public void setAcyclic(boolean acyclic) {
        this.acyclic = acyclic;
    }
}
