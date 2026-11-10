# Design: 回合证据回放

## 1. 历史回放

`TurnRunner.historyMessages()` 仍以 20 条为上限，但 assistant 消息的正文改为：

```
content + cardEvidence(data) + gradeEvidence(data)
```

- `cardEvidence`：`data.card` 存在时追加 `[Question card delivered to the learner]`，
  含 `question_id`、`question_type`、`prompt`、`options`（`A) … | B) …`）。
- `gradeEvidence`：`data.grade` 存在时追加 `[That card was graded]`，
  含 `question_id`、`learner_answer`、`result`、`is_correct`、`mastery_after`。

两条证据都只用学习者可见投影：`data.card` 来自 `QuestionCardProjector`，不含
`expected_answer` / `explanation`（`INV-07`）。

正文为空但带证据的消息不再被「跳过空正文」规则丢弃；仍为空的才跳过。

## 2. 判分落库

`LearningService.recordTurn` 增加 `Map<String, Object> grade` 参数：

```
data = { card?: …, grade?: … }
assistant 消息 data = data.isEmpty() ? null : data
```

`TurnRunner.persistMasteryTurn` 从 `produced` 里的 `tool_result.metadata.mastery_grade` 取结果
（与 `data.card` 同源，后者取 `metadata.mastery_question_card`）。判分发生在回合开头
（`applyCardSubmission`），因此它和本轮 assistant 正文落在同一条消息上。

这与 REST 题卡路径的 `recordGradeInteraction`（`data.grade`）保持一致，只是不再写
`content = result` 这种占位正文。

## 3. 事件归因

`Transaction.emit(type, payload, sessionId, turnId)` 早已存在，缺的是调用方传参。本 change 只在
回合内会产生的事件上透传：

| 事件 | 来源 |
|---|---|
| `question.registered` | `registerQuestion`（已有 `sessionId` / `turnId` 参数） |
| `attempt.graded`、`objective.mastered` | `grade` |
| `qualitative.assessed`、`objective.mastered` | `assess` |
| `question.skipped` | `skipQuestion`（新增两个参数） |
| `objective.explained` | `noteExplained`（新增两个参数） |

路径级事件（`path.created`、`outline.built`、`review.scheduled` 等）保持空串——它们没有回合上下文。

## 4. last_answered

`getState` 从 `learningEvidence` 反向找第一条未作废且带题号的证据，再用 `quizAttempts`
回查作答原文：

```
{ status: "answered", question_id, knowledge_point_id, result, assessment_type,
  hints_used, answer, mastery_after, session_id, turn_id }
```

从未作答返回 `{status: "none"}`。`StatePayload` 在 `active` 分支透传，`empty` 分支给 `none`。

`mastery_after` 直接读 `masteryLevels`，与 `attempt.graded` 的 `mastery_after` 同值。

## 5. 兼容与风险

- 历史消息变长：每条卡片 + 选项，最多 20 条消息，量级可控。
- 旧会话里没有 `data.grade`，回放只显示卡片；新回合开始后补齐。
- `last_answered` 会让 `getState` 多一个键（20 → 21），依赖 `containsOnlyKeys` 的测试同步更新。
- `recordTurn` / `skipQuestion` / `noteExplained` 签名变化只影响本仓库调用点（工具层与测试）。

## 6. 测试

- `TurnRunnerTest.historyReplaysCardAndGradeAsText`：历史里必须同时出现卡片与判分两段证据。
- `TurnRunnerTest.gradesCardAnswerBeforeTheTutorSpeaks`：判分写入 `data.grade`。
- `PathStateTest`：`last_answered` 的 `none` / `answered` 两态；`attempt.graded` 事件带
  `session_id` / `turn_id`。
