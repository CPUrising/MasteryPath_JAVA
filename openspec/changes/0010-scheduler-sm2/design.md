# Design: SM-2 调度

## Context

参考实现 `learning/scheduler.py`（165 行）是**纯算术**：不读库、不发事件、不认识 `LearningProgress`
之外的任何东西。真正把它接进事务的是 `service.py:1157-1202` 的 `_schedule_after`。

本 change 因此有两块：
1. `domain/schedule/Sm2Scheduler` —— 逐函数复刻 `scheduler.py`；
2. `LearningService.scheduleAfter` —— 逐分支复刻 `_schedule_after`，并接到三个既有入口。

## Goals / Non-Goals

**Goals**

1. 间隔序列与参考实现**逐位相同**，包括 `round` 的舍入语义（见 Decisions 1）。
2. 三分支的**互斥性与顺序**与参考实现一致；事件 payload 的**不对称**照抄（见 Decisions 4）。
3. `scheduled_after_failure` 严格跟随锚点（`INV-29/30`）：只有真正设置新锚点的路径才改它。
4. `domain.schedule` 零 I/O、零 Spring，可在无上下文下单测（`AGENTS.md` §4.1）。

**Non-Goals**

1. 不实现 `setReviewSettings`（`desired_retention` 限幅 + 批量回写）→ `0017`。
2. 不实现 `_recompute_after_repair` 的播种分支 → `0012`。
3. 不实现 `getState` 的 `reviews` 数组投影 → `0014`。
4. 不补 `forgetting_risk` / `priority` 的计算公式（参考实现从不赋值，恒 `0.0` / `0`）。
5. 不引入 `scenario` 预设（`04-scheduling.md` §六：一期不做）。

## Decisions

1. **`interval_days` 用 `Math.rint`，不是 `Math.round`**
   - Python 的 `round()` 对 `.5` 走**银行家舍入**（ties-to-even）：`round(2.5) == 2`、`round(37.5) == 38`。
   - Java 的 `Math.round(2.5) == 3`，与参考实现**不一致**；`Math.rint` 才是 ties-to-even。
   - TC-G1 的 1/6/15/38/95 两个函数都能过，所以**只有专门用例能抓住这个差异**。
     用例直接断言 `intervalDays(4, 2.5) == 38` 且 `intervalDays` 在 `x.5` 处向偶数舍入
     （构造 EF 使中间值恰为 `x.5`）。
   - 末尾 `int(days)` 是截断，但 `days` 已是整值，等价于强转。

2. **`applyReview` / `touchLastReview` 就地修改并返回同一对象**
   - 参考实现是 `state.x = ...; return state`（Pydantic 可变模型）。
   - Java 若改成返回新对象，`repetitionStates` 里的旧引用不会被替换——除非调用方显式 `put` 回去。
     就地修改 + 返回 `this` 既保持语义，也让调用方可以链式书写。
   - 副作用：`ReviewTask.state` 与 `repetitionStates[kpId]` 在**内存中共享同一对象**，
     但**落库再读回后是两个独立对象**（JSON 反序列化各建一份）。这是参考实现也有的行为，
     照抄；不要在 `upsertReviewTask` 里做「引用同步」之类的加固。

3. **`initialState` 的 `interval_index` 是 `1`，不是 `0`**
   - `04-scheduling.md` §五 的表格写「初始 `interval_index` = 0」，但**代码是 `1`**。
   - `AGENTS.md` §4.5：以参考实现为准。表格是文档口径，代码是行为口径；本 change 照代码。
   - `first = interval_days(1, DEFAULT_EF) * factor = 1 * factor`，即系统轨 1 天、声明轨 0.5 天。
     比值 0.5 落在 `[0.3, 0.7]`（TC-G5）。

4. **`_schedule_after` 三分支：顺序即语义**
   ```
   if gate_cleared            → 播种（initialState + upsert + emit，含 provenance）
   elif review and 有 state   → 重排（applyReview + upsert + emit，含 scheduled_after_failure）
   elif 有 state              → 只 touch last_review_at（不 upsert、不 emit）
   ```
   - 分支 1 优先于分支 2：即使 `review=true`，首次掌握也走播种（不会对不存在的 state 调 `applyReview`）。
   - 分支 3 存在的前提是「已掌握 + 非 review 模式」= 会话内练习（`INV-29`）。
   - **两套 payload 不对称**：分支 1 有 `provenance` 无 `scheduled_after_failure`；
     分支 2 有 `scheduled_after_failure` 无 `provenance`。这是参考实现原样行为，逐字照抄。
   - `source or "system"`：`masterySource` 可能返回 `""`，`initialState` 的 `provenance`
     只接受 `system` / `learner`，其余归一到 `system`。Java 侧要在 `Sm2Scheduler.initialState`
     内部做这个归一（`RepetitionState.setProvenance` 只处理空串，不处理非法值）。

5. **`now` 在 `scheduleAfter` 开头取一次，两条路径共用**
   - 参考实现：`now = time.time()` 在函数顶部，`initial_state(now=now)` / `apply_review(now=now)`。
   - 若两次各取一次，`next_review_at - last_scheduled_at` 会有亚毫秒漂移，
     TC-G5 的比值断言仍能过，但「首间隔恰为 0.5 天」的严格断言会失败。

6. **`scheduleAfter` 放在 `LearningService` 私有方法，不新建 `ReviewService`**
   - `migration-plan.md` §5 写的 `service/ReviewService` 是**预估**；参考实现 `_schedule_after`
     就是 `service.py` 的模块级私有函数，且被 3 个方法调用、共享 `tx`。
   - 拆类需要把 `tx` 暴露成参数类型，反而扩大可见面。`0009` 的 `assess` 已按同样理由留在
     `LearningService`（见 `0009/design.md` Decisions 1）。

7. **三处接线点必须在「写入之后、清空待答题之前」**
   - `grade`：`gateCleared` / `source` 算完后立刻 `scheduleAfter`，**再** `setPendingQuestion(null)`。
     事件顺序因此是 `review.scheduled` → `attempt.graded` → `objective.mastered`，与参考实现一致。
   - `assess`：同上，`review.scheduled` → `qualitative.assessed` → `objective.mastered`。
   - `overrideMastery`：`review.scheduled` → `mastery.override`。
   - 若放在方法末尾，事件顺序会变——`0015` 的 WS 契约测试会因此失败。

8. **`upsertReviewTask` 先删同 KP 旧项再追加**
   - 参考实现原样：`[e for e in queue if e.knowledge_point_id != kp.id] + [task]`。
   - 语义是「每 KP 至多一条」，且**新项排到队尾**（不是原位替换）。
   - 影响：`dueReviews` 的排序只看 `forgetting_risk` / `due_at`，队尾追加不影响结果；
     但 `getState` 的 `reviews` 数组顺序会变——属 `0014` 的契约，本 change 不动。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `Math.round` 代替 `Math.rint` | 特定 EF 下间隔差 1 天，与参考实现永久漂移 | 用例直接构造 `x.5` 的中间值断言向偶数舍入 |
| 把 `scheduleAfter` 放在方法末尾 | 事件顺序错，`0015` 契约测试失败 | 用例断言三入口的事件**序列**（不只断言「包含」） |
| 分支 1 漏 `source` 归一 | `provenance` 出现 `""`，`[0.3,0.7]` 比值断言崩 | `initialState` 内部归一；用例断言 `""` → `system` |
| 分支 3 误 `upsert` | 会话内练习悄悄刷新 `due_at`，掩盖失败痕迹（`INV-29` 违规） | 用例断言 `next_review_at` 与 `scheduled_after_failure` 均不变、队列长度不变 |
| 两分支 payload 被「统一」 | 与参考实现协议不一致 | `design.md` 明写不对称；用例分别断言键集合 |
| `interval_index` 按文档写 0 | 首次间隔与参考实现差一次序列 | 以代码为准；用例断言 `== 1` |

## Migration Plan

1. `domain/schedule/Sm2Scheduler` + `package-info.java`（纯函数，先单测）。
2. `LearningService.scheduleAfter` + 三处接线 + 删 3 段「已知缺口」Javadoc。
3. 测试：`SchedulerTest`（TC-G1…G5、TC-G7 + 舍入）、`ReviewSchedulingTest`（服务层三分支 + 事件序列）。
4. `verify.ps1 -Scope unit` → 全量 → 与 `pytest tests/test_scheduler.py tests/test_review.py` 对照 → 回填报告。

## Open Questions

- **TC-G6（内部量隐藏）在本层没有可执行面。** 参考实现也没有对应 Python 用例
  （`test_scheduler.py` 只覆盖 G1…G5）。本 change 用**反射**断言 `LearningService` 的公开方法
  签名里不出现 `RepetitionState` / `easeFactor` / `intervalIndex`，作为「不暴露」的最低证据；
  真正的准入拒绝在 `0013`（工具门控）与 `0014`（REST 参数校验）。已在
  `docs/verification-debt.md` 记为部分覆盖，不声称已完整验证。
- **TC-G7（改场景不追溯）一期无场景设置。** 本 change 断言两件事：SM-2 的六个函数都不读
  `desiredRetention`；修改 `LearningProgress.desiredRetention` 不改已有 state 的 `nextReviewAt`。
  端到端的 `PUT /review-settings` 行为属 `0017`。
- 参考实现测试空白（本 change 会补）：
  - `interval_days` 的 `.5` 舍入分支无用例；
  - 分支 3（会话内练习只 touch）在 `test_review.py` 里**没有**集成用例，只有纯函数用例；
  - `assess` 的 `review=true` 失败路径无用例；
  - 三个入口的事件**顺序**无用例（只断言了「包含」）。
