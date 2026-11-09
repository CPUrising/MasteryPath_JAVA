# Tasks

## 1. 实现

- [x] 1.1 `QuizSetTurn` 从 `LearningProgress` 构造只读上下文（路径名/目标/模块与知识点，最多 12 个）。
- [x] 1.2 `QuizSetGenerator` 接收上下文并写入用户提示；系统提示强调面向上下文知识点。

## 2. 测试与验证

- [x] 2.1 `QuizSetTurnTest` 新增提示包含路径上下文的用例；既有用例保持通过。
- [x] 2.2 `.\scripts\verify.ps1 -Change 0035-quiz-generation-context` 通过。

## 3. 证据

- [x] 3.1 回填 `docs/verify/0035-quiz-generation-context.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [ ] 人类审查证据后归档。
