# Tasks

## 1. 掌握度计算

- [x] 1.1 `MasteryScore.compute(attempts)`：跳过 `voided`，按近期加权（0.6 衰减，最新权重 1.0）

## 2. 返回值类型

- [x] 2.1 `NextStep` record：`action` / 模块与 KP 定位 / `status` / `mastery` / `threshold` / `reason` / `pendingPrompt`
- [x] 2.2 `NextStep.toMap()`：供 REST 序列化（与参考实现字段名一致）

## 3. 门槛与推进（MasteryPolicy）

- [x] 3.1 `gateThreshold(type)`：定量类型 0.9，定性类型 1.0（仅展示）
- [x] 3.2 `achievedEvidence` / `evidenceSatisfied`（`INV-28`，跳过 `voided`）
- [x] 3.3 `isAssessedMastered`：先查附加证据，再按类型分支
- [x] 3.4 `masterySource` / `isMastered`（`INV-01/03`）
- [x] 3.5 `displayMastery` / `objectiveStatus` / `mapSummary`
- [x] 3.6 `eligible`：前置全部已掌握；悬空前置视为不满足（`INV-27`）
- [x] 3.7 `nextObjective`：待答题 → 第一个可选未掌握 → 到期复习 → complete
- [x] 3.8 `activeAttempts`（跳过 `voided`）/ `dueReviews`（风险降序，到期升序）
- [x] 3.9 `requireTypeUnchanged`：类型只读（`INV-06`）

## 4. 测试

- [x] 4.1 `MasteryPolicyTest`：TC-C1…C17、TC-F1、TC-F6/F7/F8
- [x] 4.2 `MasteryScoreTest`：空列表、全对、全错、早期错近期对（进步）、跳过 voided
- [x] 4.3 `PolicyOrderingTest`：`nextObjective` 四个分支的优先级顺序

## 5. 验证

- [x] 5.1 运行 `.\scripts\verify.ps1 -Scope unit --change 0004-mastery-policy`
- [x] 5.2 运行全量 `.\scripts\verify.ps1 --change 0004-mastery-policy`
- [x] 5.3 回填 `docs/verify/0004-mastery-policy.md`
- [x] 5.4 追加 `docs/regression-log.md`；更新 `docs/traceability.md` 与 `README.md`
