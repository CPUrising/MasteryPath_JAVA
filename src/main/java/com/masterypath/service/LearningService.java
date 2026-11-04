package com.masterypath.service;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.grading.Grading;
import com.masterypath.domain.mode.BehaviorBudget;
import com.masterypath.domain.model.AssessmentResult;
import com.masterypath.domain.model.AssessmentType;
import com.masterypath.domain.model.CoverageReport;
import com.masterypath.domain.model.DeferredObjective;
import com.masterypath.domain.model.InteractionStatus;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.LearnerMasteryOverride;
import com.masterypath.domain.model.LearningEvidence;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.domain.model.QuizAttempt;
import com.masterypath.domain.model.SourceGap;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.domain.outline.BuildResult;
import com.masterypath.domain.outline.OutlineBuilder;
import com.masterypath.domain.outline.ReviseResult;
import com.masterypath.domain.outline.SourceResolver;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.domain.policy.MasteryScore;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import com.masterypath.storage.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务核心（参考实现 {@code learning/service.py} 的 {@code LearningService}）。
 *
 * <p>本类只做<b>编排</b>：每个写方法都包成 {@code store.mutate(pathId, fn)}，在回调里改聚合并
 * {@code tx.emit(...)}。业务判定一律委托给 {@link MasteryPolicy}，本类不重复实现门槛逻辑。
 *
 * <p>错误文案与事件 payload 逐字复刻参考实现，包括<b>不对称</b>之处：
 * {@link #overrideMastery} 用 {@code "unknown knowledge point: "}，而
 * {@link #deferObjective} / {@link #noteExplained} 用 {@code "unknown objective: "}。
 */
public final class LearningService {

    private final LearningStore store;

    public LearningService(LearningStore store) {
        this.store = store;
    }

    /** 暴露给上层（{@code 0007} 起）读取聚合，避免各处重复持有 store。 */
    public LearningStore store() {
        return store;
    }

    // -- 路径生命周期 ---------------------------------------------------------

    /**
     * 创建路径；已存在则拒绝。
     *
     * <p>与参考实现一致：存在性检查在事务之外，不额外加锁（会留下 TOCTOU 窗口，属已知行为）。
     *
     * @throws PreconditionFailedException 路径已存在
     */
    public LearningProgress createPath(String pathId, String goal, String name) {
        if (store.load(pathId) != null) {
            throw new PreconditionFailedException("path already exists: " + pathId);
        }
        return store.mutate(pathId, tx -> {
            tx.progress().setGoal(goal == null ? "" : goal);
            tx.progress().setName(name == null ? "" : name);
            tx.emit("path.created", payload(
                    "goal", tx.progress().getGoal(),
                    "name", tx.progress().getName()));
            return tx.progress();
        }, null, true);
    }

    /** 改名。参考实现不校验空名，这里照抄。 */
    public Map<String, Object> rename(String pathId, String name) {
        return store.mutate(pathId, tx -> {
            tx.progress().setName(name == null ? "" : name);
            tx.emit("path.renamed", payload("name", tx.progress().getName()));
            return payload("status", "renamed", "name", tx.progress().getName());
        });
    }

    /** 删除路径及其从属数据；不存在返回 {@code false}。 */
    public boolean delete(String pathId) {
        return store.deletePath(pathId);
    }

    // -- 来源附加 -------------------------------------------------------------

    /**
     * 整体替换路径的来源列表（例如扫描文件夹后）。
     *
     * @return 替换后的全量来源（序列化为 map）
     */
    public List<Map<String, Object>> attachSources(String pathId, List<TopicSource> sources) {
        List<TopicSource> incoming = sources == null ? List.of() : List.copyOf(sources);
        return store.mutate(pathId, tx -> {
            tx.progress().setSources(new ArrayList<>(incoming));
            tx.emit("sources.attached", payload("count", incoming.size()));
            return dumpSources(tx.progress().getSources());
        }, null, true);
    }

    /**
     * 只替换 {@code kind == "knowledge_base"} 的来源，其余类别原样保留并排在前面。
     *
     * <p>与 {@link #attachSources} 的区别就在这里：UI 增删知识库时不能清掉用户已附加的文件来源。
     *
     * @return 合并后的全量来源（序列化为 map）
     */
    public List<Map<String, Object>> setKnowledgeBaseSources(String pathId, List<TopicSource> sources) {
        List<TopicSource> incoming = sources == null ? List.of() : List.copyOf(sources);
        return store.mutate(pathId, tx -> {
            List<TopicSource> others = new ArrayList<>();
            for (TopicSource source : tx.progress().getSources()) {
                if (!"knowledge_base".equals(source.getKind())) {
                    others.add(source);
                }
            }
            others.addAll(incoming);
            tx.progress().setSources(others);
            tx.emit("sources.knowledge_bases", payload("count", incoming.size()));
            return dumpSources(tx.progress().getSources());
        }, null, true);
    }

    // -- 目标状态 -------------------------------------------------------------

    /**
     * 学习者声明掌握：推进前沿，<b>不写分数、不产证据</b>（{@code INV-01} / {@code INV-15}）。
     *
     * <p>{@code gateCleared} 必须在写入 override <b>之后</b>计算——{@code isMastered} 读的就是
     * override，先算后写会让它恒为 {@code false}。
     *
     * <p>已知缺口：参考实现在 {@code gateCleared} 时还会播种复习计划并发 {@code review.scheduled}。
     * 该能力属 {@code 0010}（SM-2 调度），本 change 不实现。
     *
     * @return {@code knowledge_point_id} / {@code source="learner"} / {@code gate_cleared}
     */
    public Map<String, Object> overrideMastery(String pathId, String kpId, String note) {
        String safeNote = note == null ? "" : note;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            LearningProgress.KnowledgePointLocation location = progress.findKnowledgePoint(kpId);
            tx.require(location.found(), "unknown knowledge point: " + kpId);
            KnowledgePoint kp = location.knowledgePoint();

            boolean wasMastered = MasteryPolicy.isMastered(progress, kp);
            progress.getLearnerMasteryOverrides()
                    .put(kpId, new LearnerMasteryOverride(kpId, safeNote));
            boolean gateCleared = MasteryPolicy.isMastered(progress, kp) && !wasMastered;

            tx.emit("mastery.override", payload(
                    "knowledge_point_id", kpId,
                    "note", safeNote));
            return payload(
                    "knowledge_point_id", kpId,
                    "source", "learner",
                    "gate_cleared", gateCleared);
        });
    }

    /**
     * 延后一个目标：写入 {@code deferredObjectives}，该 KP 不再被 {@code nextObjective} 选中。
     *
     * <p>事件 payload <b>不含</b> {@code note}（与 {@link #noteExplained} 不对称，属参考实现原样行为）。
     */
    public Map<String, Object> deferObjective(String pathId, String kpId, String note) {
        String safeNote = note == null ? "" : note;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            tx.require(progress.findKnowledgePoint(kpId).found(), "unknown objective: " + kpId);
            progress.getDeferredObjectives()
                    .put(kpId, new DeferredObjective(kpId, safeNote));
            tx.emit("objective.deferred", payload("knowledge_point_id", kpId));
            return payload("knowledge_point_id", kpId, "deferred", true);
        });
    }

    /**
     * 记录「已讲解」：只写 {@code explainedObjectives}，<b>不给掌握学分</b>。
     *
     * <p>事件 payload 含 {@code summary}（与 {@link #deferObjective} 不对称）。
     */
    public Map<String, Object> noteExplained(String pathId, String kpId, String summary) {
        String safeSummary = summary == null ? "" : summary;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            tx.require(progress.findKnowledgePoint(kpId).found(), "unknown objective: " + kpId);
            progress.getExplainedObjectives().put(kpId, payload("summary", safeSummary));
            tx.emit("objective.explained", payload(
                    "knowledge_point_id", kpId,
                    "summary", safeSummary));
            return payload("knowledge_point_id", kpId, "mastery_credit", false);
        });
    }

    // -- 大纲 ----------------------------------------------------------------

    /**
     * 构建大纲；非空路径需 {@code confirm}，否则返回 {@code refused_rebuild} 且零状态变更。
     *
     * <p>成功时发 {@code outline.built}（首次）或 {@code path.rebuilt}（替换）。
     */
    public BuildResult build(String pathId, Map<String, Object> payload, boolean confirm) {
        return store.mutate(pathId, tx -> {
            SourceResolver resolver = SourceResolver.fromSources(tx.progress().getSources());
            BuildResult result = OutlineBuilder.build(
                    tx.progress(), payload, confirm, resolver, resolver.selectedIds());
            if (result.isBuilt()) {
                String eventType = result.rebuilt() ? "path.rebuilt" : "outline.built";
                Map<String, Object> eventPayload = payload(
                        "module_count", result.modules().size(),
                        "gap_ids", result.gaps().stream().map(SourceGap::getId).toList());
                tx.emit(eventType, eventPayload);
            }
            return result;
        }, null, true);
    }

    /**
     * 按差分操作修订大纲；未触动的知识点进度保留。
     *
     * <p>无论是否有实际变更都发 {@code outline.revised}（与参考实现一致）。
     */
    public ReviseResult revise(String pathId, List<Map<String, Object>> ops) {
        return store.mutate(pathId, tx -> {
            SourceResolver resolver = SourceResolver.fromSources(tx.progress().getSources());
            ReviseResult result = OutlineBuilder.revise(tx.progress(), ops, resolver.selectedIds());
            tx.emit("outline.revised", payload(
                    "applied", result.applied(),
                    "dropped_kp_ids", result.droppedKpIds()));
            return result;
        });
    }

    /** 接受缺口或记录一次重新生成尝试。 */
    public SourceGap resolveGap(String pathId, String gapId, String action) {
        return store.mutate(pathId, tx -> {
            SourceGap gap = OutlineBuilder.resolveGap(tx.progress(), gapId, action);
            tx.emit("gap.resolved", payload("gap_id", gapId, "action", action));
            return gap;
        });
    }

    /** 读取覆盖报告；路径不存在或无报告时返回 {@code null}。 */
    public CoverageReport getCoverage(String pathId) {
        LearningProgress progress = store.load(pathId);
        return progress == null ? null : progress.getCoverage();
    }

    // -- 出题与判分 -----------------------------------------------------------

    /**
     * 注册题目（单槽位，{@code INV-02}）。
     *
     * <p>已有未终态题目时返回 {@code null}，不抛错、不产生新题——调用方据此「重放同一张卡」。
     *
     * @throws PreconditionFailedException 连续追问达到 {@link BehaviorBudget#MAX_CONSECUTIVE_QUESTIONS}
     */
    public MasteryInteraction registerQuestion(String pathId, PendingQuestion pending,
                                               String sessionId, String turnId) {
        if (store.mutate(pathId, tx -> tx.getOpenInteraction(), null, true) != null) {
            return null;
        }
        return store.mutate(pathId, tx -> {
            if (tx.getOpenInteraction() != null) {
                return null;
            }
            LearningProgress progress = tx.progress();
            tx.require(progress.getConsecutiveQuestions() < BehaviorBudget.MAX_CONSECUTIVE_QUESTIONS,
                    "Too many questions in a row without an answer; let the learner "
                            + "answer before asking again.");
            progress.setConsecutiveQuestions(progress.getConsecutiveQuestions() + 1);
            progress.setPendingQuestion(pending);

            MasteryInteraction interaction = new MasteryInteraction();
            interaction.setInteractionId(pending.getQuestionId());
            interaction.setPathId(pathId);
            interaction.setQuestion(pending);
            interaction.setStatus(InteractionStatus.AWAITING_INPUT);
            interaction.setSessionId(sessionId);
            interaction.setTurnId(turnId);
            tx.saveInteraction(interaction);

            tx.emit("question.registered", payload(
                    "question_id", pending.getQuestionId(),
                    "knowledge_point_id", pending.getKnowledgePointId(),
                    "question_type", pending.getQuestionType().value()));
            return interaction;
        });
    }

    /**
     * 判分（确定性，{@code INV-32}）。
     *
     * <p>幂等键 {@code (pathId, questionId)}：已判分时返回首次结果 + {@code status=already_graded}，
     * 证据不增加（{@code INV-21}）。无法解析时返回 {@code ungraded} 且零状态变更（{@code INV-46}）。
     *
     * <p>降级规则：{@code open} 的 {@code correct} → {@code partial}（{@code INV-35}）；
     * 有提示的 {@code correct} → {@code partial}（{@code INV-36}）。
     *
     * <p>已知缺口：参考实现在 {@code gateCleared} 时还会播种复习计划并发 {@code review.scheduled}。
     * 该能力属 {@code 0010}（SM-2 调度），本 change 不实现。
     */
    public Map<String, Object> grade(String pathId, String questionId, String answer,
                                     int hintsUsed, boolean review,
                                     String sessionId, String turnId) {
        MasteryInteraction interaction = store.getInteraction(pathId, questionId);
        if (interaction == null) {
            throw new PreconditionFailedException("unknown question_id: " + questionId);
        }
        if (interaction.getStatus() == InteractionStatus.GRADED) {
            Map<String, Object> replay = new LinkedHashMap<>(interaction.getResult());
            replay.put("status", "already_graded");
            return replay;
        }
        if (!interaction.isOpen()) {
            throw new PreconditionFailedException("interaction is not gradable");
        }

        Decision decision = Grading.grade(interaction.getQuestion(), answer);
        if (decision.isUngraded()) {
            // 零状态变更：绝不把猜测记成作答（INV-46）。
            return payload("status", "ungraded", "reason", decision.reason());
        }

        return store.mutate(pathId, tx -> {
            MasteryInteraction current = tx.getInteraction(questionId);
            tx.require(current != null, "unknown question_id");
            if (current.getStatus() == InteractionStatus.GRADED) {
                Map<String, Object> replay = new LinkedHashMap<>(current.getResult());
                replay.put("status", "already_graded");
                return replay;
            }
            PendingQuestion pending = current.getQuestion();
            LearningProgress progress = tx.progress();
            LearningProgress.KnowledgePointLocation location =
                    progress.findKnowledgePoint(pending.getKnowledgePointId());
            tx.require(location.found(),
                    "unknown objective: " + pending.getKnowledgePointId());
            KnowledgePoint kp = location.knowledgePoint();

            String result = decision.result();
            if (pending.getQuestionType() == PendingQuestion.QuestionType.OPEN
                    && Decision.CORRECT.equals(result)) {
                result = Decision.PARTIAL;   // 开放题不单独授予掌握（INV-35）
            }
            if (hintsUsed > 0 && Decision.CORRECT.equals(result)) {
                result = Decision.PARTIAL;   // INV-36
            }
            if (hintsUsed > BehaviorBudget.MAX_HINTS_PER_QUESTION && Decision.CORRECT.equals(result)) {
                result = Decision.PARTIAL;   // 提示预算超限（INV-60）；与上一条重叠，照抄参考实现
            }
            boolean isCorrect = Decision.CORRECT.equals(result);

            boolean wasMastered = MasteryPolicy.isMastered(progress, kp);
            double before = progress.getMasteryLevels().getOrDefault(kp.getId(), 0.0);

            QuizAttempt attempt = new QuizAttempt();
            attempt.setQuestionId(pending.getQuestionId());
            attempt.setKnowledgePointId(kp.getId());
            attempt.setModuleId(pending.getModuleId());
            attempt.setCorrect(isCorrect);
            attempt.setUserAnswer(answer);
            attempt.setIndependent(hintsUsed == 0);
            progress.getQuizAttempts().add(attempt);

            String evidenceId = newEvidenceId();
            LearningEvidence evidence = new LearningEvidence();
            evidence.setEvidenceId(evidenceId);
            evidence.setQuestionId(pending.getQuestionId());
            evidence.setKnowledgePointId(kp.getId());
            evidence.setAssessmentType(review ? AssessmentType.REVIEW : AssessmentType.QUIZ);
            evidence.setResult(AssessmentResult.fromValue(result));
            evidence.setHintsUsed(hintsUsed);
            evidence.setSessionId(sessionId);
            evidence.setTurnId(turnId);
            evidence.setSource("mastery_path");
            progress.getLearningEvidence().add(evidence);

            // 定性类型不写分数：分数只能由 assess 打开（INV-05）。
            if (!MasteryPolicy.QUALITATIVE_TYPES.contains(kp.getType())) {
                progress.getMasteryLevels().put(kp.getId(),
                        MasteryScore.compute(MasteryPolicy.activeAttempts(progress, kp.getId())));
            }
            double after = progress.getMasteryLevels().getOrDefault(kp.getId(), 0.0);
            boolean gateCleared = MasteryPolicy.isMastered(progress, kp) && !wasMastered;
            String source = MasteryPolicy.masterySource(progress, kp);

            progress.setPendingQuestion(null);
            progress.setConsecutiveQuestions(0);

            Map<String, Object> graded = payload(
                    "status", "graded",
                    "knowledge_point_id", kp.getId(),
                    "is_correct", isCorrect,
                    "result", result,
                    "mastery_before", before,
                    "mastery_after", after,
                    "gate_cleared", gateCleared,
                    "source", source,
                    "explanation", pending.getExplanation());

            current.setStatus(InteractionStatus.GRADED);
            current.setUserAnswer(answer);
            current.setResult(graded);
            tx.saveInteraction(current);

            tx.saveBankEntry(bankEntry(pathId, kp, pending, answer, isCorrect, result));

            tx.emit("attempt.graded", payload(
                    "question_id", pending.getQuestionId(),
                    "knowledge_point_id", kp.getId(),
                    "is_correct", isCorrect,
                    "result", result,
                    "hints_used", hintsUsed,
                    "mastery_after", after));
            if (gateCleared) {
                tx.emit("objective.mastered", payload(
                        "knowledge_point_id", kp.getId(),
                        "source", source));
            }
            return graded;
        });
    }

    /**
     * 跳过题目：未终态置 {@code ABANDONED}，清空槽位与连续追问计数。
     *
     * <p>已终态时原样返回状态，不重复发事件。
     */
    public Map<String, Object> skipQuestion(String pathId, String questionId) {
        return store.mutate(pathId, tx -> {
            MasteryInteraction current = tx.getInteraction(questionId);
            tx.require(current != null, "unknown question_id");
            if (current.isTerminal()) {
                return payload("status", current.getStatus().value());
            }
            current.setStatus(InteractionStatus.ABANDONED);
            tx.saveInteraction(current);
            tx.progress().setPendingQuestion(null);
            tx.progress().setConsecutiveQuestions(0);
            tx.emit("question.skipped", payload("question_id", questionId));
            return payload("status", "abandoned", "question_id", questionId);
        });
    }

    /** 题库列表；{@code tag} 非空时只返回含该标签的条目。 */
    public List<QuestionBankEntry> listQuestions(String pathId, String tag) {
        List<QuestionBankEntry> entries = store.listBankEntries(pathId);
        if (tag == null || tag.isBlank()) {
            return entries;
        }
        List<QuestionBankEntry> filtered = new ArrayList<>();
        for (QuestionBankEntry entry : entries) {
            if (entry.getTags() != null && entry.getTags().contains(tag)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    /** 给题库条目追加标签（去重，保持顺序）。 */
    public QuestionBankEntry tagQuestion(String entryId, List<String> tags) {
        return store.mutateBankEntry(entryId, entry -> {
            for (String tag : tags == null ? List.<String>of() : tags) {
                if (tag != null && !tag.isBlank() && !entry.getTags().contains(tag)) {
                    entry.getTags().add(tag);
                }
            }
            return entry;
        });
    }

    // -- 内部 ----------------------------------------------------------------

    /** 构造保持插入顺序的 payload；键值交替传入。 */
    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    /** 32 位十六进制证据 id（与参考实现的 {@code uuid4().hex} 长度一致）。 */
    private static String newEvidenceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** 判分时写入题库的条目。 */
    private static QuestionBankEntry bankEntry(String pathId, KnowledgePoint kp,
                                               PendingQuestion pending, String answer,
                                               boolean isCorrect, String result) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId(newEvidenceId());
        entry.setPathId(pathId);
        entry.setKnowledgePointId(kp.getId());
        entry.setKnowledgePointName(kp.getName());
        entry.setQuestionId(pending.getQuestionId());
        entry.setQuestion(pending.getPrompt());
        entry.setQuestionType(pending.getQuestionType().value());
        List<Map<String, Object>> options = new ArrayList<>();
        for (com.masterypath.domain.model.PendingOption option : pending.getOptions()) {
            options.add(payload("label", option.getLabel(), "body", option.getBody()));
        }
        entry.setOptions(options);
        entry.setCorrectAnswer(pending.getExpectedAnswer());
        entry.setExplanation(pending.getExplanation());
        entry.setDifficulty(pending.getDifficulty());
        entry.setUserAnswer(answer);
        entry.setCorrect(isCorrect);
        entry.setResult(result);
        entry.setSource("mastery_path");
        return entry;
    }

    /** 来源序列化：字段名与参考实现 {@code model_dump()} 一致（snake_case）。 */
    private static List<Map<String, Object>> dumpSources(List<TopicSource> sources) {
        List<Map<String, Object>> dumped = new ArrayList<>();
        for (TopicSource source : sources) {
            dumped.add(payload(
                    "id", source.getId(),
                    "kind", source.getKind(),
                    "source_id", source.getSourceId(),
                    "label", source.getLabel(),
                    "excerpt", source.getExcerpt(),
                    "position", source.getPosition(),
                    "available", source.isAvailable(),
                    "metadata", source.getMetadata()));
        }
        return dumped;
    }
}
