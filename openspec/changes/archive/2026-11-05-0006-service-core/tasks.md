# Tasks

## 1. 学习者可见投影

- [x] 1.1 `QuestionCardProjector.publicPendingQuestion(PendingQuestion)`：7 个白名单字段
- [x] 1.2 断言不含 `expected_answer` / `explanation` / `accepted_answers` / `required_keywords` / `optional_keywords` / `created_at`

## 2. 路径删除（存储层）

- [x] 2.1 `LearningStore.deletePath(String)`：单事务级联 messages → sessions → question_bank → interactions → paths
- [x] 2.2 返回 `rowcount > 0`；**不删 events**（append-only）

## 3. LearningService：路径生命周期

- [x] 3.1 `createPath(pathId, goal, name)`：已存在抛 `PreconditionFailedException("path already exists: ...")`；发 `path.created`
- [x] 3.2 `rename(pathId, name)`：发 `path.renamed`，返回 `{status: "renamed", name}`
- [x] 3.3 `delete(pathId)`：转发 `store.deletePath`

## 4. LearningService：来源附加

- [x] 4.1 `attachSources(pathId, sources)`：整体替换；发 `sources.attached`，返回全量
- [x] 4.2 `setKnowledgeBaseSources(pathId, sources)`：只替换 `kind == "knowledge_base"`；发 `sources.knowledge_bases`

## 5. LearningService：目标状态

- [x] 5.1 `overrideMastery(pathId, kpId, note)`：写 override → 算 `gateCleared`；不写分、不产证据；发 `mastery.override`
- [x] 5.2 `deferObjective(pathId, kpId, note)`：写 `deferredObjectives`；发 `objective.deferred`（payload 不含 note）
- [x] 5.3 `noteExplained(pathId, kpId, summary)`：写 `explainedObjectives`；发 `objective.explained`；返回 `mastery_credit: false`

## 6. 测试

- [x] 6.1 `QuestionCardProjectorTest`：TC-E4 投影隐藏答案 + 字段集合精确
- [x] 6.2 `PathLifecycleTest`：创建/重复创建/改名/删除
- [x] 6.3 `SourceAttachmentTest`：整体替换 vs 只替换 KB
- [x] 6.4 `MasteryOverrideServiceTest`：TC-C6/C7/C8 + `gate_cleared` 顺序
- [x] 6.5 `ObjectiveStateTest`：defer / noteExplained 的事件与返回值
- [x] 6.6 `PathDeletionTest`：级联范围 + events 保留

## 7. 验证

- [x] 7.1 运行 `.\scripts\verify.ps1 -Scope unit --change 0006-service-core`
- [x] 7.2 运行全量 `.\scripts\verify.ps1 --change 0006-service-core`
- [x] 7.3 与参考实现对照（`pytest tests/test_policy.py tests/test_interactions.py tests/test_path_events.py tests/test_preferences.py -q`）
- [x] 7.4 回填 `docs/verify/0006-service-core.md`、`docs/regression-log.md`、`docs/traceability.md`、`README.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0006-service-core.md`
