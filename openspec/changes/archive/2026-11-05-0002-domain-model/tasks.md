# Tasks

## 1. 枚举与 JSON 基础设施

- [x] 1.1 `KnowledgeType`（4 值 + 历史中文映射 + `@JsonCreator`）
- [x] 1.2 `ErrorType`（4 值 + 历史中文映射）
- [x] 1.3 `InteractionStatus` / `AssessmentType` / `AssessmentResult` / `GapKind` / `GapResolution`
- [x] 1.4 `JsonSupport`：`SNAKE_CASE` + `FAIL_ON_UNKNOWN_PROPERTIES=false` + 空值照常写出

## 2. 叶子实体

- [x] 2.1 `KnowledgePoint`（name ≥2 校验、`grounding()` 派生）
- [x] 2.2 `LearningModule`（`display_target` 默认 0.7）
- [x] 2.3 `PendingOption` / `PendingQuestion`（旧式选项解析、`choiceMap()`）
- [x] 2.4 `QuizAttempt` / `LearningEvidence`（`voided` 标记）
- [x] 2.5 `RepetitionState` / `ReviewTask`
- [x] 2.6 `LearnerMasteryOverride` / `DeferredObjective` / `SourceGap` / `ObjectiveProposal`
- [x] 2.7 `CoverageGap` / `CoverageReport` / `MasteryInteraction` / `MasteryEvent`
- [x] 2.8 `QuestionBankEntry` / `TopicMetadata` / `TopicSource`

## 3. 聚合根

- [x] 3.1 `LearningProgress`（全字段 + `desiredRetention` 区间校验 + `findKnowledgePoint`）

## 4. 测试（TC-A1…A6）

- [x] 4.1 `KnowledgePointTest`：TC-A1 名称过短（构造 + 反序列化两条路径）、TC-A2 未知类型、TC-A3 中文映射、TC-A4 未知字段
- [x] 4.2 `PendingQuestionTest`：TC-A5 旧式选项字符串 + 位置编号 + 四种分隔符 + 新式对象
- [x] 4.3 `LearningProgressJsonTest`：TC-A6 序列化往返相等（含二次往返稳定性）
- [x] 4.4 `JsonFieldNameTest`：关键字段名为 snake_case
- [x] 4.5 `ReferenceJsonCompatibilityTest`：读取 Python 原样产出的 JSON（数据等价）

## 5. 验证

- [x] 5.1 运行 `.\scripts\verify.ps1 --change 0002-domain-model`
- [x] 5.2 回填 `docs/verify/0002-domain-model.md`
- [x] 5.3 追加 `docs/regression-log.md` 一行
- [x] 5.4 更新 `docs/traceability.md` 中 TC-A1…A6 状态与 `README.md` 看板
