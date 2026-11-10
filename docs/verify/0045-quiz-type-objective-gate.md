# 验证报告：0045-quiz-type-objective-gate

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0045-quiz-type-objective-gate` |
| 执行时间 | 2026-11-10 20:16–20:19（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链 | JDK 21.0.11 / Maven Wrapper / Spring Boot 3.5.16 |
| 命令 | `.\scripts\verify.ps1 -Scope all -Change 0045-quiz-type-objective-gate` |
| 日志 | `artifacts/verify-0045-quiz-type-objective-gate-20261110-201708.log` |
| 结果包 | `target/surefire-reports/` |
| 触发来源 | 真实路径 `pmw8vlwsu`：`k5`（procedure）连续两条 open 正确答案被记为 partial，掌握度 0.6607 → 0.4714 |

## 2. 变更摘要

- `mastery_quiz` 注册期门控：目标门槛为 `quantitative`（`memory` / `procedure`）时拒绝
  `question_type=open`，不登记 interaction、不生成题卡。
- `mastery_quiz` 工具描述与 `question_type` schema 描述说明「open 判对也只记 partial，不产生掌握度」。
- `mastery_repair_question(action=correct)` 返回体与 interaction 结果新增 `effective` / `credit_note`；
  open 题改键判对时 `effective=false`，工具回执不再暗示掌握度已恢复。
- 未修改 `Grading`、`MasteryScore`、权重与置信上限、`INV-35`；未修改提示词包（`mastery-prompts`
  要求逐字来自 DeepTutor）。

## 3. 问题证据（变更前）

`data/learning.db` 中 `k5`（`procedure`）的有效作答：

| 题 | 题型 | 结果 | is_correct | 计入 |
|---|---|---|---|---|
| `ad2a…` | choice | correct | true | 1 |
| `03ae…` | open | partial | false | 0 |
| `d367…` | choice | correct | true | 1 |
| `524e…` | open（答案键已修正） | partial | false | 0 |

按 `MasteryScore` 的最近 4 次权重（0.7 / 0.85 / 0.95 / 1.0）：
`(0.7×1 + 0.85×0 + 0.95×1 + 1.0×0) / 3.5 = 0.471428…`，与库中值一致。
`524e…` 修键前掌握度为 0.6607，修键后降到 0.4714——答对反而扣分。

## 4. 验证结果

```
[INFO] Tests run: 944, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

[PASS] 测试通过。
```

基线 941 → 944（新增 3 个用例；`longOpenIsAllowed` 由 memory 目标改为 concept 目标）。

| Requirement | Scenario | 测试方法 | 结果 |
|---|---|---|---|
| Quiz Type Matches The Objective Gate | Open is refused on a memory objective | `StatusAndQuizToolsTest#openRefusedOnMemoryObjective` | PASS |
| Quiz Type Matches The Objective Gate | Open is refused on a procedure objective | `StatusAndQuizToolsTest#procedureObjectiveRefusesOpenButAcceptsChoice` | PASS |
| Quiz Type Matches The Objective Gate | Choice and short remain available on quantitative objectives | `StatusAndQuizToolsTest#procedureObjectiveRefusesOpenButAcceptsChoice`、`#longShortWithShortAcceptedIsAllowed` | PASS |
| Quiz Type Matches The Objective Gate | Open remains available on qualitative objectives | `StatusAndQuizToolsTest#longOpenIsAllowed` | PASS |
| Repair Recomputes Instead Of Deleting | Open repair reports no credit change | `RepairQuestionTest#openCorrectionReportsNoCreditChange` | PASS |
| Repair Recomputes Instead Of Deleting | Corrected key is reported truthfully | `RepairQuestionTest#resultHasStableKeys`、`#correctionCanStillBeIncorrect` | PASS |

## 5. 运维数据修复

通过 `LearningService.repairQuestion(action=void)` 执行，保留 `version` 递增与
`assessment.voided` 事件；未直接改写 SQLite。

| question_id | 处理 | 原因 |
|---|---|---|
| `03ae518697fd4631a9ed8f96453cdba4` | void | open 题误用于 procedure 目标，partial 不代表理解程度 |
| `524e64ef9aa94fc29b9de9864a52c603` | void | 同上；答案键修正后仍只能是 partial |
| `81a23491d8c1444da862e74c92ee5a0f` | void | 待答 open 题与定量门槛不匹配，改出 choice |

执行后：

- `k5` 有效作答只剩两条 `choice` 正确 → 掌握度 `0.8`（置信上限，仍低于 0.9）。
- 已登记新的 `choice` 题 `82ae20d29a7448e38c1f6e713bc2f989`（答案键 `B`，
  `dept <> '销售' OR status <> '在职'`），作为 `k5` 的当前待答题。

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 门控只在 `mastery_quiz` 工具层；直接调用 `LearningService.registerQuestion` 仍可注册 open | 低 | 与 `0044` 的注册契约一致；历史数据与测试夹具依赖该入口 |
| D2 | 练习题卷（`QuizSetGenerator`）不受此门控 | 低 | 题卷不写掌握度，与本 change 无关 |
| D3 | 未修改 `mastery_loop.yaml` | 低 | `mastery-prompts` 要求提示词包逐字来自 DeepTutor；约束放在工具描述与工具回执 |
| D4 | 题库中已有的 open 题不在注册期被追溯拦截 | 低 | 由 `repair` 作废或改键；本次已处理当前路径的 3 条 |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
