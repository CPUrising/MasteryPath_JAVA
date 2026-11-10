# Tasks

## 1. 学习阶段

- [x] 1.1 `MasteryPolicy.learningStage` / `learningStages`（作废作答不计、证据照原样计入）。
- [x] 1.2 `getState` 与 `StatePayload` 暴露 `learning_stages`。

## 2. 推进与策略

- [x] 2.1 `NextStep` 增加 `gate` / `forgetting_risk`，`answer_pending` 补齐知识点信息。
- [x] 2.2 `nextObjective` 改为：待答 → 到期复习（跳过延后项）→ 第一个可选未掌握 → 延后回退 → complete。
- [x] 2.3 未触碰目标按 `teaching_strategy` 返回 `teach` / `probe`；其余按门槛返回 `assess` / `practice`。

## 3. 验证

- [x] 3.1 新增/更新 `MasteryPolicyTest`、`PolicyOrderingTest`、`PathStateTest`、`ObjectiveStateTest`、`StatusAndQuizToolsTest`。
- [x] 3.2 `.\scripts\verify.ps1 -Change 0039-learning-stages-strategy` 通过，前端 `typecheck` / `build` 通过。
- [x] 3.3 回填 `docs/verify/0039-learning-stages-strategy.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
