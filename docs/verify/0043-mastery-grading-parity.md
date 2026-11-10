# 验证报告：0043-mastery-grading-parity

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0043-mastery-grading-parity` |
| 执行时间 | 2026-11-10 17:40–18:10（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Scope all -Change 0043-mastery-grading-parity` |
| 触发来源 | 真实对话中两道简答被判错、导师却说「你答对了」、`repair` 回执称「已纠正」 |

## 2. 变更摘要

- 删除 `MasteryGrading`，掌握路径与练习题卷共用 `Grading.grade(PendingQuestion, String)`：
  `accepted_answers`、选项正文解析、数值容差、0.90 相似度、`ungraded` 全部恢复。
- `LearningService.grade`：`ungraded` 事务外返回，零状态变更；开放题 `correct` 降 `partial`（INV-35）。
- `mastery_grade`：`ungraded` 返回 `ok=false` + `Not graded: … Ask the learner to clarify.`。
- `POST /grade`：只落库 `graded`；`ungraded` 走 422，不写会话消息。
- `repair(correct)`：重判 `ungraded` 时抛前置条件错误；返回体增加 `result`；
  工具回执带真实判定（`… now grades <result>.`）。
- 删除 Java 独有的「简答题必须带 `accepted_answers`」注册约束。

## 3. 问题证据（变更前）

`data/learning.db`，会话 113/115 的两道简答：`data.grade` 均为 `is_correct=false`；
`assessment.corrected` 事件里 `is_correct` 仍为 `false`；`quiz_attempts` 里两条记录 `voided=false`；
`k5` 停在 0.3036。判分器当时不读 `accepted_answers`（注册却强制要求），任何多行解释都无法匹配。

## 4. 验证结果

```
[INFO] Tests run: 936, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

[PASS] 测试通过。
```

| 用例 | 覆盖 |
|---|---|
| `GradingTest#realAnswersKeepPracticeSemantics` | 13 条 fixture 单一期望（练习与掌握同一判分器） |
| `GradeFlowTest#unparseableChoiceIsUngraded` | `zzz` → `ungraded`，无 attempt/证据，题卡仍在 |
| `GradeFlowTest#openCorrectIsDowngradedToPartial` | 开放题判对 → `partial`，掌握度 0 |
| `GradeFlowTest#repeatedGradeAfterIncorrectIsAlreadyGraded` | 幂等仍成立 |
| `RepairQuestionTest#correctionCanStillBeIncorrect` | 可解析但错误的作答重判后仍 `incorrect` |
| `RepairQuestionTest#correctionRefusedWhenStillUngraded` | 新键仍无法判定 → 抛错且无 `assessment.corrected` |
| `RepairQuestionTest#resultHasNineKeys` | 返回值含 `result` |
| `MasteryGradeContractTest#unparseableChoiceIsUngraded` | REST 422 + 零状态变更 |
| `StudyApiTest#unparseableChoiceReturns422WithoutPersisting` | 422 且不写会话消息 |
| `StudyApiTest#naturalLanguageShortIsAllowedWithoutAcceptedAnswers` | 注册约束已移除 |
| `StatusAndQuizToolsTest#unparseableChoiceIsUngraded` | 工具回执 `Not graded: …` |

用例总数 945 → 936：删除 `MasteryGradingTest`（10 个），新增 1 个修复拒判用例。

## 5. 行为变化

| 场景 | 变更前 | 变更后（与 DeepTutor 一致） |
|---|---|---|
| 选择题写选项正文 | `incorrect` | `correct`（正文唯一匹配标签） |
| 无法解析的作答 | `incorrect`，写负证据 | `ungraded`，零状态变更 |
| 简答相似度 | SequenceMatcher ≥0.85 | 编辑距离相似度 ≥0.90 |
| `accepted_answers` | 注册必填、判分忽略 | 注册可选、判分真正使用 |
| 开放题判对 | `correct`，可单独授予掌握 | 服务层降 `partial` |
| 修复后仍无法判定 | 回「已纠正」 | 抛错、不写事件、回执带真实 `result` |
| REST `/grade` 无法解析 | 200 + 落库 `incorrect` | 422 + 不落库 |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 历史 `incorrect` 记录不追溯重判 | 中 | 只影响旧数据；新作答按新规则，必要时手动 `void` |
| D2 | 提示（`hints_used>0`）仍不降级 | 低 | 保留 0036 的既有行为，未纳入本 change |
| D3 | 旧 `voided` 语义与 `repair(correct)` 仍不覆盖「长解释型」题 | 低 | 这类题应改用 `open` / `mastery_assess`；属题型契约问题 |
| D4 | 未重跑真实模型 E2E | 中 | 判分是纯函数，单测与 REST 契约已覆盖；如需端到端可跑 `.\scripts\e2e-mastery.ps1` |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
