# quiz-viewer Specification

## Purpose
定义生成型练习题卷的前端契约：能力入口、`quiz_plan` / `quiz_question` / `quiz_set` 事件渲染、整卷提交、`ungraded` 重答、提交后复盘与刷新恢复，并确保提交前不暴露任何答案字段。

## Requirements

### Requirement: Practice Entry

掌握之路会话页 SHALL 提供能力选择，允许学习者在「掌握辅导」与「练习题」之间切换。
选择「练习题」后发起的回合 MUST 带 `capability="deep_question"` 与 `num_questions`，且回合结束后 MUST 回到「掌握辅导」。

#### Scenario: Learner starts a practice turn

- **GIVEN** 学习者在掌握之路会话页
- **WHEN** 选择「练习题」并发送消息
- **THEN** 前端 MUST 以 `capability="deep_question"` 发起回合，默认 `num_questions=3`

#### Scenario: Capability resets after the turn

- **GIVEN** 一次练习题回合已结束
- **WHEN** 回合收到 `turn_end`
- **THEN** 前端 MUST 把能力切回「掌握辅导」

### Requirement: Render Quiz Events

前端 SHALL 消费 `quiz_plan`、`quiz_question`、`quiz_set` 事件并在消息流内联渲染题卷。
同一 `set_id` 的重复 `quiz_question` MUST 按 `question_id` 去重。

#### Scenario: Quiz set renders inline

- **GIVEN** 一次 `deep_question` 回合依次收到 `quiz_plan`、3 条 `quiz_question`、`quiz_set`
- **WHEN** 事件流结束
- **THEN** 消息流 MUST 只出现一个题卷，含 3 道题且无重复题卡

#### Scenario: Generation failure leaves no quiz

- **GIVEN** 回合只收到 `error` 与 `turn_end{reason:"error"}`
- **WHEN** 消息流结束
- **THEN** 前端 MUST 显示错误文本且 MUST NOT 渲染任何题卷

### Requirement: Submit And Review

`QuizViewer` SHALL 收集逐题作答并整卷提交。提交前 MUST 只使用公开字段；
提交成功后 MUST 展示逐题判定、正确答案、解析与总分。`ungraded` 时 MUST 提示原因并保留作答。

#### Scenario: Successful submit reveals answers

- **GIVEN** 一套 3 题且全部作答
- **WHEN** 提交成功
- **THEN** 前端 MUST 展示每题 `is_correct`、`correct_answer`、`explanation` 与总分

#### Scenario: Ungraded keeps answers editable

- **GIVEN** 提交返回 `status="ungraded"`
- **WHEN** 前端渲染响应
- **THEN** 前端 MUST 展示未判定原因，且学习者 MUST 能修改作答后重交

### Requirement: Restore From History

刷新页面后，带 `messages.data.quiz_set` 的会话 MUST 恢复题卷；已提交题卷 MUST 通过
`GET /learning/paths/{pathId}/quiz-sets/{setId}` 拉取答案复盘。

#### Scenario: Reload restores an open quiz

- **GIVEN** 会话中存在一条 `data.quiz_set` 且 `status="open"`
- **WHEN** 学习者刷新页面
- **THEN** 前端 MUST 重新渲染该题卷并保留作答与提交入口

#### Scenario: Reload restores a submitted quiz

- **GIVEN** 会话中的题卷已在服务端提交
- **WHEN** 学习者刷新页面
- **THEN** 前端 MUST 渲染复盘视图，含逐题答案与解析
