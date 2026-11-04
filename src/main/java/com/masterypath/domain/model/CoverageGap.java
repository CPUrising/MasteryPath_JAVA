package com.masterypath.domain.model;

/** 未被覆盖的来源（{@link CoverageReport#getMissing()} 的元素）。 */
public class CoverageGap {

    private String label;
    private String document = "";

    public CoverageGap() {
    }

    public CoverageGap(String label, String document) {
        this.label = label;
        this.document = document == null ? "" : document;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document == null ? "" : document;
    }
}
