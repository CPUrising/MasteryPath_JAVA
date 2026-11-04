package com.masterypath.domain.model;

/** 选择题选项（spec 01 §二 / {@code PendingQuestion.options}）。 */
public class PendingOption {

    private String label;
    private String body;

    public PendingOption() {
    }

    public PendingOption(String label, String body) {
        this.label = label;
        this.body = body;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
