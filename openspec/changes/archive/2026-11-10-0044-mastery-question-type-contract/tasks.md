# Tasks

## 1. 注册校验

- [x] 1.1 `StudyTools.Quiz` 增加长度校验：`short` 且归一化期望答案 >30 字时要求存在 ≤30 字的 `accepted_answers`。
- [x] 1.2 拒绝文案指向 `question_type=open` 与 `accepted_answers`。
- [x] 1.3 `mastery_quiz` 工具描述补充题型指引。

## 2. 测试

- [x] 2.1 `StatusAndQuizToolsTest`：长 short 无短等价答案被拒；带短等价答案通过；长 open 通过。
- [x] 2.2 `StudyApiTest`：REST 422 与 `status=registered` 两条路径。

## 3. 验证

- [x] 3.1 `.\scripts\verify.ps1 -Scope all -Change 0044-mastery-question-type-contract` 通过。
- [x] 3.2 回填 `docs/verify/0044-mastery-question-type-contract.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
