# Proposal: 学习阶段与教学策略

## Why

DeepTutor 的 mastery 导师不是只看「掌握 / 未掌握」：`mastery_status` 为每个知识点返回
`learning_stages`（未覆盖 / 已讲解 / 练习未证明 / 有辅助练习 / 独立证明），导师据此解释
「下一步为什么是这一步」。`next_objective` 还会：

- 先处理到期复习，再推进新目标；
- 对未触碰目标按学习者的 `teaching_strategy` 返回 `teach` 或 `probe`；
- 把被延后的目标保留为回退——延后不是放弃，只剩它们时仍返回其中一个。

当前 Java 版：没有 `learning_stages`；`next` 先新目标后复习；新目标一律 `practice`/`assess`；
被延后的目标被彻底跳过；`NextStep` 缺 `gate` / `forgetting_risk`。本 change 对齐这些语义。

## What Changes

- 新增 `MasteryPolicy.learningStage` / `learningStages`：按作答与证据推导阶段，
  作废作答不计，证据按参考实现原样计入。
- `mastery_status`（工具与 REST）新增 `learning_stages`。
- `NextStep` 增加 `gate`（`qualitative` / `quantitative`）与 `forgetting_risk`。
- `nextObjective` 调整为参考实现优先级：待答 → 到期复习（跳过延后项）→ 第一个可选未掌握目标
  → 只剩延后项时回退 → `complete`。
- 未触碰目标按 `learner_profile.teaching_strategy` 返回 `teach`（`teach_first`）或
  `probe`（缺省）；已有记录后才按类型返回 `assess` / `practice`。
- `answer_pending` 补齐知识点名称、类型、门槛与掌握度。

## Capabilities

### Modified Capabilities

- `learning-domain`: 下一个目标的优先级、教学策略与学习阶段。
- `learning-tools`: `mastery_status` 增加 `learning_stages`，`next` 增加 `gate` / `forgetting_risk`。

## Impact

- Java：`MasteryPolicy`、`NextStep`、`LearningService.getState`、`StatePayload`。
- 测试：`PolicyOrderingTest`、`MasteryPolicyTest`、`PathStateTest`、`ObjectiveStateTest`、
  `StatusAndQuizToolsTest`。
- 行为：有到期复习时 `next` 指向复习；新目标动作变为 `probe`/`teach`；延后目标会回退出现。
