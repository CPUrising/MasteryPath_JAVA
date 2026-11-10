# Design: 学习阶段与教学策略

## 1. 学习阶段

`MasteryPolicy.learningStage(progress, kpId)` 逐字复刻参考实现 `learning_stage`：

```
attempts = 该 KP 未作废的作答
evidence = 该 KP 的全部学习证据（含作废，与参考实现一致）
1. 任一独立且正确的作答，或任一无提示的 qualitative/correct 证据 → independently_demonstrated
2. 任一非独立作答，或任一 hints_used > 0 的证据            → practiced_with_help
3. attempts 或 evidence 非空                              → practiced_not_demonstrated
4. 该 KP 在 explainedObjectives 中                         → explained
5. 否则                                                    → not_yet_covered
```

`learningStages(progress)` 按模块 `order` 与 KP 列表顺序产出 `kp_id → stage`。

投影位置：

- REST：`LearningService.getState` 增加 `learning_stages`；
- 工具：`StatePayload` 在 `status=active` 时透传 `learning_stages`。

## 2. NextStep 扩展

新增两个字段（`toMap` 用 snake_case）：

- `gate`：`qualitative` | `quantitative`（`gateKind(kp)`）；
- `forgetting_risk`：复习步骤取 `ReviewTask.forgettingRisk`，其余为 0。

`answer_pending` 在能找到知识点时补齐 module / name / type / status / gate / mastery /
threshold；找不到时退回最小形状。

## 3. nextObjective 优先级

```
1. 待答题                                  → answer_pending
2. 未延后的到期复习（按遗忘风险降序）        → review
3. 第一个「未掌握 + 前置满足」的目标：
   - status == new → teaching_strategy == teach_first ? teach : probe
   - 其它          → qualitative ? assess : practice
   延后目标不立即返回，但记为 fallback
4. 只剩延后目标                            → 返回记录的第一个 fallback
5. 否则                                    → complete
```

`teachingStrategy(progress)` 读 `learner_profile.teaching_strategy`，只有精确等于
`teach_first` 才生效，其余一律 `probe_first`。

## 4. 兼容与风险

- 延后目标从「彻底消失」变为「无其它目标时回退」，单目标路径上 `next` 不再返回 `complete`。
- 有到期复习时 `next` 指向复习，不再先推进新目标。
- 新目标动作由 `practice`/`assess` 变为 `probe`（或 `teach`），前端只对 `review` 特判，无需改动。

## 5. 测试

- `MasteryPolicyTest`：五个阶段的判定、作废作答不计、`learningStages` 覆盖全部目标。
- `PolicyOrderingTest`：复习优先、`probe`/`teach`、`practice`/`assess`、延后回退、延后不复习。
- `PathStateTest` / `StatusAndQuizToolsTest`：`learning_stages` 键与取值。
- `ObjectiveStateTest`：单目标延后后回退到该目标。
