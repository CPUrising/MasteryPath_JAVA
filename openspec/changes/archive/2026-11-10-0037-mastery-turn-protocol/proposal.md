# Proposal: 题卡与回合协议对齐 DeepTutor

## Why

DeepTutor 的 mastery 回合协议是「出题即收尾、作答作为下一条消息」：学习者提交答案时，
运行时在导师开口前完成确定性判分，并把判分结果作为 `mastery_grade` 工具事件发出；
跳过题目同样是一条消息。当前 Java 版由前端单独调用 `POST /grade`，判分后不再进入导师回合，
题卡也不会在卡内显示判定。本 change 复刻这条协议。

## What Changes

- WS 请求新增 `mastery_answer`（`question_id` + `text`）与 `mastery_skip`（`question_id`）。
- `MasteryTurn` 在启动 LLM 前先判分/跳过，并把结果作为 `tool_result` 事件发出，
  metadata 键为 `mastery_grade` / `mastery_skip`。
- 作答文本同时作为本轮 user 消息；导师回合照常流式回复。
- 回合消息包含会话历史（只取 role/content），让导师看到此前的教学上下文。
- 选择题选项在注册时打乱并重新按位置贴标签，`expected_answer` 跟随正确选项正文。
- 题卡增加 `allow_free_text=true`；判分结果增加 `correct_label` / `correct_body` /
  `learner_answer` / `independent`。
- `mastery_grade.question_id` 变为可选（缺省取当前待答题）；`mastery_skip_question` 无参数。
- 前端作答/跳过改为 WS 消息，不再调用 `POST /grade`。

## Capabilities

### Modified Capabilities

- `agent-turn`: 新增作答/跳过回合与历史注入。
- `learning-tools`: 题卡选项乱序、判分结果字段、可选题号与无参跳过。

## Impact

- Java：`TurnRequest`、`WsTurnHandler`、`TurnRunner`、`StudyTools`、`ToolSchemas`。
- 前端：`lib/ws.ts`、会话页作答/跳过。
- 测试：`TurnRunnerTest`/`WsTurnTest` 覆盖作答先行判分与历史；`StudyToolsTest` 覆盖乱序与可选题号。
