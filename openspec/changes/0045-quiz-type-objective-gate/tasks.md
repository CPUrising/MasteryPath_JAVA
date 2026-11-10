# Tasks

## 1. 题型门控

- [x] 1.1 `StudyTools.Quiz` 在注册前校验：目标门槛为 `quantitative` 时拒绝 `question_type=open`。
- [x] 1.2 拒绝文案指向 `question_type=choice` / `question_type=short`（≤30 字或 `accepted_answers`）。
- [x] 1.3 `mastery_quiz` 工具描述与 `question_type` schema 描述补充题型约束。

## 2. repair 诚实化

- [x] 2.1 `LearningService.repairQuestion` 返回体与 interaction 结果增加 `effective`；open 的 `correct` 为 `false`。
- [x] 2.2 open 且 `effective=false` 时附 `credit_note`。
- [x] 2.3 `RepairTools` 回执与工具描述按 `effective` 如实表述。

## 3. 测试

- [x] 3.1 `StatusAndQuizToolsTest`：memory / procedure 目标 open 被拒；choice / short 通过；concept 目标 open 通过。
- [x] 3.2 `RepairQuestionTest`：open 题 correct → `partial`、`effective=false`、掌握度不变；short 题 correct → `effective=true`。
- [x] 3.3 调整既有 open 用例到 `concept` 目标。

## 4. 验证

- [x] 4.1 `.\scripts\verify.ps1 -Scope all -Change 0045-quiz-type-objective-gate` 通过（944 tests）。
- [x] 4.2 回填 `docs/verify/0045-quiz-type-objective-gate.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## 5. 运维数据修复

- [x] 5.1 通过 `repairQuestion(void)` 作废 `03ae…`、`524e…`、`81a2…` 三条 open 记录，保留事件与审计链。
- [x] 5.2 为 `k5` 重新登记一道 `choice` 题（`82ae…`，答案键 `B`）。

## Workflow follow-up

- [ ] 人类审查证据后归档。
