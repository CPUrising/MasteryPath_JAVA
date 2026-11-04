# 验证报告：0004-mastery-policy

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0004-mastery-policy` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 13:08:01（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0004-mastery-policy` |
| 日志 | `artifacts/verify-0004-mastery-policy-20261104-130801.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Mastery Gate | TC-C1 量化 0.85 未掌握 | `MasteryPolicyTest#quantitativeBelowGate` | PASS | |
| Mastery Gate | TC-C2 量化 0.90 掌握 | `MasteryPolicyTest#quantitativeAtGate` | PASS | 来源 `system` |
| Mastery Gate | TC-C3 procedure 满分 | `MasteryPolicyTest#procedureFullMastery` | PASS | |
| Mastery Gate | TC-C4 concept 忽略分数 | `MasteryPolicyTest#conceptIgnoresScore` | PASS | `INV-05` |
| Mastery Gate | TC-C5 concept 定性通过 | `MasteryPolicyTest#conceptQualitativePass` | PASS | |
| Mastery Gate | TC-C6 声明来源 learner | `MasteryPolicyTest#overrideMarksLearnerSource` | PASS | |
| Mastery Gate | TC-C7 声明不写分 | `MasteryPolicyTest#overrideDoesNotWriteScore` | PASS | `INV-01` |
| Mastery Gate | TC-C8 声明不产证据 | `MasteryPolicyTest#overrideAddsNoEvidence` | PASS | `INV-15` |
| Frontier Advancement | TC-C9 第一个可选目标 | `MasteryPolicyTest#nextObjectiveFirstEligible` | PASS | |
| Frontier Advancement | TC-C10 全掌握 complete | `MasteryPolicyTest#nextObjectiveComplete` | PASS | |
| Frontier Advancement | TC-C11 待答题优先 | `MasteryPolicyTest#nextObjectiveAnswerPending` | PASS | `INV-02` |
| Mastery Gate | TC-C12 作废不计账 | `MasteryPolicyTest#voidedAttemptIgnored` | PASS | `INV-14` |
| Mastery Gate | TC-C13 三种状态 | `MasteryPolicyTest#objectiveStatusBranches` | PASS | |
| Mastery Gate | TC-C14 map_summary 一致 | `MasteryPolicyTest#mapSummaryMatchesGate` | PASS | |
| Frontier Advancement | TC-C15 deferred 跳过 | `MasteryPolicyTest#deferredSkipped` | PASS | |
| Typed Knowledge Point | TC-C16 类型只读 | `MasteryPolicyTest#typeIsReadOnly` | PASS | `INV-06` |
| Goal Preservation | TC-C17 目标保留 | `GoalPreservationTest#goalSurvivesMutation` | PASS | `INV-13` |
| Frontier Advancement | TC-F1 模块阈值不推进 | `MasteryPolicyTest#displayTargetNotGate` | PASS | `INV-18` |
| Frontier Advancement | TC-F6 前置硬门控 | `MasteryPolicyTest#prerequisiteHardGate` | PASS | `INV-27` |
| Frontier Advancement | TC-F7 前置通过未定性 | `MasteryPolicyTest#prerequisitePassedButNoQualitative` | PASS | |
| Frontier Advancement | TC-F8 两条件相与 | `MasteryPolicyTest#prerequisiteAndQualitative` | PASS | |
| Frontier Advancement | 悬空前置 fail-closed | `MasteryPolicyTest#danglingPrerequisiteFailsClosed` | PASS | 历史数据保护 |
| —（掌握度） | 空/全对/全错 | `MasteryScoreTest` | PASS | 3 项 |
| —（掌握度） | 衰减方向正确 | `MasteryScoreTest#decayDirectionIsNotReversed` | PASS | 方向写反会永远达不到门槛 |
| —（掌握度） | 跳过作废 | `MasteryScoreTest#voidedAttemptsExcluded` | PASS | `INV-14` |
| —（掌握度） | 连续答对跨门槛 | `MasteryScoreTest#sustainedCorrectCrossesGate` | PASS | |
| —（顺序） | 待答题 > 目标 > 复习 > complete | `PolicyOrderingTest` | PASS | 4 个分支各一例 |
| —（顺序） | 模块按 order 升序 | `PolicyOrderingTest#modulesOrderedByOrderField` | PASS | |
| —（顺序） | 复习按风险降序 | `PolicyOrderingTest#dueReviewsOrderedByRisk` | PASS | |

本 change 新增用例：**38 个**（`MasteryPolicyTest` 21 + `MasteryScoreTest` 8 + `PolicyOrderingTest` 7 + `GoalPreservationTest` 2），全部 PASS。
全量 **88 个，0 失败**。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 39, Failures: 0 |
| 全量 | `verify.ps1 -Change 0004-mastery-policy` | BUILD SUCCESS，Tests run: 88, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退 |
| 参考实现对照 | `pytest tests/test_policy.py -q`（在 `../MasteryPath`） | **21 passed**，与 Java 侧 `MasteryPolicyTest` 的 21 个用例数量一致 |

### 与参考实现的对齐方式

Java 的 `MasteryPolicyTest` 逐条对应 Python 的 `tests/test_policy.py`：
同样的 TC 编号、同样的输入构造、同样的断言。两侧独立执行、结果一致。
`MasteryScoreTest` 对应 `learning/mastery.py` 的语义（Python 侧无独立测试文件，故用行为断言固化）。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `verify.ps1` 的 `-Scope unit` 过滤表达式漏掉 `MasteryScoreTest` / `PolicyOrderingTest`，只跑出 21 项 | 一般 | 已补全过滤规则（同时补了后续 change 会用到的类名） |
| D2 | `PolicyOrderingTest` 用了单字符知识点名 `"a"`/`"b"`，被 `0002` 的名称校验拒绝 | 一般 | 改用 `alpha`/`beta`。**这是校验正确生效的证据，不是缺陷** |
| D3 | `JsonSupport` 的 `setVisibility` 仍触发 deprecation 告警 | 一般 | 不影响行为；沿用 `0002` 的登记 |

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 88, Failures: 0` 来自 `artifacts/verify-0004-mastery-policy-20261104-130801.log`；参考实现对照见 `pytest tests/test_policy.py` 的 `21 passed`。
