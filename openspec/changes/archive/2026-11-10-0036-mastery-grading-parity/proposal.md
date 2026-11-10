# Proposal: 掌握式判分与掌握度算法对齐 DeepTutor

## Why

DeepTutor 的 mastery path 使用一套明确、可复刻的判分与掌握度算法；当前 Java 移植版增加了
`accepted_answers`、数值容差、`partial`、`ungraded`、提示降级，并使用指数衰减分数。
这些差异改变了「一次答对能否过关」「模糊作答如何处置」等核心行为。本 change 先把
**判分与掌握度计算**逐字对齐 DeepTutor，作为完整复刻 mastery path 的第一批。

## What Changes

- 新增 `MasteryGrading`，逐字复刻 DeepTutor `grade_answer`：
  - `expected_answer` 为空 → 判错（fail-closed）；
  - `choice`：去空格后整串相等；
  - `short`：完全相等，或期望答案长度 ≤30 时 `SequenceMatcher` 相似度 ≥0.85；
  - `open`：期望答案按 `[,;，；。\n]` 拆词，命中率 ≥0.6；
  - 不再使用 `accepted_answers`、数值容差、`partial`、`ungraded`。
- `MasteryScore` 改为 DeepTutor 的近期加权正确率：
  - 最近 5 次权重 `(0.5, 0.7, 0.85, 0.95, 1.0)`；
  - 置信上限：1 次 ≤0.5，2 次 ≤0.8；
  - 只统计 `independent` 且未作废的作答。
- `LearningService.grade` 改用 `MasteryGrading`；移除「提示降级」「开放题 correct→partial」
  与 `ungraded` 分支；提示只影响 `independent`，不再改判分结果。
- 练习题卷（`quiz_sets`）保持自己的判分，不在本 change 范围内。

## Capabilities

### Modified Capabilities

- `learning-tools`: 掌握题判分规则与结果集合变化。
- `learning-domain`: 掌握度分数算法与置信上限变化。

## Impact

- Java：`domain/grading/MasteryGrading`（新增）、`domain/policy/MasteryScore`、
  `service/LearningService`。
- 测试：`GradingTest` 保留给题卷；新增 `MasteryGradingTest`；重写 `MasteryScoreTest`、
  `GradeFlowTest` 及相关服务/API 断言。
- 行为：一次答对不再直接过门槛；无法解析的作答判错而非 `ungraded`；提示不再改变判定结果。
