package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 题库条目（spec 01 §二 / {@code 06} §五）。
 *
 * <p>知识点关联是<b>自动</b>的；{@code tags} 为 MVP 手动。
 */
public class QuestionBankEntry {

    private String entryId;
    private String pathId;
    private String knowledgePointId;
    private String knowledgePointName = "";
    private String questionId = "";
    private String question = "";
    private String questionType = "short";
    private List<java.util.Map<String, Object>> options = new ArrayList<>();
    private String correctAnswer = "";
    private String explanation = "";
    private String difficulty = "";
    private String userAnswer = "";
    private boolean correct;
    private String result = "incorrect";
    private String source = "mastery_path";
    private List<String> tags = new ArrayList<>();
    private double createdAt = TimeSupport.nowSeconds();
    private double updatedAt = TimeSupport.nowSeconds();

    public QuestionBankEntry() {
    }

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
        this.entryId = entryId;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public String getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(String knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getKnowledgePointName() {
        return knowledgePointName;
    }

    public void setKnowledgePointName(String knowledgePointName) {
        this.knowledgePointName = knowledgePointName == null ? "" : knowledgePointName;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId == null ? "" : questionId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question == null ? "" : question;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType == null || questionType.isEmpty() ? "short" : questionType;
    }

    public List<java.util.Map<String, Object>> getOptions() {
        return options;
    }

    public void setOptions(List<java.util.Map<String, Object>> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer == null ? "" : correctAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation == null ? "" : explanation;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty == null ? "" : difficulty;
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
        this.result = result == null || result.isEmpty() ? "incorrect" : result;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source == null || source.isEmpty() ? "mastery_path" : source;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public double getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(double updatedAt) {
        this.updatedAt = updatedAt;
    }
}
