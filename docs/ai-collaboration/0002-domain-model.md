# AI 协作留痕：0002-domain-model

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/domain/model/**`（新增 26 个类）、
  `src/test/java/com/masterypath/domain/model/**`（5 个测试类）、
  `src/test/resources/fixtures/reference-progress.json`。
- 明确不做的事：不实现掌握门槛/判分/调度（属 `0004`/`0008`/`0010`）、不实现存储（`0003`）、
  不实现学习者可见投影（`0006`）、不引入 Bean Validation 注解。
- 出处：`openspec/changes/0002-domain-model/proposal.md` §Impact，人类在批准整体计划后授权串行推进。

## 2. AI 产出

- 新增文件：
  - 枚举 7 个：`KnowledgeType`、`ErrorType`、`InteractionStatus`、`AssessmentType`、
    `AssessmentResult`、`GapKind`、`GapResolution`
  - 实体 20 个（含 `LearningProgress` 聚合根）
  - 基础设施 2 个：`JsonSupport`、`TimeSupport`
  - 测试 5 个：`KnowledgePointTest`、`PendingQuestionTest`、`LearningProgressJsonTest`、
    `JsonFieldNameTest`、`ReferenceJsonCompatibilityTest`
  - fixture 1 个：`fixtures/reference-progress.json`（Python 原样产出）
- 关键决策：
  - 全局 `SNAKE_CASE` 命名策略，而非逐字段注解（20 个实体、200+ 字段）。
  - 关闭 `FAIL_ON_UNKNOWN_PROPERTIES`，对齐 `extra="ignore"`。
  - 枚举用 `@JsonCreator` + 历史映射表，实现「未知拒绝、历史映射」。
  - 校验写在构造器与 setter 内（不依赖 Bean Validation 注解），因为 Jackson 反序列化不触发注解校验。
  - 反序列化只走 public setter（`withFieldVisibility(NONE)`），否则 `TC-A1` 会被绕过。

## 3. 人工修正点

- 无人类介入（本 change 全程由 AI 执行，人类尚未审查产出）。
- AI 自我修正（如实记录，均在提交前完成）：
  1. `JsonSupport` 初版用 `withFieldVisibility(ANY)`，会让 Jackson 直接写字段、绕过 setter 校验 → 改为 `NONE`。
  2. `JsonSupport` 初版用 `Include.NON_NULL`，与 Pydantic 默认行为不符，会导致 Python 侧读不到字段 → 改为 `ALWAYS`。
  3. `QuizAttempt` 的 `is_correct` / `voided` / `independent` 会被 Jackson 推断成错误名字 → 加 `@JsonProperty` 显式命名。
  4. `LearningProgress.findKnowledgePoint` 初版返回数组 → 改为 `KnowledgePointLocation` record。
  5. `PackageLayoutTest` 初版用 `Package.getPackage`（Java 9+ 废弃且对无类包不可靠）→ 改为查 `package-info.class` 资源。

## 4. 回归结果

- 用例数 / 失败数：全量 23 / 0（本 change 新增 21 项）。
- 证据链接：[`docs/verify/0002-domain-model.md`](../verify/0002-domain-model.md)
- 日志：`artifacts/verify-0002-domain-model-20261104-122436.log`
- 跨语言证据：fixture 由 `../MasteryPath` 的 `LearningProgress.model_dump_json(indent=2)` 原样产出，
  Java 侧逐字段断言通过。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 空值是否写入 JSON | **写入**（`Include.ALWAYS`） | 参考实现 `model_dump_json()` 默认不排除 None；省略会破坏互读 |
| 布尔字段命名 | 显式 `@JsonProperty` | Python 字段是 `is_correct`/`voided`/`independent`，Jackson 默认推断会漂移 |
| 校验放哪里 | 构造器 + setter | `TC-A1` 要求反序列化路径也拒绝，Bean Validation 注解无法覆盖 |
| 历史中文类型是否映射 | **映射** | `INV-16` + `TC-A3` |
| 未知类型是否兜底 | **拒绝** | `INV-16`；不得用 `@JsonEnumDefaultValue` 静默兜底 |
