package com.masterypath.service;

import com.masterypath.domain.grading.Decision;
import com.masterypath.domain.grading.Grading;
import com.masterypath.domain.mode.BehaviorBudget;
import com.masterypath.domain.model.AssessmentResult;
import com.masterypath.domain.model.AssessmentType;
import com.masterypath.domain.model.CoverageGap;
import com.masterypath.domain.model.CoverageReport;
import com.masterypath.domain.model.DeferredObjective;
import com.masterypath.domain.model.GapKind;
import com.masterypath.domain.model.InteractionStatus;
import com.masterypath.domain.model.KnowledgePoint;
import com.masterypath.domain.model.KnowledgeType;
import com.masterypath.domain.model.LearnerMasteryOverride;
import com.masterypath.domain.model.LearningEvidence;
import com.masterypath.domain.model.LearningModule;
import com.masterypath.domain.model.LearningProgress;
import com.masterypath.domain.model.MasteryInteraction;
import com.masterypath.domain.model.ObjectiveProposal;
import com.masterypath.domain.model.PendingQuestion;
import com.masterypath.domain.model.QuestionBankEntry;
import com.masterypath.domain.model.QuizAttempt;
import com.masterypath.domain.model.RepetitionState;
import com.masterypath.domain.model.ReviewTask;
import com.masterypath.domain.model.SourceGap;
import com.masterypath.domain.model.TimeSupport;
import com.masterypath.domain.model.TopicSource;
import com.masterypath.domain.outline.BuildResult;
import com.masterypath.domain.outline.OutlineBuilder;
import com.masterypath.domain.outline.ReviseResult;
import com.masterypath.domain.outline.SourceResolver;
import com.masterypath.domain.policy.MasteryPolicy;
import com.masterypath.domain.policy.MasteryScore;
import com.masterypath.domain.policy.NextStep;
import com.masterypath.domain.projection.QuestionCardProjector;
import com.masterypath.domain.schedule.Sm2Scheduler;
import com.masterypath.storage.LearningStore;
import com.masterypath.storage.PreconditionFailedException;
import com.masterypath.storage.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /** 学习者画像的白名单字段；其余键静默丢弃（参考实现原样行为）。 */
    private static final java.util.Set<String> PROFILE_FIELDS = java.util.Set.of(
            "prior_knowledge",
            "target_level",
            "time_budget",
            "preferences",
            "teaching_strategy",
            "notes");

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
     * <p>首次掌握会播种复习计划并发 {@code review.scheduled}，provenance 为 {@code learner}，
     * 首间隔约为系统轨的一半（{@code INV-19}）。
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

            scheduleAfter(tx, kp, "correct", 0, false, gateCleared, "learner", "");

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

    /**
     * 当前地图的确定性质量指标（参考实现 {@code eval_outline}，{@code INV-57} / {@code spec 09}）。
     *
     * <p><b>重算而不是读存库报告</b>：参考实现按调用时的来源集合重新计算覆盖。
     * 只读，不写库、不调模型。
     *
     * @return 8 键报告；路径不存在返回 {@code null}
     */
    public Map<String, Object> evalOutline(String pathId) {
        LearningProgress progress = store.load(pathId);
        if (progress == null) {
            return null;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (TopicSource source : progress.getSources()) {
            selected.add(source.getId());
        }
        CoverageReport report = OutlineBuilder.computeCoverage(progress, selected);
        double coverageRatio = report.getDocuments() == 0
                ? 1.0 : (double) report.getCovered() / report.getDocuments();

        double coverageThreshold = 0.9;
        double groundingThreshold = 1.0;
        boolean acyclicThreshold = true;
        boolean passed = coverageRatio >= coverageThreshold
                && report.getGroundingRatio() >= groundingThreshold
                && report.isAcyclic() == acyclicThreshold;

        List<Map<String, Object>> missing = new ArrayList<>();
        for (CoverageGap gap : report.getMissing()) {
            missing.add(payload("label", gap.getLabel(), "document", gap.getDocument()));
        }
        Map<String, Object> thresholds = payload(
                "coverage", coverageThreshold,
                "grounding", groundingThreshold,
                "acyclic", acyclicThreshold);

        return payload(
                "coverage_ratio", coverageRatio,
                "grounding_ratio", report.getGroundingRatio(),
                "acyclic", report.isAcyclic(),
                "documents", report.getDocuments(),
                "covered", report.getCovered(),
                "missing", missing,
                "passed", passed,
                "thresholds", thresholds);
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
     * <p>首次掌握会播种复习计划；{@code review=true} 且已有状态时按结果重排。
     * 调度必须在清空待答题<b>之前</b>，否则事件顺序与参考实现不一致。
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

            scheduleAfter(tx, kp, result, hintsUsed, review, gateCleared, source, evidenceId);

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

    /**
     * 题库列表；{@code tag} 非空时只返回含该标签的条目。
     *
     * <p><b>真值语义</b>：参考实现用 {@code if tag:}，Python 中 {@code '  '} 是<b>真值</b>、
     * {@code ''} 与 {@code None} 是假值。因此这里用 {@code isEmpty()} 而非 {@code isBlank()}——
     * 后者会让空白标签静默失效。
     */
    public List<QuestionBankEntry> listQuestions(String pathId, String tag) {
        List<QuestionBankEntry> entries = store.listBankEntries(pathId);
        if (tag == null || tag.isEmpty()) {
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

    /**
     * 用给定列表<b>整体替换</b>题库条目的标签（参考实现 {@code entry.tags = list(dict.fromkeys(...))}）。
     *
     * <p>不是追加：调用方提交的是该条目的完整标签集。每个标签经 {@code str()} 转换
     * （{@code null} → {@code "None"}），去重且保持首次出现顺序。
     *
     * <p><b>不校验空值</b>：参考实现对空串与空白照收，此处不得「顺手加固」。
     *
     * @throws PreconditionFailedException 条目不存在
     */
    public QuestionBankEntry tagQuestion(String entryId, List<String> tags) {
        List<String> normalised = new ArrayList<>(new LinkedHashSet<>(
                pythonStrings(tags == null ? List.of() : tags)));
        QuestionBankEntry updated = store.mutateBankEntry(entryId, entry -> {
            entry.setTags(normalised);
            return entry;
        });
        if (updated == null) {
            throw new PreconditionFailedException("unknown question bank entry: " + entryId);
        }
        return updated;
    }

    /** 逐字复刻 Python 的 {@code str(tag)}：{@code None} → {@code "None"}，其余 {@code String.valueOf}。 */
    private static List<String> pythonStrings(List<String> tags) {
        List<String> out = new ArrayList<>(tags.size());
        for (String tag : tags) {
            out.add(tag == null ? "None" : tag);
        }
        return out;
    }

    /**
     * 定性评估（Feynman 式），{@code concept} / {@code design} 唯一的门槛打开方式。
     *
     * <p>与 {@link #grade} 的关键差异：<b>从不</b>写 {@code masteryLevels}（{@code INV-05}）；
     * 证据类型固定 {@code QUALITATIVE}；未通过记为 {@code partial} 而非 {@code incorrect}
     * （「还没讲清楚」不是「答错了」）。
     *
     * <p>{@code quality} 默认值：通过固定 {@code 1.0}（忽略传入值）；未通过时显式值优先，
     * 缺省 {@code 0.2}。
     *
     * <p>调度用的 {@code result} 是 {@code "correct" if passed else "incorrect"}，
     * <b>与证据结果不同</b>（证据未通过记 {@code partial}）——这是参考实现的不一致，照抄。
     *
     * @throws PreconditionFailedException 知识点不存在，或类型不是 {@code concept} / {@code design}
     */
    public Map<String, Object> assess(String pathId, String kpId, boolean passed,
                                      String evidenceText, Double quality, boolean review,
                                      String sessionId, String turnId) {
        String evidence = evidenceText == null ? "" : evidenceText;
        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            LearningProgress.KnowledgePointLocation location = progress.findKnowledgePoint(kpId);
            tx.require(location.found(), "unknown objective: " + kpId);
            KnowledgePoint kp = location.knowledgePoint();
            tx.require(MasteryPolicy.QUALITATIVE_TYPES.contains(kp.getType()),
                    "mastery_assess is for concept/design objectives; use mastery_grade "
                            + "for memory/procedure");

            boolean wasMastered = MasteryPolicy.isMastered(progress, kp);
            progress.getQualitativeMastery().put(kpId, passed);

            double recordedQuality;
            if (passed) {
                recordedQuality = 1.0;   // 通过时忽略传入值
            } else {
                recordedQuality = quality == null ? 0.2 : quality;
            }

            String evidenceId = newEvidenceId();
            LearningEvidence record = new LearningEvidence();
            record.setEvidenceId(evidenceId);
            record.setKnowledgePointId(kpId);
            record.setAssessmentType(AssessmentType.QUALITATIVE);
            record.setResult(passed ? AssessmentResult.CORRECT : AssessmentResult.PARTIAL);
            record.setQuality(recordedQuality);
            record.setHintsUsed(0);
            record.setSessionId(sessionId);
            record.setTurnId(turnId);
            record.setSource("mastery_path");
            progress.getLearningEvidence().add(record);

            boolean gateCleared = MasteryPolicy.isMastered(progress, kp) && !wasMastered;
            String source = MasteryPolicy.masterySource(progress, kp);

            scheduleAfter(tx, kp, passed ? "correct" : "incorrect", 0, review,
                    gateCleared, source, evidenceId);

            // 只清同一知识点的待答题，避免误清别的目标上的当前题。
            PendingQuestion pending = progress.getPendingQuestion();
            if (pending != null && kpId.equals(pending.getKnowledgePointId())) {
                progress.setPendingQuestion(null);
            }

            Map<String, Object> result = payload(
                    "status", "assessed",
                    "knowledge_point_id", kpId,
                    "passed", passed,
                    "gate_cleared", gateCleared,
                    "source", source);

            tx.emit("qualitative.assessed", payload(
                    "knowledge_point_id", kpId,
                    "passed", passed,
                    "quality", quality));
            if (gateCleared) {
                tx.emit("objective.mastered", payload(
                        "knowledge_point_id", kpId,
                        "source", source));
            }

            tx.saveBankEntry(writtenBankEntry(pathId, kp, evidence, passed));
            return result;
        });
    }

    // -- 题目修复（ADR-020） ---------------------------------------------------

    /**
     * 作废或改键一道题，然后重建该目标的状态（{@code ADR-020}）。
     *
     * <p>{@code void} 退役 attempt / evidence / 题库行；{@code correct} 换答案键、重判已存作答、
     * 追加改正证据。<b>历史永不删除</b>，只标记。
     *
     * <p>三项参数校验都在事务<b>之外</b>，顺序敏感：{@code action} → {@code reason} → {@code correct}
     * 的 {@code expectedAnswer}。{@code reason} 先截断到 500 再判空。
     *
     * @throws PreconditionFailedException 参数非法、题号无法解析、知识点不存在、改正后无法判分
     */
    public Map<String, Object> repairQuestion(String pathId, String questionId, String action,
                                              String expectedAnswer, String reason,
                                              String sessionId, String turnId) {
        String actionName = (action == null ? "" : action).strip().toLowerCase();
        if (!"void".equals(actionName) && !"correct".equals(actionName)) {
            throw new PreconditionFailedException("action must be 'void' or 'correct'");
        }
        String note = truncate((reason == null ? "" : reason).strip(), 500);
        if (note.isEmpty()) {
            throw new PreconditionFailedException("a reason is required so the repair can be audited");
        }
        String newExpected = (expectedAnswer == null ? "" : expectedAnswer).strip();
        if ("correct".equals(actionName) && newExpected.isEmpty()) {
            throw new PreconditionFailedException(
                    "correcting a question requires the new expected_answer");
        }
        String safeSession = sessionId == null ? "" : sessionId;
        String safeTurn = turnId == null ? "" : turnId;

        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            String requested = (questionId == null ? "" : questionId).strip();
            MasteryInteraction interaction = requested.isEmpty() ? null : tx.getInteraction(requested);
            PendingQuestion pending = progress.getPendingQuestion();
            if (interaction == null && requested.isEmpty()) {
                interaction = tx.getOpenInteraction();
            }
            String resolvedId;
            if (interaction != null) {
                resolvedId = interaction.getInteractionId();
            } else if (!requested.isEmpty()) {
                resolvedId = requested;
            } else if (pending != null) {
                resolvedId = pending.getQuestionId();
            } else {
                resolvedId = "";
                for (int index = progress.getQuizAttempts().size() - 1; index >= 0; index--) {
                    QuizAttempt attempt = progress.getQuizAttempts().get(index);
                    if (!attempt.isVoided()) {
                        resolvedId = attempt.getQuestionId();
                        break;
                    }
                }
            }
            tx.require(!resolvedId.isEmpty(), "no question is available to repair");

            if (interaction == null) {
                interaction = tx.getInteraction(resolvedId);
            }
            PendingQuestion question = interaction == null ? null : interaction.getQuestion();
            if (question == null && pending != null && resolvedId.equals(pending.getQuestionId())) {
                question = pending;
            }
            List<QuizAttempt> attempts = new ArrayList<>();
            for (QuizAttempt attempt : progress.getQuizAttempts()) {
                if (resolvedId.equals(attempt.getQuestionId())) {
                    attempts.add(attempt);
                }
            }
            List<LearningEvidence> evidenceRows = new ArrayList<>();
            for (LearningEvidence record : progress.getLearningEvidence()) {
                if (resolvedId.equals(record.getQuestionId())) {
                    evidenceRows.add(record);
                }
            }
            String kpId = firstNonEmpty(
                    question == null ? "" : question.getKnowledgePointId(),
                    attempts.isEmpty() ? "" : attempts.get(0).getKnowledgePointId(),
                    evidenceRows.isEmpty() ? "" : evidenceRows.get(0).getKnowledgePointId());
            LearningProgress.KnowledgePointLocation location = progress.findKnowledgePoint(kpId);
            tx.require(location.found(), "unknown objective: " + kpId);
            KnowledgePoint kp = location.knowledgePoint();

            boolean wasMastered = MasteryPolicy.isMastered(progress, kp);
            String previousExpected = question == null ? "" : question.getExpectedAnswer();
            Boolean previousIsCorrect = null;
            for (QuizAttempt attempt : attempts) {
                if (!attempt.isVoided()) {
                    previousIsCorrect = attempt.isCorrect();
                    break;
                }
            }

            Boolean repairedCorrect;
            if ("void".equals(actionName)) {
                for (QuizAttempt attempt : attempts) {
                    attempt.setVoided(true);
                    attempt.setVoidReason(note);
                }
                for (LearningEvidence record : evidenceRows) {
                    record.setVoided(true);
                    record.setVoidReason(note);
                }
                if (interaction != null) {
                    interaction.setStatus(InteractionStatus.ABANDONED);
                    tx.saveInteraction(interaction);
                }
                if (pending != null && resolvedId.equals(pending.getQuestionId())) {
                    progress.setPendingQuestion(null);
                }
                for (QuestionBankEntry entry : tx.findBankEntriesByQuestion(resolvedId)) {
                    entry.setCorrect(false);
                    entry.setResult("voided");
                    tx.saveBankEntry(entry);
                }
                repairedCorrect = null;
            } else {
                tx.require(question != null, "the corrected question is no longer available");
                String userAnswer = !attempts.isEmpty()
                        ? String.valueOf(attempts.get(0).getUserAnswer())
                        : (interaction == null ? "" : String.valueOf(interaction.getUserAnswer()));
                // 必须先改答案键再判分，否则判的还是旧键。
                question.setExpectedAnswer(newExpected);
                if (interaction != null) {
                    interaction.getQuestion().setExpectedAnswer(newExpected);
                }
                Decision decision = Grading.grade(question, userAnswer == null ? "" : userAnswer);
                tx.require(!decision.isUngraded(),
                        "corrected answer cannot be graded: " + decision.reason());
                String repairedResult = decision.result();
                if (question.getQuestionType() == PendingQuestion.QuestionType.OPEN
                        && Decision.CORRECT.equals(repairedResult)) {
                    repairedResult = Decision.PARTIAL;   // INV-35
                }
                repairedCorrect = Decision.CORRECT.equals(repairedResult);

                for (QuizAttempt attempt : attempts) {
                    attempt.setCorrect(repairedCorrect);
                    attempt.setVoided(false);
                    attempt.setVoidReason("");
                }
                for (LearningEvidence record : evidenceRows) {
                    if (!record.isVoided()) {
                        record.setVoided(true);
                        record.setVoidReason("answer key corrected: " + note);
                    }
                }
                LearningEvidence prior = evidenceRows.isEmpty()
                        ? null : evidenceRows.get(evidenceRows.size() - 1);
                LearningEvidence appended = new LearningEvidence();
                appended.setEvidenceId(newEvidenceId());
                appended.setQuestionId(resolvedId);
                appended.setKnowledgePointId(kp.getId());
                appended.setAssessmentType(prior != null
                        ? prior.getAssessmentType() : AssessmentType.QUIZ);
                appended.setResult(AssessmentResult.fromValue(repairedResult));
                appended.setQuality(prior != null && prior.getQuality() != null
                        ? prior.getQuality() : (repairedCorrect ? 1.0 : 0.0));
                appended.setHintsUsed(prior != null ? prior.getHintsUsed() : 0);
                appended.setAttemptCount(prior != null ? prior.getAttemptCount() : 1);
                appended.setSessionId(safeSession);
                appended.setTurnId(safeTurn);
                appended.setSource("mastery_path");
                progress.getLearningEvidence().add(appended);

                if (interaction != null) {
                    interaction.setStatus(InteractionStatus.GRADED);
                    interaction.setUserAnswer(userAnswer == null ? "" : userAnswer);
                    interaction.setResult(payload(
                            "status", "graded",
                            "knowledge_point_id", kp.getId(),
                            "is_correct", repairedCorrect,
                            "result", repairedResult,
                            "repaired", true));
                    tx.saveInteraction(interaction);
                }
                for (QuestionBankEntry entry : tx.findBankEntriesByQuestion(resolvedId)) {
                    entry.setCorrectAnswer(newExpected);
                    entry.setCorrect(repairedCorrect);
                    entry.setResult(repairedResult);
                    tx.saveBankEntry(entry);
                }
            }

            recomputeAfterRepair(tx, kp, wasMastered);
            tx.emit("void".equals(actionName) ? "assessment.voided" : "assessment.corrected",
                    payload(
                            "question_id", resolvedId,
                            "knowledge_point_id", kp.getId(),
                            "reason", note,
                            "previous_expected", previousExpected,
                            "expected_answer", newExpected,
                            "previous_is_correct", previousIsCorrect,
                            "is_correct", repairedCorrect),
                    safeSession, safeTurn);
            return payload(
                    "status", "repaired",
                    "action", actionName,
                    "question_id", resolvedId,
                    "knowledge_point_id", kp.getId(),
                    "path_revision", progress.getVersion() + 1,
                    "is_correct", repairedCorrect,
                    "mastery", round3(MasteryPolicy.displayMastery(progress, kp)),
                    "mastered", MasteryPolicy.isMastered(progress, kp),
                    "next", MasteryPolicy.nextObjective(progress).toMap());
        });
    }

    /**
     * 修复后重算该目标并调和复习计划（{@code ADR-020}）。
     *
     * <p>只有定量类型重算分数——定性目标的掌握由 {@code qualitativeMastery} 决定，
     * 修复不伪造分数（{@code INV-05}）。
     *
     * <p>与 {@code scheduleAfter}（{@code 0010}）<b>刻意不共用</b>：本函数先重算分数，
     * 再按「未掌握 → 清除计划 / 原本已掌握 → 不动 / 否则 → 播种」三分支；条件与
     * {@code scheduleAfter} 不同，抽公共函数会让两者交织。
     */
    private static void recomputeAfterRepair(Transaction tx, KnowledgePoint kp, boolean wasMastered) {
        LearningProgress progress = tx.progress();
        if (!MasteryPolicy.QUALITATIVE_TYPES.contains(kp.getType())) {
            progress.getMasteryLevels().put(kp.getId(),
                    MasteryScore.compute(MasteryPolicy.activeAttempts(progress, kp.getId())));
        }
        if (!MasteryPolicy.isMastered(progress, kp)) {
            progress.getRepetitionStates().remove(kp.getId());
            progress.getReviewQueue().removeIf(
                    task -> kp.getId().equals(task.getKnowledgePointId()));
            return;
        }
        if (wasMastered) {
            return;
        }
        RepetitionState state = Sm2Scheduler.initialState("system", TimeSupport.nowSeconds());
        progress.getRepetitionStates().put(kp.getId(), state);
        Sm2Scheduler.upsertReviewTask(progress, kp, state, "mastery_path", "");
        tx.emit("review.scheduled", payload(
                "knowledge_point_id", kp.getId(),
                "next_review_at", state.getNextReviewAt(),
                "interval_index", state.getIntervalIndex(),
                "provenance", state.getProvenance()));
    }

    // -- 学习者画像（ADR-020） -------------------------------------------------

    /**
     * 把 intake 作答合并进路径的学习者画像（{@code ADR-020}）。
     *
     * <p>只认 6 个白名单键，白名单外的键<b>静默丢弃</b>；值为 {@code null} 的跳过；
     * 其余经 {@code str().strip()[:2000]} 清洗。
     *
     * <p><b>无变更时直接返回且不发事件</b>——重复提交同一份画像不应产生噪音事件。
     *
     * @return {@code profile}（合并后的完整画像）与 {@code changed}（本次真正变化的字段名）
     * @throws PreconditionFailedException 清洗后无任何字段，或 {@code teaching_strategy} 非法
     */
    public ProfileUpdate recordLearnerProfile(String pathId, Map<String, Object> fields,
                                              String sessionId, String turnId) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry
                : (fields == null ? Map.<String, Object>of() : fields).entrySet()) {
            if (!PROFILE_FIELDS.contains(entry.getKey()) || entry.getValue() == null) {
                continue;
            }
            cleaned.put(entry.getKey(),
                    truncate(String.valueOf(entry.getValue()).strip(), 2000));
        }
        if (cleaned.isEmpty()) {
            throw new PreconditionFailedException("learner profile needs at least one field");
        }
        String strategy = cleaned.get("teaching_strategy");
        if (strategy != null && !strategy.isEmpty()
                && !"probe_first".equals(strategy) && !"teach_first".equals(strategy)) {
            throw new PreconditionFailedException(
                    "teaching_strategy must be probe_first or teach_first");
        }
        String safeSession = sessionId == null ? "" : sessionId;
        String safeTurn = turnId == null ? "" : turnId;

        return store.mutate(pathId, tx -> {
            LearningProgress progress = tx.progress();
            Map<String, Object> current = progress.getLearnerProfile() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(progress.getLearnerProfile());
            List<String> changed = new ArrayList<>();
            for (Map.Entry<String, String> entry : cleaned.entrySet()) {
                if (!entry.getValue().equals(current.get(entry.getKey()))) {
                    changed.add(entry.getKey());
                }
            }
            if (changed.isEmpty()) {
                return new ProfileUpdate(new LinkedHashMap<>(current), List.of());
            }
            current.putAll(cleaned);
            current.put("updated_at", TimeSupport.nowSeconds());
            progress.setLearnerProfile(current);
            tx.emit("path.learner_profile_recorded", payload("fields", changed),
                    safeSession, safeTurn);
            return new ProfileUpdate(new LinkedHashMap<>(current), List.copyOf(changed));
        }, null, true);
    }

    /** 画像写入结果：合并后的完整画像 + 本次真正变化的字段名。 */
    public record ProfileUpdate(Map<String, Object> profile, List<String> changed) {
    }

    // -- 状态快照 --------------------------------------------------------------

    /**
     * 路径状态快照（参考实现 {@code get_state}）。
     *
     * <p>{@code pendingQuestion} 走 {@link QuestionCardProjector} 白名单投影（{@code INV-07}）：
     * 学习者可见视图 MUST NOT 含 {@code expectedAnswer} / {@code explanation}。
     *
     * @return 路径不存在时返回 {@code {status: "missing", path_id: ...}}
     */
    public Map<String, Object> getState(String pathId) {
        LearningProgress progress = store.load(pathId);
        if (progress == null) {
            return payload("status", "missing", "path_id", pathId);
        }
        NextStep step = MasteryPolicy.nextObjective(progress);
        Map<String, Map<String, Object>> summary = MasteryPolicy.mapSummary(progress);
        Map<String, Object> counts = payload(
                "mastered", 0, "learning", 0, "new", 0, "total", summary.size());
        for (Map<String, Object> entry : summary.values()) {
            String status = String.valueOf(entry.get("status"));
            counts.put(status, ((Number) counts.get(status)).intValue() + 1);
        }
        double now = TimeSupport.nowSeconds();
        List<Map<String, Object>> reviews = new ArrayList<>();
        for (ReviewTask task : progress.getReviewQueue()) {
            Map<String, Object> info = summary.getOrDefault(task.getKnowledgePointId(), Map.of());
            reviews.add(payload(
                    "id", task.getId(),
                    "knowledge_point_id", task.getKnowledgePointId(),
                    "knowledge_point_name", String.valueOf(info.getOrDefault("name", "")),
                    "knowledge_type", task.getKnowledgeType().value(),
                    "due_at", task.getDueAt(),
                    "due", task.getDueAt() <= now,
                    "forgetting_risk", task.getForgettingRisk(),
                    "reason", task.getReason(),
                    "interval_index", task.getState().getIntervalIndex(),
                    "scheduled_after_failure", task.getState().isScheduledAfterFailure(),
                    "desired_retention", task.getState().getDesiredRetention(),
                    "provenance", task.getState().getProvenance()));
        }
        List<Map<String, Object>> knowledgeBases = new ArrayList<>();
        for (TopicSource source : progress.getSources()) {
            if ("knowledge_base".equals(source.getKind())) {
                knowledgeBases.add(payload(
                        "id", source.getSourceId() == null || source.getSourceId().isEmpty()
                                ? source.getId() : source.getSourceId(),
                        "name", source.getLabel(),
                        "ready", source.isAvailable()));
            }
        }
        List<Map<String, Object>> dueReviews = new ArrayList<>();
        for (ReviewTask task : MasteryPolicy.dueReviews(progress, now)) {
            dueReviews.add(reviewTaskMap(task));
        }
        return payload(
                "status", "active",
                "path_id", pathId,
                "name", progress.getName(),
                "goal", progress.getGoal(),
                "version", progress.getVersion(),
                "map", summary,
                "counts", counts,
                "next", step.toMap(),
                "sources", dumpSources(progress.getSources()),
                "knowledge_bases", knowledgeBases,
                "gaps", gapMaps(progress.getSourceGaps()),
                "proposals", progress.getProposals(),
                "coverage", progress.getCoverage(),
                "reviews", reviews,
                "review_settings", payload(
                        "desired_retention", progress.getDesiredRetention(),
                        "scope", "path"),
                "session_count", store.listSessions(pathId).size(),
                "learner_profile", progress.getLearnerProfile(),
                "pending_question", progress.getPendingQuestion() == null
                        ? null
                        : QuestionCardProjector.publicPendingQuestion(progress.getPendingQuestion()),
                "due_reviews", dueReviews);
    }

    // -- 路径概览与 scratch（ADR-021） -----------------------------------------

    /**
     * 学习者可切换的路径列表（参考实现 {@code list_path_overviews}）。
     *
     * <p><b>空 map 的路径被跳过</b>——这是 scratch 路径「隐藏」的唯一机制：scratch 没有知识点，
     * {@code mapSummary} 返回空 map，因此不出现在列表里。不需要额外的 {@code is_scratch} 标志。
     *
     * <p>副作用（照抄）：一条建过大纲但被清空的普通路径也会被隐藏。
     *
     * @return 按 {@code path_id} 升序的 9 键列表
     */
    public List<Map<String, Object>> listPathOverviews() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LearningProgress progress : store.listPaths()) {
            Map<String, Map<String, Object>> summary = MasteryPolicy.mapSummary(progress);
            if (summary.isEmpty()) {
                continue;
            }
            Map<String, Object> counts = payload(
                    "mastered", 0, "learning", 0, "new", 0, "total", summary.size());
            for (Map<String, Object> entry : summary.values()) {
                String status = String.valueOf(entry.get("status"));
                counts.put(status, ((Number) counts.get(status)).intValue() + 1);
            }
            rows.add(payload(
                    "path_id", progress.getPathId(),
                    "name", progress.getName() == null || progress.getName().isEmpty()
                            ? progress.getPathId() : progress.getName(),
                    "goal", progress.getGoal(),
                    "objectives", counts.get("total"),
                    "mastered", counts.get("mastered"),
                    "learning", counts.get("learning"),
                    "not_started", counts.get("new"),
                    "due_reviews", MasteryPolicy.dueReviews(progress).size(),
                    "owner_session_id", progress.getOwnerSessionId()));
        }
        rows.sort(java.util.Comparator.comparing(row -> String.valueOf(row.get("path_id"))));
        return rows;
    }

    /**
     * 返回该会话的私有 scratch 路径，不存在则建档（参考实现 {@code ensure_scratch_path}）。
     *
     * <p>id 派生：{@code "scratch-" + re.sub("[^A-Za-z0-9_-]", "_", sessionId).strip("_")}，
     * 空则兜底 {@code default}。注意 {@code strip("_")} 在替换<b>之后</b>：
     * {@code "!!!"} → {@code "___"} → {@code ""} → {@code "scratch-default"}。
     *
     * <p>已存在但无 owner 时<b>认领</b>（单独 mutate，<b>不发事件</b>——{@code path.created}
     * 已经发过了，再发会让事件流出现重复创建）。
     */
    public LearningProgress ensureScratchPath(String sessionId) {
        String raw = sessionId == null ? "" : sessionId;
        String safe = stripUnderscores(raw.replaceAll("[^A-Za-z0-9_-]", "_"));
        String pathId = "scratch-" + (safe.isEmpty() ? "default" : safe);
        LearningProgress existing = store.load(pathId);
        if (existing != null) {
            if ((existing.getOwnerSessionId() == null || existing.getOwnerSessionId().isEmpty())
                    && !raw.isEmpty()) {
                return store.mutate(pathId, tx -> {
                    tx.progress().setOwnerSessionId(raw);
                    return tx.progress();
                });
            }
            return existing;
        }
        return store.mutate(pathId, tx -> {
            tx.progress().setName("Scratch");
            tx.progress().setOwnerSessionId(raw);
            tx.emit("path.created", payload(
                    "goal", "",
                    "name", "Scratch",
                    "scratch", true), raw, "");
            return tx.progress();
        }, null, true);
    }

    /**
     * 把会话指向另一条路径，从下一回合生效（参考实现 {@code bind_session_path}）。
     *
     * <p>空 {@code sessionId} 返回 {@code null} 且<b>不写库</b>——没有会话就没有绑定对象，
     * 写库会产生孤儿记录。
     */
    public Map<String, Object> bindSessionPath(String sessionId, String pathId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        return store.setSessionPath(sessionId, pathId);
    }

    /**
     * 删除会话；若它是某条空 scratch 路径的 owner 且该路径已无其他会话，一并删除路径。
     *
     * <p>三重条件必须同时满足：{@code ownerSessionId == sessionId}、
     * 没有任何模块含知识点、该路径已无其他会话。
     *
     * <p>顺序照抄参考实现：先读 session → 读其 path → <b>删 session</b> → 再判断是否删 path。
     *
     * @return 会话不存在时 {@code false}；否则 {@code true}（路径是否删除不影响返回值）
     */
    public boolean deleteSession(String sessionId) {
        Map<String, Object> session = store.getSession(sessionId);
        if (session == null) {
            return false;
        }
        String pathId = String.valueOf(session.getOrDefault("path_id", ""));
        LearningProgress progress = pathId.isEmpty() ? null : store.load(pathId);
        store.deleteSession(sessionId);
        if (progress != null
                && sessionId.equals(progress.getOwnerSessionId())
                && !hasKnowledgePoints(progress)
                && store.listSessions(pathId).isEmpty()) {
            store.deletePath(pathId);
        }
        return true;
    }

    /**
     * 确保会话存在（参考实现 {@code ensure_session}）。
     *
     * <p><b>已存在时原样返回，不改任何字段</b>——包括 {@code mode}。
     * 调用方（{@link #recordTurn}）随后单独写 {@code mode}。
     *
     * @param mode 仅在建档时使用
     */
    public Map<String, Object> ensureSession(String pathId, String sessionId,
                                             String mode, String title) {
        Map<String, Object> existing = store.getSession(sessionId);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("session_id", sessionId);
        created.put("path_id", pathId);
        created.put("title", title == null ? "" : title);
        created.put("mode", mode == null || mode.isEmpty() ? "study" : mode);
        created.put("status", "active");
        created.put("message_count", 0);
        created.put("last_message", "");
        created.put("has_pending_question", false);
        return store.upsertSession(created);
    }

    /**
     * 记录一个完整回合（参考实现 {@code record_turn}）。
     *
     * <p>固定写两条消息（user + assistant），因此 {@code message_count} 加 <b>2</b>。
     * {@code last_message} 取 assistant 优先（Python 真值语义：空串回退到 user）。
     * {@code has_pending_question} 由 {@code card} 决定。
     *
     * @param card 题卡；非 {@code null} 时写入 assistant 消息的 {@code data.card}
     */
    public Map<String, Object> recordTurn(String pathId, String sessionId,
                                          String userText, String assistantText,
                                          String mode, String turnId,
                                          Map<String, Object> card) {
        String user = userText == null ? "" : userText;
        String assistant = assistantText == null ? "" : assistantText;
        Map<String, Object> session = ensureSession(pathId, sessionId, mode, "");
        Object rawTitle = session.get("title");
        if ((rawTitle == null || String.valueOf(rawTitle).isEmpty())
                && !user.strip().isEmpty()) {
            session.put("title", truncate(user.strip(), 40));
        }
        store.appendMessage(sessionId, "user", user, turnId, null);
        store.appendMessage(sessionId, "assistant", assistant, turnId,
                card == null ? null : payload("card", card));
        session.put("mode", mode == null || mode.isEmpty() ? "study" : mode);
        Object rawCount = session.get("message_count");
        int count = rawCount instanceof Number number ? number.intValue() : 0;
        session.put("message_count", count + 2);
        String last = assistant.strip().isEmpty() ? user : assistant;
        session.put("last_message", truncate(last.strip(), 200));
        session.put("has_pending_question", card != null);
        return store.upsertSession(session);
    }

    /**
     * 记录一次题卡作答：user 答案 + assistant 判分数据。
     *
     * <p>只由 REST 题卡路径调用，避免与 WS 回合的 {@link #recordTurn} 重复写消息。
     * {@code sessionId} 为空时不落库；调用方负责按 {@code status} 去重。
     *
     * @param outcome 工具返回的判分元数据（含 {@code status/result/is_correct/...}）
     */
    public Map<String, Object> recordGradeInteraction(String pathId, String sessionId,
                                                      String answer, Map<String, Object> outcome,
                                                      String turnId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        String user = answer == null ? "" : answer;
        String assistant = outcome == null ? ""
                : String.valueOf(outcome.getOrDefault("result",
                        outcome.getOrDefault("status", "")));
        Map<String, Object> session = ensureSession(pathId, sessionId, null, "");
        store.appendMessage(sessionId, "user", user, turnId, null);
        store.appendMessage(sessionId, "assistant", assistant, turnId,
                payload("grade", outcome));
        Object rawCount = session.get("message_count");
        int count = rawCount instanceof Number number ? number.intValue() : 0;
        session.put("message_count", count + 2);
        String last = assistant.strip().isEmpty() ? user : assistant;
        session.put("last_message", truncate(last.strip(), 200));
        session.put("has_pending_question", false);
        return store.upsertSession(session);
    }

    /**
     * 记录一次题卷生成回合：user 消息 + assistant 的 {@code data.quiz_set}。
     *
     * <p>题卷是练习，不改变 {@code has_pending_question}（保持 false）与掌握聚合。
     */
    public Map<String, Object> recordQuizSetTurn(String pathId, String sessionId,
                                                 String userText, String assistantText,
                                                 String mode, String turnId,
                                                 Map<String, Object> quizSet) {
        if (sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        String user = userText == null ? "" : userText;
        String assistant = assistantText == null ? "" : assistantText;
        Map<String, Object> session = ensureSession(pathId, sessionId, mode, "");
        Object rawTitle = session.get("title");
        if ((rawTitle == null || String.valueOf(rawTitle).isEmpty())
                && !user.strip().isEmpty()) {
            session.put("title", truncate(user.strip(), 40));
        }
        store.appendMessage(sessionId, "user", user, turnId, null);
        store.appendMessage(sessionId, "assistant", assistant, turnId,
                payload("quiz_set", quizSet));
        Object rawCount = session.get("message_count");
        int count = rawCount instanceof Number number ? number.intValue() : 0;
        session.put("message_count", count + 2);
        String last = assistant.strip().isEmpty() ? user : assistant;
        session.put("last_message", truncate(last.strip(), 200));
        session.put("has_pending_question", false);
        session.put("mode", mode == null || mode.isEmpty() ? "study" : mode);
        return store.upsertSession(session);
    }

    /** 该路径的会话列表（存储层转发）。 */
    public List<Map<String, Object>> listSessions(String pathId) {
        return store.listSessions(pathId);
    }

    /** 单个会话；不存在返回 {@code null}。 */
    public Map<String, Object> getSession(String sessionId) {
        return store.getSession(sessionId);
    }

    /**
     * 会话详情（参考实现 {@code get_session}）：会话行 + 消息列表。
     *
     * <p>与 {@link #getSession} 的区别是这里返回<b>嵌套结构</b>
     * {@code {session, messages}}，REST 端点用的就是它。
     *
     * @return 会话不存在时返回 {@code null}
     */
    public Map<String, Object> getSessionDetail(String sessionId) {
        Map<String, Object> session = store.getSession(sessionId);
        if (session == null) {
            return null;
        }
        return payload("session", session, "messages", store.listMessages(sessionId));
    }

    /**
     * 写入会话模式（参考实现 {@code set_session_mode}）。
     *
     * <p>会话不存在时先建档（{@code ensure_session}），再改 {@code mode}。
     * 工具层调用它时**不**因失败而让工具失败——回合已切到新模式，持久化失败只记录。
     *
     * @return 更新后的会话行
     */
    public Map<String, Object> setSessionMode(String pathId, String sessionId, String mode) {
        Map<String, Object> session = store.getSession(sessionId);
        if (session == null) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("session_id", sessionId);
            created.put("path_id", pathId);
            created.put("title", "");
            created.put("mode", mode);
            created.put("status", "active");
            created.put("message_count", 0);
            created.put("last_message", "");
            created.put("has_pending_question", false);
            session = store.upsertSession(created);
        }
        session.put("mode", mode);
        return store.upsertSession(session);
    }

    /**
     * 记录候选目标（参考实现 {@code propose_objective}，{@code INV-53/54}）。
     *
     * <p><b>不改地图</b>：只追加到 {@code proposals} 并发 {@code objective.proposed}。
     *
     * @return {@code {proposal_id, status}}
     */
    public Map<String, Object> proposeObjective(String pathId, String name, String kpType,
                                                String reason, String moduleId,
                                                List<String> sourceIds, String proposedBy,
                                                String sessionId, String turnId) {
        return store.mutate(pathId, tx -> {
            ObjectiveProposal proposal = new ObjectiveProposal();
            proposal.setId(newEvidenceId());
            proposal.setPathId(pathId);
            proposal.setName(name == null ? "" : name.strip());
            proposal.setType(KnowledgeType.fromValue(kpType));
            proposal.setReason(reason == null ? "" : reason);
            proposal.setModuleId(moduleId == null ? "" : moduleId);
            proposal.setSourceIds(sourceIds == null ? new ArrayList<>() : new ArrayList<>(sourceIds));
            proposal.setProposedBy("learner".equals(proposedBy) ? "learner" : "tutor");
            proposal.setSessionId(sessionId == null ? "" : sessionId);
            proposal.setTurnId(turnId == null ? "" : turnId);
            tx.progress().getProposals().add(proposal);
            tx.emit("objective.proposed", payload(
                    "proposal_id", proposal.getId(),
                    "name", proposal.getName(),
                    "type", proposal.getType().value()));
            return payload("proposal_id", proposal.getId(), "status", "pending");
        });
    }

    /**
     * 记录覆盖缺口（参考实现 {@code note_gap}）。
     *
     * <p><b>不改地图</b>：只追加到 {@code source_gaps} 并发 {@code gap.noted}。
     *
     * @return {@code {gap_id}}
     */
    public Map<String, Object> noteGap(String pathId, String kind, String ref, String reason) {
        return store.mutate(pathId, tx -> {
            SourceGap gap = new SourceGap();
            gap.setId("g" + newEvidenceId().substring(0, 8));
            gap.setKind(GapKind.fromValue(kind));
            gap.setRef(ref == null ? "" : ref);
            gap.setReason(reason == null ? "" : reason);
            tx.progress().getSourceGaps().add(gap);
            tx.emit("gap.noted", payload(
                    "gap_id", gap.getId(),
                    "kind", kind,
                    "ref", gap.getRef()));
            return payload("gap_id", gap.getId());
        });
    }

    /**
     * 清空学习态但保留地图（参考实现 {@code reset}）。
     *
     * <p>清 10 个 map 与 3 个 list 的对应字段；漏清任一项都会让旧进度影响新大纲。
     * 发 {@code path.reset}。
     *
     * @return {@code {status: "reset"}}
     */
    public Map<String, Object> reset(String pathId) {
        return store.mutate(pathId, tx -> {
            OutlineBuilder.resetLearningState(tx.progress());
            tx.emit("path.reset", payload());
            return payload("status", "reset");
        });
    }

    /**
     * 设置复习保留率（参考实现 {@code set_review_settings}）。
     *
     * <p>限幅到 {@code [0.7, 0.99]}，并回写**全部**已有重复状态——否则旧状态仍按旧值排期。
     * 发 {@code review.settings}。
     *
     * @return {@code {desired_retention, scope: "path"}}
     */
    public Map<String, Object> setReviewSettings(String pathId, double desiredRetention) {
        double value = Math.max(0.7, Math.min(0.99, desiredRetention));
        return store.mutate(pathId, tx -> {
            tx.progress().setDesiredRetention(value);
            for (RepetitionState state : tx.progress().getRepetitionStates().values()) {
                state.setDesiredRetention(value);
            }
            tx.emit("review.settings", payload("desired_retention", value));
            return payload("desired_retention", value, "scope", "path");
        });
    }

    /**
     * 接受或拒绝候选目标（参考实现 {@code resolve_proposal}）。
     *
     * <p>接受时重算覆盖报告；两种动作都发 {@code objective.proposal_resolved}。
     *
     * @return {@code {proposal_id, status}}
     * @throws PreconditionFailedException 动作非法或候选不存在
     */
    public Map<String, Object> resolveProposal(String pathId, String proposalId, String action) {
        return store.mutate(pathId, tx -> {
            ObjectiveProposal proposal;
            if ("accept".equals(action)) {
                proposal = OutlineBuilder.applyProposal(tx.progress(), proposalId);
                tx.progress().setCoverage(OutlineBuilder.computeCoverage(
                        tx.progress(), selectedSourceIds(tx.progress())));
            } else if ("reject".equals(action)) {
                proposal = OutlineBuilder.rejectProposal(tx.progress(), proposalId);
            } else {
                throw new PreconditionFailedException("unknown proposal action: " + action);
            }
            tx.emit("objective.proposal_resolved", payload(
                    "proposal_id", proposalId, "action", action));
            return payload("proposal_id", proposalId, "status", proposal.getStatus());
        });
    }

    /** 全部来源 id（覆盖计算用）。 */
    private static java.util.Set<String> selectedSourceIds(LearningProgress progress) {
        java.util.Set<String> ids = new LinkedHashSet<>();
        for (TopicSource source : progress.getSources()) {
            ids.add(source.getId());
        }
        return ids;
    }

    private static boolean hasKnowledgePoints(LearningProgress progress) {
        for (LearningModule module : progress.getModules()) {
            if (!module.getKnowledgePoints().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Python 的 {@code s.strip("_")}：只去首尾下划线（Java 的 {@code strip()} 只去空白）。 */
    private static String stripUnderscores(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '_') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '_') {
            end--;
        }
        return text.substring(start, end);
    }

    // -- 内部 ----------------------------------------------------------------

    /**
     * 掌握之后的三分支调度（参考实现 {@code _schedule_after}）。
     *
     * <ol>
     *   <li>{@code gateCleared} → 播种新计划并 upsert 复习任务，事件含 {@code provenance}</li>
     *   <li>{@code review} 且已有状态 → 按结果重排，事件含 {@code scheduled_after_failure}</li>
     *   <li>已有状态但非正式复习 → 只 touch {@code lastReviewAt}，不动锚点（{@code INV-29}）</li>
     * </ol>
     *
     * <p>分支 1 优先于分支 2：首次掌握即使标了 {@code review} 也只播种，不会对不存在的状态调
     * {@code applyReview}。两条事件 payload <b>不对称</b>（前者无 {@code scheduled_after_failure}、
     * 后者无 {@code provenance}），属参考实现原样行为。
     *
     * <p>{@code now} 只取一次：两次各取会让「首间隔恰为 0.5 天」的严格断言因亚毫秒漂移而失败。
     */
    private static void scheduleAfter(Transaction tx, KnowledgePoint kp, String result,
                                      int hintsUsed, boolean review, boolean gateCleared,
                                      String source, String evidenceId) {
        double now = TimeSupport.nowSeconds();
        LearningProgress progress = tx.progress();
        if (gateCleared) {
            RepetitionState state = Sm2Scheduler.initialState(
                    source == null || source.isEmpty() ? "system" : source, now);
            progress.getRepetitionStates().put(kp.getId(), state);
            Sm2Scheduler.upsertReviewTask(progress, kp, state, "mastery_path", evidenceId);
            tx.emit("review.scheduled", payload(
                    "knowledge_point_id", kp.getId(),
                    "next_review_at", state.getNextReviewAt(),
                    "interval_index", state.getIntervalIndex(),
                    "provenance", state.getProvenance()));
        } else if (review && progress.getRepetitionStates().containsKey(kp.getId())) {
            RepetitionState state = progress.getRepetitionStates().get(kp.getId());
            Sm2Scheduler.applyReview(state, result, hintsUsed, now);
            Sm2Scheduler.upsertReviewTask(progress, kp, state, "mastery_path", evidenceId);
            tx.emit("review.scheduled", payload(
                    "knowledge_point_id", kp.getId(),
                    "next_review_at", state.getNextReviewAt(),
                    "interval_index", state.getIntervalIndex(),
                    "scheduled_after_failure", state.isScheduledAfterFailure()));
        } else if (progress.getRepetitionStates().containsKey(kp.getId())) {
            // 已掌握目标上的会话内练习：只改时间，锚点保持不变。
            Sm2Scheduler.touchLastReview(progress.getRepetitionStates().get(kp.getId()), now);
        }
    }

    /** 构造保持插入顺序的 payload；键值交替传入。 */
    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    /** Python 的 {@code s[:limit]}：按字符数截断。 */
    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String firstNonEmpty(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return "";
    }

    /** Python 的 {@code round(x, 3)}。 */
    private static double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /** 复习任务的完整 map（{@code get_state} 的 {@code due_reviews} 用）。 */
    private static Map<String, Object> reviewTaskMap(ReviewTask task) {
        return payload(
                "id", task.getId(),
                "knowledge_point_id", task.getKnowledgePointId(),
                "knowledge_type", task.getKnowledgeType() == null
                        ? "" : task.getKnowledgeType().value(),
                "due_at", task.getDueAt(),
                "priority", task.getPriority(),
                "state", task.getState(),
                "forgetting_risk", task.getForgettingRisk(),
                "reason", task.getReason(),
                "evidence_source", task.getEvidenceSource(),
                "evidence_id", task.getEvidenceId());
    }

    /** 来源缺口的序列化（{@code model_dump()} 等价）。 */
    private static List<Map<String, Object>> gapMaps(List<SourceGap> gaps) {
        List<Map<String, Object>> dumped = new ArrayList<>();
        for (SourceGap gap : gaps) {
            dumped.add(payload(
                    "id", gap.getId(),
                    "kind", gap.getKind() == null ? "" : gap.getKind().value(),
                    "ref", gap.getRef(),
                    "reason", gap.getReason(),
                    "candidate_source_ids", gap.getCandidateSourceIds(),
                    "resolution", gap.getResolution() == null
                            ? null : gap.getResolution().value(),
                    "created_at", gap.getCreatedAt()));
        }
        return dumped;
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

    /** 定性评估写入题库的条目：题型固定 {@code written}，题目用知识点名。 */
    private static QuestionBankEntry writtenBankEntry(String pathId, KnowledgePoint kp,
                                                      String evidence, boolean passed) {
        QuestionBankEntry entry = new QuestionBankEntry();
        entry.setEntryId(newEvidenceId());
        entry.setPathId(pathId);
        entry.setKnowledgePointId(kp.getId());
        entry.setKnowledgePointName(kp.getName());
        entry.setQuestion(kp.getName());
        entry.setQuestionType("written");
        entry.setUserAnswer(evidence);
        entry.setCorrect(passed);
        entry.setResult(passed ? "correct" : "partial");
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
