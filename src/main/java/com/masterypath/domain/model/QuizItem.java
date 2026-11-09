package com.masterypath.domain.model;

/**
 * 题卷中的一道题（{@code 0033}）。
 *
 * <p>{@link #question} 复用 {@link PendingQuestion}：答案键、可接受答案、关键词在生成时固定，
 * 判分复用 {@code Grading}，不调用模型。
 */
public class QuizItem {

    private String itemId = "";
    private String setId = "";
    private int seq;
    private PendingQuestion question = new PendingQuestion();
    private String userAnswer = "";
    private boolean correct;
    private String result = "";
    private double updatedAt = TimeSupport.nowSeconds();

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId == null ? "" : itemId;
    }

    public String getSetId() {
        return setId;
    }

    public void setSetId(String setId) {
        this.setId = setId == null ? "" : setId;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public PendingQuestion getQuestion() {
        return question;
    }

    public void setQuestion(PendingQuestion question) {
        this.question = question == null ? new PendingQuestion() : question;
    }

    /** 题目标识的便捷访问（等于 {@code question.question_id}）。 */
    public String questionId() {
        return question == null ? "" : question.getQuestionId();
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public void setUserAnswer(String userAnswer) {
        this.userAnswer = userAnswer == null ? "" : userAnswer;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("is_correct")
    public boolean isCorrect() {
        return correct;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("is_correct")
    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result == null ? "" : result;
    }

    public double getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(double updatedAt) {
        this.updatedAt = updatedAt;
    }
}
