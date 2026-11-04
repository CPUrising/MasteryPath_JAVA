# Tasks

## 1. 纯函数层

- [x] 1.1 新建 `domain/schedule/Sm2Scheduler` + `package-info.java`：5 个常量 + `qualityFromResult` / `updateEase` / `intervalDays`（`Math.rint`）
- [x] 1.2 同类的状态函数：`initialState`（provenance 归一 + `now` 注入）/ `applyReview`（就地改）/ `touchLastReview`
- [x] 1.3 同类的队列函数：`buildReviewTask`（`id = "rev:" + kpId`）/ `upsertReviewTask`（先删同 KP 再追加）

## 2. 服务层接线

- [x] 2.1 `LearningService.scheduleAfter(tx, kp, result, hintsUsed, review, gateCleared, source, evidenceId)`：三分支 + 顶部只取一次 `now`
- [x] 2.2 接入 `overrideMastery`（`source="learner"`）、`grade`、`assess` 的 `gateCleared` 之后；删 3 段「已知缺口」Javadoc

## 3. 测试

- [x] 3.1 `SchedulerTest`：TC-G1 序列、TC-G2 失败重学步、TC-G3 恢复清标志、TC-G4 只 touch、TC-G5 双轨比值、`x.5` 银行家舍入、EF 下限
- [x] 3.2 `ReviewSchedulingTest`（纯函数侧）：TC-G7 两断言（六函数不读 `desiredRetention` + 改 `progress.desiredRetention` 不动已有锚点）、TC-G6 反射断言公开签名不含调度内部量
- [x] 3.3 `ReviewSchedulingTest`（服务侧）：三分支各自的状态与事件、事件**序列**、`source=""` 归一、`review=true` 失败路径、`assess` 未通过不播种

## 4. 验证

- [x] 4.1 `.\scripts\verify.ps1 -Scope unit -Change 0010-scheduler-sm2` → 340 通过
- [x] 4.2 全量 `.\scripts\verify.ps1 -Change 0010-scheduler-sm2` → 409 通过
- [x] 4.3 与参考实现对照（`pytest tests/test_scheduler.py tests/test_review.py -q`）→ 11 passed
- [x] 4.4 回填 `docs/verify/0010-scheduler-sm2.md`、`docs/regression-log.md`、`docs/traceability.md`（TC-G1…G7）、`docs/verification-debt.md`、`docs/ai-collaboration/0010-scheduler-sm2.md`、`README.md`
