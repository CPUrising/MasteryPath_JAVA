# 验证报告：0009-qualitative-assess

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0009-qualitative-assess` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 14:46:41（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0009-qualitative-assess` |
| 日志 | `artifacts/verify-0009-qualitative-assess-20261104-144641.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Qualitative Assessment | TC-M3 concept 通过清门槛 | `QualitativeAssessTest#passClearsGate` | PASS | 来源 `system` |
| Qualitative Assessment | TC-M3 未通过不清门槛 | `QualitativeAssessTest#failDoesNotClearGate` | PASS | |
| Qualitative Assessment | TC-M3 拒绝 memory | `QualitativeAssessTest#refusesQuantitativeObjective` | PASS | 文案逐字 |
| Qualitative Assessment | TC-M3 拒绝时零变更 | `QualitativeAssessTest#refusalLeavesNoTrace` | PASS | |
| Qualitative Assessment | 未知知识点拒绝 | `QualitativeAssessTest#unknownObjectiveRejected` | PASS | |
| Qualitative Assessment | 证据类型 QUALITATIVE | `QualitativeAssessTest#evidenceTypeIsQualitative` | PASS | |
| Qualitative Assessment | 通过记 correct | `QualitativeAssessTest#passRecordsCorrect` | PASS | |
| Qualitative Assessment | 未通过记 partial | `QualitativeAssessTest#failRecordsPartial` | PASS | 不是 incorrect |
| Qualitative Assessment | 多次评估累积证据 | `QualitativeAssessTest#multipleAssessmentsAccumulate` | PASS | |
| Qualitative Assessment | 证据字段最小集 | `QualitativeAssessTest#evidenceFieldsAreMinimal` | PASS | 32 位 hex id |
| Qualitative Assessment | 通过时 quality 固定 1.0 | `QualitativeAssessTest#passIgnoresIncomingQuality` | PASS | 忽略传入 0.5 |
| Qualitative Assessment | 未通过默认 quality 0.2 | `QualitativeAssessTest#failDefaultsToZeroPointTwo` | PASS | |
| Qualitative Assessment | 未通过使用传入 quality | `QualitativeAssessTest#failUsesIncomingQuality` | PASS | |
| Qualitative Assessment | 从不写 masteryLevels | `QualitativeAssessTest#neverWritesMasteryLevels` | PASS | `INV-05` |
| Qualitative Assessment | 写 qualitativeMastery | `QualitativeAssessTest#writesQualitativeMastery` | PASS | |
| Qualitative Assessment | 后一次未通过覆盖标记 | `QualitativeAssessTest#laterFailureOverwritesFlag` | PASS | |
| Qualitative Assessment | 发 qualitative.assessed + objective.mastered | `QualitativeAssessTest#emitsEvents` | PASS | |
| Qualitative Assessment | 未通过不发 mastered | `QualitativeAssessTest#failEmitsNoMastered` | PASS | |
| Qualitative Assessment | 通过后前沿推进 | `QualitativeAssessTest#passAdvancesFrontier` | PASS | |
| Qualitative Assessment | 同 KP 清空待答题 | `QualitativeAssessTest#clearsPendingQuestionOfSamePoint` | PASS | |
| Qualitative Assessment | 异 KP 不清待答题 | `QualitativeAssessTest#keepsPendingQuestionOfOtherPoint` | PASS | |
| Qualitative Assessment | 题库题型 written | `QualitativeAssessTest#writesBankEntry` | PASS | |
| Qualitative Assessment | 未通过题库记 partial | `QualitativeAssessTest#failedBankEntryIsPartial` | PASS | |
| Qualitative Assessment | design 可评估 | `QualitativeAssessTest#designTypeAssessable` | PASS | |
| Qualitative Assessment | procedure 被拒绝 | `QualitativeAssessTest#procedureRefused` | PASS | |

本 change 新增用例：**25 个**（`QualitativeAssessTest`），全部 PASS。
全量 **378 个，0 失败**（353 → 378）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 309, Failures: 0 |
| 全量 | `verify.ps1 -Change 0009-qualitative-assess` | BUILD SUCCESS，Tests run: 378, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0008 的 353 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_assess.py -q`（在 `../MasteryPath`） | **5 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_qualitative_pass_clears_gate` | `QualitativeAssessTest#passClearsGate` + `#evidenceTypeIsQualitative` | 拆为两例 |
| `test_qualitative_fail_does_not_open_gate` | `QualitativeAssessTest#failDoesNotClearGate` | 同断言 |
| `test_assess_refuses_quantitative_objective` | `QualitativeAssessTest#refusesQuantitativeObjective` | 同断言 + 文案逐字 |
| `test_review_refuses_unmastered_assess` | 待 `0013`（工具层 review 校验） | 服务层无 mode 概念 |
| `test_multiple_assessments_allowed_and_evidence_accumulates` | `QualitativeAssessTest#multipleAssessmentsAccumulate` | 同断言 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#passIgnoresIncomingQuality` / `#failDefaultsToZeroPointTwo` / `#failUsesIncomingQuality` | 参考实现无 `quality` 三条分支的用例 |
| `#keepsPendingQuestionOfOtherPoint` | 参考实现无「异 KP 不清待答题」的用例 |
| `#writesBankEntry` / `#failedBankEntryIsPartial` | 参考实现无题库 `written` 类型的断言 |
| `#refusalLeavesNoTrace` | 参考实现无「拒绝时聚合零变更」的用例 |
| `#designTypeAssessable` / `#procedureRefused` | 参考实现只测了 concept / memory，未覆盖 design / procedure |
| `#laterFailureOverwritesFlag` | 参考实现无「后一次未通过覆盖标记」的用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 未按 `migration-plan.md` §5 新建 `service/AssessService`，而是放进 `LearningService` | 一般 | 参考实现就在 `LearningService`（80 行）；拆类会引入无收益的间接层。已在本 change 的 `design.md` §Decisions 1 与留痕中说明 |
| D2 | 参考实现 `_schedule_after` 传 `result="correct" if passed else "incorrect"`，而证据记 `partial` | 一般 | **参考实现的不一致，照抄**：证据用 `partial`（「还没讲清楚」），调度参数用 `incorrect`。调度属 `0010`，届时按此实现 |
| D3 | `gateCleared` 时未播种复习计划、未发 `review.scheduled` | **重要** | 属 `0010`；已登记验证债 |
| D4 | `review` 参数当前只透传，不影响证据类型（参考实现固定写 `QUALITATIVE`） | 一般 | 照抄参考实现；`review` 的实际作用是传给 `_schedule_after`，属 `0010` |
| D5 | 工具层 `mastery_assess` 与 review 模式下拒绝未掌握 KP 的检查未实现 | 一般 | 属 `0013` |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `review.scheduled` 与 SM-2 状态推进 | `0010` | 需调度器 |
| review 模式下拒绝未掌握 KP 的定性评估（工具层） | `0013` | 需工具上下文与 mode 门控 |
| `mastery_assess` 工具本体 | `0013` | 需 `ToolResult` 包装 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 378, Failures: 0` 来自
`artifacts/verify-0009-qualitative-assess-20261104-144641.log`；参考实现对照见
`pytest tests/test_assess.py` 的 `5 passed`。
