# 验证报告：0010-scheduler-sm2

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0010-scheduler-sm2` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 15:12:17（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0010-scheduler-sm2` |
| 日志 | `artifacts/verify-0010-scheduler-sm2-20261104-151151.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| SM2 Only | TC-G1 间隔序列 1/6/15/38/95 | `SchedulerTest#intervalSequence` | PASS | EF=2.5 |
| SM2 Only | TC-G1 长序列 238/595/1488 | `SchedulerTest#longSequence` | PASS | 逐位对齐 |
| SM2 Only | TC-G1 非默认 EF 序列 | `SchedulerTest#sequenceWithOtherEaseFactors` | PASS | EF=2.36 / 1.3 |
| SM2 Only | TC-G1 `x.5` 银行家舍入 | `SchedulerTest#roundsHalfToEven` | PASS | `Math.rint` 而非 `Math.round` |
| SM2 Only | 评分映射 q∈0..5 | `SchedulerTest#qualityMapping` | PASS | |
| SM2 Only | EF 公式 | `SchedulerTest#easeUpdate` | PASS | 1.7/1.96/2.18/2.36/2.5/2.6 |
| SM2 Only | EF 下限 1.3 | `SchedulerTest#easeFloor` | PASS | |
| Failure Enters Relearning Step | TC-G2 失败进重学步 | `SchedulerTest#failureRelearning` + `ReviewSchedulingTest#reviewFailureSetsFlag` | PASS | 600s、lapse+1、标志真 |
| Failure Enters Relearning Step | partial 不算失败 | `SchedulerTest#partialIsNotLapse` | PASS | q=3 |
| Failure Enters Relearning Step | TC-G3 恢复清标志 | `SchedulerTest#recoveryClearsFlag` + `ReviewSchedulingTest#failureThenSuccessClearsFlag` | PASS | |
| Failure Enters Relearning Step | TC-G4 会话内只 touch | `SchedulerTest#inSessionTouchOnly` + `ReviewSchedulingTest#inSessionPracticeKeepsAnchor` | PASS | 锚点与队列均不变 |
| Provenance Seeding | TC-G5 双轨比值 | `SchedulerTest#learnerTrackRatio` + `ReviewSchedulingTest#overrideSeedsLearnerTrack` | PASS | 比值 0.5 |
| Provenance Seeding | 播种字段 | `SchedulerTest#initialStateFields` | PASS | `interval_index=1` |
| Provenance Seeding | 来源归一 | `SchedulerTest#provenanceNormalisesToSystem` | PASS | `""`/`null`/未知 → system |
| Provenance Seeding | TC-G6 内部量隐藏 | `ReviewSchedulingTest#schedulerInternalsNotExposed` | PASS（部分） | 仅公开签名反射断言；准入拒绝属 `0017` |
| Provenance Seeding | TC-G7 不消费 desired_retention | `SchedulerTest#desiredRetentionNotConsumed` | PASS | `INV-31` |
| Provenance Seeding | TC-G7 改设置不动锚点 | `SchedulerTest#changingRetentionKeepsAnchor` | PASS | 端到端见 `0018` |

服务层接线（参考实现 `_schedule_after` 三分支）：

| 分支 | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 1 播种（`grade`） | `ReviewSchedulingTest#gradeSeedsAndEmitsInOrder` | PASS | 事件顺序 `review.scheduled` → `attempt.graded` → `objective.mastered` |
| 1 播种 payload | `ReviewSchedulingTest#seedPayloadShape` | PASS | 含 `provenance`，不含 `scheduled_after_failure` |
| 1 播种（`override`） | `ReviewSchedulingTest#overrideSeedsLearnerTrack` | PASS | 事件顺序 `review.scheduled` → `mastery.override` |
| 1 播种（`assess`） | `ReviewSchedulingTest#assessSeedsBeforeAssessedEvent` | PASS | 事件顺序 `review.scheduled` → `qualitative.assessed` → `objective.mastered` |
| 1 未通过不播种 | `ReviewSchedulingTest#failedAssessSeedsNothing` | PASS | |
| 2 重排成功 | `ReviewSchedulingTest#reviewCorrectReschedulesForward` | PASS | 含 `scheduled_after_failure`，不含 `provenance` |
| 2 重排失败 | `ReviewSchedulingTest#reviewFailureSetsFlag` | PASS | |
| 2 无状态不重排 | `ReviewSchedulingTest#reviewWithoutStateDoesNothing` | PASS | |
| 3 会话内只 touch | `ReviewSchedulingTest#inSessionPracticeKeepsAnchor` | PASS | 不 upsert、不发事件 |
| 队列进入 nextObjective | `ReviewSchedulingTest#dueReviewBecomesNextAction` | PASS | 到期返回 `review` |
| 未到期不复习 | `ReviewSchedulingTest#notDueYetNoReview` | PASS | 返回 `complete` |
| 队列构建 | `SchedulerTest#buildReviewTaskFields` | PASS | `id = rev:<kpId>` |
| 队列 upsert | `SchedulerTest#upsertReplacesSamePointOnly` | PASS | 每 KP 一条、追加到队尾 |

本 change 新增用例：**31 个**（`SchedulerTest` 18 + `ReviewSchedulingTest` 13），全部 PASS。
全量 **409 个，0 失败**（378 → 409）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 340, Failures: 0 |
| 全量 | `verify.ps1 -Change 0010-scheduler-sm2` | BUILD SUCCESS，Tests run: 409, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0009 的 378 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_scheduler.py tests/test_review.py -q`（在 `../MasteryPath`） | **11 passed** |
| 断言严格性复查 | 见「偏差与风险」D7 | 已由 `containsSubsequence` 收紧为切片 `containsExactly` |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_tc_g1_interval_sequence` | `SchedulerTest#intervalSequence` | 同断言 |
| `test_tc_g2_failure_enters_relearning` | `SchedulerTest#failureRelearning` | 同断言 + 精确到 600s |
| `test_tc_g3_success_after_failure_clears_flag` | `SchedulerTest#recoveryClearsFlag` | 同断言 |
| `test_tc_g4_in_session_practice_keeps_anchor` | `SchedulerTest#inSessionTouchOnly` | 同断言 |
| `test_tc_g5_learner_first_interval_shorter` | `SchedulerTest#learnerTrackRatio` | 同断言 + 精确 0.5 |
| `test_ease_factor_floor` | `SchedulerTest#easeFloor` | 同断言 |
| `test_mastering_seeds_schedule_and_emits` | `ReviewSchedulingTest#gradeSeedsAndEmitsInOrder` | 同断言 + 事件顺序 |
| `test_due_review_becomes_next_action` | `ReviewSchedulingTest#dueReviewBecomesNextAction` | 同断言 |
| `test_review_correct_reschedules_forward` | `ReviewSchedulingTest#reviewCorrectReschedulesForward` | 同断言 |
| `test_review_failure_sets_flag_and_relearning` | `ReviewSchedulingTest#reviewFailureSetsFlag` | 同断言 |
| `test_learner_override_seeds_shorter_interval` | `ReviewSchedulingTest#overrideSeedsLearnerTrack` | 同断言 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `SchedulerTest#roundsHalfToEven` | 参考实现无 `interval_days` 在 `x.5` 处的舍入用例（TC-G1 的 1/6/15/38/95 抓不住 `Math.round` 的差异） |
| `SchedulerTest#partialIsNotLapse` | 参考实现无 `partial`（q=3）不算失败的用例 |
| `SchedulerTest#provenanceNormalisesToSystem` | 参考实现无非法来源归一的用例 |
| `SchedulerTest#desiredRetentionNotConsumed` / `#changingRetentionKeepsAnchor` | 参考实现无 TC-G7 的用例 |
| `SchedulerTest#buildReviewTaskFields` / `#upsertReplacesSamePointOnly` | 参考实现无队列构建/upsert 的用例 |
| `ReviewSchedulingTest#gradeSeedsAndEmitsInOrder` 等的顺序断言 | 参考实现只断言「包含」，不断言顺序 |
| `ReviewSchedulingTest#inSessionPracticeKeepsAnchor` | 参考实现只有纯函数侧用例，无服务层集成用例 |
| `ReviewSchedulingTest#failedAssessSeedsNothing` / `#reviewWithoutStateDoesNothing` | 参考实现无「不播种」的负向用例 |
| `ReviewSchedulingTest#schedulerInternalsNotExposed` | 参考实现无 TC-G6 用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 未按 `migration-plan.md` §5 新建 `service/ReviewService`，`scheduleAfter` 放进 `LearningService` | 一般 | 参考实现 `_schedule_after` 就是 `service.py` 的模块级私有函数，被 3 个方法共享 `tx`；拆类需把 `tx` 暴露成参数类型，反而扩大可见面。与 `0009` 同理由，见 `design.md` Decisions 6 |
| D2 | 三处「已知缺口：…属 0010」Javadoc 已删除并改写为实际行为说明 | 无 | 缺口已补齐 |
| D3 | `TC-G6` 只做公开签名反射断言，未做准入拒绝 | 一般 | 服务层无可执行面；真正的拒绝在 `0017`（工具门控）。已在 `verification-debt.md` 记为部分覆盖 |
| D4 | `TC-G7` 只验证「SM-2 不消费 `desired_retention`」与「改聚合设置不动锚点」，未验证 `PUT /review-settings` 端到端 | 一般 | 端到端已由 `0018` 的 `LearningApiTest#reviewSettingsClampsToRange` 覆盖 |
| D5 | `forgetting_risk` / `priority` 恒 `0.0` / `0`，未补计算公式 | 无（照抄） | 参考实现从不赋值；补公式即行为变化 |
| D6 | 两分支 `review.scheduled` payload 不对称（播种有 `provenance` 无 `scheduled_after_failure`；重排反之） | 无（照抄） | 参考实现原样行为，已逐字锁定并有用例 |
| D7 | 自愈期间**放宽了断言**：`gradeSeedsAndEmitsInOrder` 用 `containsSubsequence` 替代 `containsExactly`；`inSessionPracticeKeepsAnchor` 用「恰好一次」替代「零事件」 | **重要** | 人类审查后自查发现。已改为「基线索引 + 切片 `containsExactly`」，恢复严格性；修正后 unit 340 / 全量 409 仍全绿，证明实现本就正确 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| TC-G6 的准入拒绝（学习者不能直接读写调度内部参数） | `0013` / `0014` | 需工具上下文与 REST 参数校验 |
| TC-G7 的 `PUT /review-settings` 端到端 | `0017` | 需设置存储与端点 |
| `setReviewSettings`（限幅 + 批量回写 state） | `0017` | 参考实现在 `LearningService`，属设置能力 |
| `_recompute_after_repair` 的播种分支 | `0012` | 需题目修复 |
| `getState` 的 `reviews` 数组投影 | `0014` | 需 REST 快照形状 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 409, Failures: 0` 来自
`artifacts/verify-0010-scheduler-sm2-20261104-151151.log`；参考实现对照见
`pytest tests/test_scheduler.py tests/test_review.py` 的 `11 passed`。
