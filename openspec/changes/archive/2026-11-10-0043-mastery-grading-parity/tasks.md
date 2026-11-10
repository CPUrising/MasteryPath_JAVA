# Tasks

## 1. 单一判分器

- [x] 1.1 删除 `MasteryGrading` 与其测试；`LearningService` 改用 `Grading.grade(PendingQuestion, String)`。
- [x] 1.2 `real-answers-13.json` 去掉 `mastery` 列，`AnswerFixtures` 同步。

## 2. 服务层

- [x] 2.1 `grade`：`ungraded` 在事务前返回 `{status, reason}`，零状态变更。
- [x] 2.2 `grade`：开放题 `correct` 降 `partial`，`is_correct` 与 `result` 一致。
- [x] 2.3 `repairQuestion(correct)`：重判 `ungraded` 抛前置条件错误；开放题同样降级；返回体增加 `result`。

## 3. 工具与 REST

- [x] 3.1 `mastery_grade`：`ungraded` → `ok=false` + `Not graded: … Ask the learner to clarify.`。
- [x] 3.2 `POST /grade`：只落库 `graded`；`ungraded` 走 422。
- [x] 3.3 `mastery_repair_question` 回执带真实 `result`。
- [x] 3.4 删除「简答题必须带 `accepted_answers`」注册约束。

## 4. 测试

- [x] 4.1 `GradeFlowTest`：`zzz` → `ungraded` 零变更；开放题 → `partial`。
- [x] 4.2 `RepairQuestionTest`：可解析但错误的作答；返回值 9 键；重判 `ungraded` 抛错。
- [x] 4.3 `MasteryGradeContractTest` / `StudyApiTest`：无法解析 → 422 且不落消息。
- [x] 4.4 `StatusAndQuizToolsTest`：`Graded: …` 文案与 `ungraded` 分支。

## 5. 验证

- [x] 5.1 `.\scripts\verify.ps1 -Scope all -Change 0043-mastery-grading-parity` 通过。
- [x] 5.2 回填 `docs/verify/0043-mastery-grading-parity.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
