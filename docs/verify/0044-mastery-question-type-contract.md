# 验证报告：0044-mastery-question-type-contract

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0044-mastery-question-type-contract` |
| 执行时间 | 2026-11-10 18:20–18:45（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Scope all -Change 0044-mastery-question-type-contract` |
| 触发来源 | `0043` 偏差 D3：长解释型作答被 `short` 判分器一律判错 |

## 2. 变更摘要

- `mastery_quiz` 注册校验：`question_type=short` 且 `Grading.normalize(expected_answer)` 超过 30 字时，
  必须至少有一条归一化后 ≤30 字的 `accepted_answers`；否则拒绝，并提示改用
  `question_type=open`（关键词）或补充短等价答案。
- `mastery_quiz` 工具描述补一句：`short` 仅用于 ≤30 字的短答，解释/多行答案用 `open` 或 `mastery_assess`。
- `choice` / `open` 不受影响；已在库里的历史题目不受影响（校验只在注册期）。

## 3. 问题证据（变更前）

判分器（`0043` 之后）对 >30 字的 `short` 只做精确匹配。真实对话里学习者写出完整的
`CREATE DATABASE` / `USE` / `SOURCE` 三步并附解释，与 125 字的期望答案不相等 →
`incorrect`，掌握度被扣。出题侧从未约束题型与答案形态。

## 4. 验证结果

```
[INFO] Tests run: 941, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

[PASS] 测试通过。
```

基线 936 → 941（新增 5 个用例）。

| 用例 | 覆盖 |
|---|---|
| `StatusAndQuizToolsTest#longShortWithoutAcceptedIsRefused` | >30 字 short 无短等价答案 → `ok=false`，文案含 `question_type=open` 与 `accepted_answers`，且不建题 |
| `StatusAndQuizToolsTest#longShortWithShortAcceptedIsAllowed` | 带 ≤30 字 `accepted_answers` → `registered` |
| `StatusAndQuizToolsTest#longOpenIsAllowed` | 长答案 `open` → `registered` |
| `StudyApiTest#longShortWithoutAcceptedAnswersIs422` | REST 422 |
| `StudyApiTest#longShortWithShortAcceptedAnswersIsRegistered` | REST 200 + `status=registered` |

## 5. 行为变化

| 场景 | 变更前 | 变更后 |
|---|---|---|
| 长解释型 `short`（无短等价答案） | 注册成功，学习者答完必被判错 | 注册被拒，模型收到改用 `open` / 补 `accepted_answers` 的提示 |
| 长 `short` + 短等价答案 | 可注册 | 可注册（判分时先比等价答案） |
| 长答案 `open` | 可注册 | 可注册（不变） |
| `choice` | 可注册 | 可注册（不变） |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 不做题干语义检测（「说明」「解释」等字样） | 低 | 只用长度这一确定性信号，避免误判；提示词另行引导 |
| D2 | 题库中已有的长 `short` 题不会被追溯拦截 | 低 | 需要时由 `mastery_repair_question` 作废重出 |
| D3 | `QuizSetGenerator`（练习题卷）仍有自己的 `accepted_answers` 规则 | 低 | 与本 change 的掌握题契约无关，未改动 |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
