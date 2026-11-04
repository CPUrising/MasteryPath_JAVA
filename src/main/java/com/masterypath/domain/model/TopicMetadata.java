package com.masterypath.domain.model;

/** 主题元数据（spec 01 §二）。 */
public class TopicMetadata {

    private String pathId;
    private String goal = "";
    private String description = "";
    private String emoji = "\uD83E\uDDED";
    private String status = "active";
    private double createdAt = TimeSupport.nowSeconds();
    private double updatedAt = TimeSupport.nowSeconds();

    public TopicMetadata() {
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal == null ? "" : goal;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji == null || emoji.isEmpty() ? "\uD83E\uDDED" : emoji;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isEmpty() ? "active" : status;
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
