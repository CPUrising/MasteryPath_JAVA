# Proposal: SM-2 调度

## Why

`0006` / `0008` / `0009` 都已经算出了 `gateCleared`，但**只把它当作返回值**：
掌握之后没有任何复习锚点、没有复习队列条目、也不发 `review.scheduled`。
结果是掌握过的知识点**永远不会再被复习**——`MasteryPolicy.dueReviews` 读的
`reviewQueue` 恒为空，`nextObjective` 的第三优先级（到期复习）成为死代码。

参考实现 `learning/scheduler.py` 就是补齐这段：掌握即播种、复习即重排、练习只 touch。

## What Changes

- 新增 `domain/schedule/Sm2Scheduler`：纯算术，零 I/O、零 Spring
  - 常量：`SECONDS_PER_DAY` / `RELEARNING_SECONDS` / `DEFAULT_EF` / `MIN_EF` / `LEARNER_FIRST_FACTOR`
  - 函数：`qualityFromResult` / `updateEase` / `intervalDays` / `initialState` /
    `applyReview` / `touchLastReview` / `buildReviewTask` / `upsertReviewTask`
- 新增 `LearningService` 私有方法 `scheduleAfter(...)`，复刻参考实现 `_schedule_after` 的三分支
- 把 `scheduleAfter` 接入三个已有入口的 `gateCleared` 之后：
  `overrideMastery`（`0006`）/ `grade`（`0008`）/ `assess`（`0009`）
- 删除上述三处 Javadoc 里的「已知缺口：…属 `0010`」段落

## Capabilities

### New Capabilities

无新增能力；实现 `specs/review-scheduling` 的「SM2 Only」「Failure Enters Relearning Step」
「Provenance Seeding」三需求的全部场景（TC-G1…G5、TC-G7）。

### Modified Capabilities

无。本 change 只补齐参考实现已有、而 `0006`–`0009` 刻意推迟的行为；
`skip_specs: true` 即为此意（不产生增量规范）。

## Impact

- 影响文件：
  - 新增 `src/main/java/com/masterypath/domain/schedule/Sm2Scheduler.java` + `package-info.java`
  - 修改 `src/main/java/com/masterypath/service/LearningService.java`（1 个私有方法 + 3 处调用点 + 删 3 段 Javadoc）
  - `scripts/verify.ps1`（`-Scope unit` 已含 `*SchedulerTest`，无需改动；仅确认）
- 影响测试：既有 378 个用例**不修改**。新增 `SchedulerTest`（纯函数）与 `ReviewSchedulingTest`（服务层）。
- 明确不做（Out of scope）：
  - `TC-G6`（学习者不能直接读写调度内部参数）是 **REST/工具层**的职责，
    本 change 在服务层无可执行面；属 `0013`（工具）与 `0014`（REST）。见 `design.md` §Open Questions。
  - `forgetting_risk` 的计算公式参考实现**从不赋值**（恒 `0.0`），`priority` 同理恒 `0`。
    本 change 照抄，不补公式。
  - `setReviewSettings`（`desired_retention` 限幅 + 批量写 state）→ 属 `0017`。
  - `repair_question` 的 `_recompute_after_repair`（含播种分支）→ 属 `0012`。
  - `getState` 的 `reviews` 数组形状 → 属 `0014`。
