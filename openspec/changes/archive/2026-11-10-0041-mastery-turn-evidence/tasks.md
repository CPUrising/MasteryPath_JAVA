# Tasks

## 1. 历史回放

- [x] 1.1 `TurnRunner.historyMessages` 把 `data.card` 还原成题号/题型/题干/选项。
- [x] 1.2 同处把 `data.grade` 还原成作答/结果/判分后掌握度；空正文但有证据的消息不再被丢弃。

## 2. 判分落库

- [x] 2.1 `LearningService.recordTurn` 增加 `grade` 参数，写入 assistant 消息 `data.grade`。
- [x] 2.2 `TurnRunner.persistMasteryTurn` 从 `tool_result.metadata.mastery_grade` 取判分并落库。

## 3. 事件归因

- [x] 3.1 `registerQuestion` / `grade` / `assess` 的 `tx.emit` 透传 `sessionId` / `turnId`。
- [x] 3.2 `skipQuestion` / `noteExplained` 增加 `sessionId` / `turnId` 参数并透传，工具层同步。

## 4. 状态快照

- [x] 4.1 `getState` 增加 `last_answered`（反向找最新未作废证据 + 回查作答原文）。
- [x] 4.2 `StatePayload` 在 `active` 分支透传、`empty` 分支给 `{status: "none"}`。

## 5. 验证

- [x] 5.1 新增/更新 `TurnRunnerTest`、`PathStateTest`、`ObjectiveStateTest`、`GradeFlowTest`、`QuizRegistrationTest`。
- [x] 5.2 `.\scripts\verify.ps1 -Scope all -Change 0041-mastery-turn-evidence` 通过（945 用例）。
- [x] 5.3 回填 `docs/verify/0041-mastery-turn-evidence.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
