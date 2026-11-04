package com.masterypath.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 路径聚合根（spec 01 §二）。
 *
 * <p>这是唯一被 CAS 保护的聚合：所有写入都经存储层的 {@code mutate(pathId, fn)}，
 * 并以 {@code version} 做乐观并发校验（{@code INV-11}）。
 *
 * <p>刻意设计为可变类：参考实现的模型是「读聚合 → 就地改 → 整体写回」，
 * 用不可变对象会让服务层每次改动都产生大量样板。
 */
public class LearningProgress {

    private String pathId;
    private String name = "";
    private String goal = "";
    /** scratch path 的创建会话；无 objectives 时随会话隐藏/清理（ADR-021）。 */
    private String ownerSessionId = "";
    private List<TopicSource> sources = new ArrayList<>();
    private Map<String, Object> learnerProfile;
    private List<LearningModule> modules = new ArrayList<>();
    private String currentModuleId = "";
    private int currentKpIndex;
    private Map<String, KnowledgeType> knowledgeTypes = new LinkedHashMap<>();
    private Map<String, Double> masteryLevels = new LinkedHashMap<>();
    private Map<String, Boolean> qualitativeMastery = new LinkedHashMap<>();
    private List<QuizAttempt> quizAttempts = new ArrayList<>();
    private List<LearningEvidence> learningEvidence = new ArrayList<>();
    private Map<String, RepetitionState> repetitionStates = new LinkedHashMap<>();
    private List<ReviewTask> reviewQueue = new ArrayList<>();
    private Map<String, LearnerMasteryOverride> learnerMasteryOverrides = new LinkedHashMap<>();
    private Map<String, DeferredObjective> deferredObjectives = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> explainedObjectives = new LinkedHashMap<>();
    private List<SourceGap> sourceGaps = new ArrayList<>();
    private List<ObjectiveProposal> proposals = new ArrayList<>();
    private CoverageReport coverage;
    /** 连续出题但未获作答的次数（行为预算，{@code INV-60}）。 */
    private int consecutiveQuestions;
    private PendingQuestion pendingQuestion;
    private double desiredRetention = 0.9;
    private int version;
    private double createdAt = TimeSupport.nowSeconds();
    private double updatedAt = TimeSupport.nowSeconds();

    public LearningProgress() {
    }

    public LearningProgress(String pathId) {
        this.pathId = pathId;
    }

    public String getPathId() {
        return pathId;
    }

    public void setPathId(String pathId) {
        this.pathId = pathId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getGoal() {
        return goal;
    }

    /** {@code INV-13}：目标非显式编辑不得清空。 */
    public void setGoal(String goal) {
        this.goal = goal == null ? "" : goal;
    }

    public String getOwnerSessionId() {
        return ownerSessionId;
    }

    public void setOwnerSessionId(String ownerSessionId) {
        this.ownerSessionId = ownerSessionId == null ? "" : ownerSessionId;
    }

    public List<TopicSource> getSources() {
        return sources;
    }

    public void setSources(List<TopicSource> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public Map<String, Object> getLearnerProfile() {
        return learnerProfile;
    }

    public void setLearnerProfile(Map<String, Object> learnerProfile) {
        this.learnerProfile = learnerProfile;
    }

    public List<LearningModule> getModules() {
        return modules;
    }

    public void setModules(List<LearningModule> modules) {
        this.modules = modules == null ? new ArrayList<>() : modules;
    }

    public String getCurrentModuleId() {
        return currentModuleId;
    }

    public void setCurrentModuleId(String currentModuleId) {
        this.currentModuleId = currentModuleId == null ? "" : currentModuleId;
    }

    public int getCurrentKpIndex() {
        return currentKpIndex;
    }

    public void setCurrentKpIndex(int currentKpIndex) {
        this.currentKpIndex = currentKpIndex;
    }

    public Map<String, KnowledgeType> getKnowledgeTypes() {
        return knowledgeTypes;
    }

    public void setKnowledgeTypes(Map<String, KnowledgeType> knowledgeTypes) {
        this.knowledgeTypes = knowledgeTypes == null ? new LinkedHashMap<>() : knowledgeTypes;
    }

    public Map<String, Double> getMasteryLevels() {
        return masteryLevels;
    }

    public void setMasteryLevels(Map<String, Double> masteryLevels) {
        this.masteryLevels = masteryLevels == null ? new LinkedHashMap<>() : masteryLevels;
    }

    public Map<String, Boolean> getQualitativeMastery() {
        return qualitativeMastery;
    }

    public void setQualitativeMastery(Map<String, Boolean> qualitativeMastery) {
        this.qualitativeMastery = qualitativeMastery == null ? new LinkedHashMap<>() : qualitativeMastery;
    }

    public List<QuizAttempt> getQuizAttempts() {
        return quizAttempts;
    }

    public void setQuizAttempts(List<QuizAttempt> quizAttempts) {
        this.quizAttempts = quizAttempts == null ? new ArrayList<>() : quizAttempts;
    }

    public List<LearningEvidence> getLearningEvidence() {
        return learningEvidence;
    }

    public void setLearningEvidence(List<LearningEvidence> learningEvidence) {
        this.learningEvidence = learningEvidence == null ? new ArrayList<>() : learningEvidence;
    }

    public Map<String, RepetitionState> getRepetitionStates() {
        return repetitionStates;
    }

    public void setRepetitionStates(Map<String, RepetitionState> repetitionStates) {
        this.repetitionStates = repetitionStates == null ? new LinkedHashMap<>() : repetitionStates;
    }

    public List<ReviewTask> getReviewQueue() {
        return reviewQueue;
    }

    public void setReviewQueue(List<ReviewTask> reviewQueue) {
        this.reviewQueue = reviewQueue == null ? new ArrayList<>() : reviewQueue;
    }

    public Map<String, LearnerMasteryOverride> getLearnerMasteryOverrides() {
        return learnerMasteryOverrides;
    }

    public void setLearnerMasteryOverrides(Map<String, LearnerMasteryOverride> overrides) {
        this.learnerMasteryOverrides = overrides == null ? new LinkedHashMap<>() : overrides;
    }

    public Map<String, DeferredObjective> getDeferredObjectives() {
        return deferredObjectives;
    }

    public void setDeferredObjectives(Map<String, DeferredObjective> deferredObjectives) {
        this.deferredObjectives = deferredObjectives == null ? new LinkedHashMap<>() : deferredObjectives;
    }

    public Map<String, Map<String, Object>> getExplainedObjectives() {
        return explainedObjectives;
    }

    public void setExplainedObjectives(Map<String, Map<String, Object>> explainedObjectives) {
        this.explainedObjectives = explainedObjectives == null ? new LinkedHashMap<>() : explainedObjectives;
    }

    public List<SourceGap> getSourceGaps() {
        return sourceGaps;
    }

    public void setSourceGaps(List<SourceGap> sourceGaps) {
        this.sourceGaps = sourceGaps == null ? new ArrayList<>() : sourceGaps;
    }

    public List<ObjectiveProposal> getProposals() {
        return proposals;
    }

    public void setProposals(List<ObjectiveProposal> proposals) {
        this.proposals = proposals == null ? new ArrayList<>() : proposals;
    }

    public CoverageReport getCoverage() {
        return coverage;
    }

    public void setCoverage(CoverageReport coverage) {
        this.coverage = coverage;
    }

    public int getConsecutiveQuestions() {
        return consecutiveQuestions;
    }

    public void setConsecutiveQuestions(int consecutiveQuestions) {
        this.consecutiveQuestions = consecutiveQuestions;
    }

    public PendingQuestion getPendingQuestion() {
        return pendingQuestion;
    }

    public void setPendingQuestion(PendingQuestion pendingQuestion) {
        this.pendingQuestion = pendingQuestion;
    }

    public double getDesiredRetention() {
        return desiredRetention;
    }

    /** {@code 00} §四 #2：区间 {@code [0.7, 0.99]}。 */
    public void setDesiredRetention(double desiredRetention) {
        if (Double.isNaN(desiredRetention) || desiredRetention < 0.7 || desiredRetention > 0.99) {
            throw new IllegalArgumentException("desired_retention must be within [0.7, 0.99]");
        }
        this.desiredRetention = desiredRetention;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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

    /**
     * 按 id 查找知识点及其所属模块。
     *
     * @return 命中的 {@link KnowledgePointLocation}；找不到时两个字段均为 {@code null}
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public KnowledgePointLocation findKnowledgePoint(String kpId) {
        for (LearningModule module : modules) {
            for (KnowledgePoint kp : module.getKnowledgePoints()) {
                if (kp.getId() != null && kp.getId().equals(kpId)) {
                    return new KnowledgePointLocation(kp, module);
                }
            }
        }
        return KnowledgePointLocation.MISSING;
    }

    /** 知识点定位结果（等价于参考实现返回的 {@code (kp, module)} 二元组）。 */
    public record KnowledgePointLocation(KnowledgePoint knowledgePoint, LearningModule module) {

        public static final KnowledgePointLocation MISSING = new KnowledgePointLocation(null, null);

        public boolean found() {
            return knowledgePoint != null;
        }
    }
}
