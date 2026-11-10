# 验证报告：0041-mastery-turn-evidence

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0041-mastery-turn-evidence` |
| 执行时间 | 2026-11-10 15:38–15:44（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 命令 | `.\scripts\verify.ps1 -Scope all -Change 0041-mastery-turn-evidence` |
| 触发来源 | 真实会话 `3d0m8lwouqy`（path `pmw8vlwsu`）中「卡片没送出去」的重复出现 |

## 2. 变更摘要

- `TurnRunner.historyMessages` 把 `data.card` / `data.grade` 还原成文本证据随历史回放。
- `LearningService.recordTurn` 增加 `grade` 参数；WS 回合把 `tool_result.metadata.mastery_grade`
  写入 assistant 消息的 `data.grade`（补齐 0040 登记的偏差 D1）。
- 回合内事件（`question.registered`、`attempt.graded`、`objective.mastered`、
  `qualitative.assessed`、`question.skipped`、`objective.explained`）携带 `session_id` / `turn_id`。
- 状态快照新增 `last_answered`；`StatePayload` 的 `active` 分支透传。

## 3. 问题证据（变更前）

`data/learning.db`，会话 `3d0m8lwouqy`：

| 时间 | 记录 |
|---|---|
| 15:21:59 | `question.registered` `85039278…`；`messages.data.card` 写给 message 64 |
| 15:22:19 | `attempt.graded` `85039278…` → `is_correct=true`、`mastery_after=0.4625` |
| 15:22:28 | message 65 `user: "C"` + message 66 `assistant: "上一题的卡片没送出去…"` + 新卡 `97548ae2…` |

判分成功、掌握度已更新，但模型在同一回合断言卡片未送达。导出脚本（`target/TraceDump.java`）
同时暴露：所有 `events` 行的 `session_id` / `turn_id` 均为空串。

## 4. 验证结果

```
[INFO] Results:
[INFO] Tests run: 945, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS

[PASS] 测试通过。
```

基线 941 → 945（新增 4 个用例）。

| 用例 | 覆盖 |
|---|---|
| `TurnRunnerTest.historyReplaysCardAndGradeAsText`（新增） | 历史必须同时含 `[Question card delivered to the learner]` 与 `[That card was graded]`，且含题干、选项、作答与结果 |
| `TurnRunnerTest.gradesCardAnswerBeforeTheTutorSpeaks`（扩展） | 判分写入 assistant 消息 `data.grade`（`question_id` / `result`） |
| `PathStateTest.lastAnsweredIsNoneBeforeAnyAnswer`（新增） | 无作答时 `last_answered = {status: "none"}` |
| `PathStateTest.lastAnsweredReplaysTheLatestAttempt`（新增） | 回放 `question_id` / `answer` / `result` / `session_id` / `turn_id` / `mastery_after` |
| `PathStateTest.gradedEventCarriesSessionAndTurn`（新增） | `attempt.graded` 事件带 `s1` / `t1` |

签名变化涉及的既有用例（`recordTurn`、`skipQuestion`、`noteExplained`）全部同步更新并通过。

## 5. 行为变化

| 场景 | 变更前 | 变更后 |
|---|---|---|
| 学习者回一个裸选项 `C` | 模型看到「assistant 说要出题但没有题」+「user: C」，判定卡片未送达并重发 | 历史里带 `[Question card delivered …]`（题干/选项）与 `[That card was graded]`（作答/结果/掌握度） |
| WS 回合的 assistant 消息 | 只有 `data.card` | `data.card` + `data.grade` |
| `events.session_id` / `turn_id` | 恒为空串 | 回合内事件带真实值；路径级事件仍为空串 |
| 状态快照键数 | 20 | 21（新增 `last_answered`） |

## 6. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 本次未重跑真实模型 E2E，未在新会话里复现「不再道歉」 | 中 | 逻辑确定（历史内容由单测锁死）；如需端到端确认可跑 `.\scripts\e2e-mastery.ps1` |
| D2 | 旧会话的历史里没有 `data.grade` | 低 | 只回放卡片；新回合起补齐，不追溯改造历史数据 |
| D3 | `last_answered` 使用 `learningEvidence` 末条未作废记录 | 低 | 作废题被跳过，与门槛判定口径一致 |
| D4 | 路径级事件（`path.created`、`review.scheduled` 等）仍不带回合标识 | 低 | 它们本就没有回合上下文，符合设计 §3 |

## 7. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
