package com.masterypath.domain.model;

/**
 * 单个知识点的间隔复习状态（spec 01 §二 / {@code 04-scheduling.md}）。
 *
 * <p>{@code INV-31}：只有 SM-2 字段参与计算；{@code difficulty}/{@code stability}/
 * {@code retrievability} 是 FSRS 字段，<b>仅展示</b>。
 */
public class RepetitionState {

    private int intervalIndex;
    private int consecutiveCorrect;
    private int consecutiveWrong;
    private double nextReviewAt;
    private double easeFactor = 2.5;
    private Double lastReviewAt;
    private Double lastScheduledAt;
    private boolean scheduledAfterFailure;
    private int reviewCount;
    private int lapseCount;
    private String provenance = "system";
    private double desiredRetention = 0.9;
    private double difficulty = 0.3;
    private double stability;
    private double retrievability = 1.0;

    public RepetitionState() {
    }

    public int getIntervalIndex() {
        return intervalIndex;
    }

    public void setIntervalIndex(int intervalIndex) {
        this.intervalIndex = intervalIndex;
    }

    public int getConsecutiveCorrect() {
        return consecutiveCorrect;
    }

    public void setConsecutiveCorrect(int consecutiveCorrect) {
        this.consecutiveCorrect = consecutiveCorrect;
    }

    public int getConsecutiveWrong() {
        return consecutiveWrong;
    }

    public void setConsecutiveWrong(int consecutiveWrong) {
        this.consecutiveWrong = consecutiveWrong;
    }

    public double getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(double nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    public Double getLastReviewAt() {
        return lastReviewAt;
    }

    public void setLastReviewAt(Double lastReviewAt) {
        this.lastReviewAt = lastReviewAt;
    }

    public Double getLastScheduledAt() {
        return lastScheduledAt;
    }

    public void setLastScheduledAt(Double lastScheduledAt) {
        this.lastScheduledAt = lastScheduledAt;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("scheduled_after_failure")
    public boolean isScheduledAfterFailure() {
        return scheduledAfterFailure;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("scheduled_after_failure")
    public void setScheduledAfterFailure(boolean scheduledAfterFailure) {
        this.scheduledAfterFailure = scheduledAfterFailure;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public int getLapseCount() {
        return lapseCount;
    }

    public void setLapseCount(int lapseCount) {
        this.lapseCount = lapseCount;
    }

    public String getProvenance() {
        return provenance;
    }

    public void setProvenance(String provenance) {
        this.provenance = provenance == null || provenance.isEmpty() ? "system" : provenance;
    }

    public double getDesiredRetention() {
        return desiredRetention;
    }

    public void setDesiredRetention(double desiredRetention) {
        this.desiredRetention = desiredRetention;
    }

    public double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(double difficulty) {
        this.difficulty = difficulty;
    }

    public double getStability() {
        return stability;
    }

    public void setStability(double stability) {
        this.stability = stability;
    }

    public double getRetrievability() {
        return retrievability;
    }

    public void setRetrievability(double retrievability) {
        this.retrievability = retrievability;
    }
}
