# Tasks

## 1. 请求与回合

- [x] 1.1 `TurnRequest` 增加 `masteryAnswer` / `masterySkip`；`WsTurnHandler` 解析并回填消息。
- [x] 1.2 `MasteryTurn` 在 LLM 前调用 `mastery_grade` / `mastery_skip_question`，发 `tool_result`。
- [x] 1.3 注入会话历史（role + content，最多 20 条）。

## 2. 题卡与工具

- [x] 2.1 选择题注册时打乱选项、按位置重贴标签并跟随 `expected_answer`。
- [x] 2.2 题卡增加 `allow_free_text=true`；判分结果补 `correct_label` / `correct_body` / `learner_answer` / `independent`。
- [x] 2.3 `mastery_grade.question_id` 可选；`mastery_skip_question` 无参。

## 3. 前端

- [x] 3.1 `lib/ws.ts` 增加 `mastery_answer` / `mastery_skip`。
- [x] 3.2 会话页作答/跳过改走 WS，消费 `mastery_grade` 并标记题卡已答。

## 4. 验证

- [x] 4.1 新增/更新 `TurnRunnerTest`、`WsTurnTest`、`StudyToolsTest`。
- [x] 4.2 `.\scripts\verify.ps1 -Change 0037-mastery-turn-protocol` 通过。
- [x] 4.3 回填 `docs/verify/0037-mastery-turn-protocol.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
