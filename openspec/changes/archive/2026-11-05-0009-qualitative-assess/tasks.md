# Tasks

## 1. 定性评估

- [x] 1.1 `LearningService.assess(pathId, kpId, passed, evidence, quality, review, sessionId, turnId)`
- [x] 1.2 类型校验：仅 `concept` / `design`，文案逐字；校验在写入之前
- [x] 1.3 写 `qualitativeMastery[kpId] = passed`
- [x] 1.4 写 `QUALITATIVE` 证据：`result` 通过为 `correct`、未通过为 `partial`；`quality` 通过 `1.0`、未通过显式值或 `0.2`
- [x] 1.5 `gateCleared` 在写入之后计算；发 `qualitative.assessed`，通过时再发 `objective.mastered`
- [x] 1.6 同一知识点才清空 `pendingQuestion`
- [x] 1.7 写题库条目（`question_type="written"`，`question=kp.name`，`user_answer=evidence`）

## 2. 测试

- [x] 2.1 `QualitativeAssessTest`：TC-M3 通过清门槛 + 来源 system
- [x] 2.2 未通过不清门槛
- [x] 2.3 对 `memory` / `procedure` 拒绝，文案逐字
- [x] 2.4 未知知识点拒绝
- [x] 2.5 多次评估累积证据
- [x] 2.6 `quality` 默认值两条分支 + 通过时忽略传入值
- [x] 2.7 证据类型为 `QUALITATIVE`，未通过结果为 `partial`
- [x] 2.8 只清同一知识点的待答题
- [x] 2.9 题库写入字段正确
- [x] 2.10 定性通过后 `nextObjective` 推进

## 3. 验证

- [x] 3.1 运行 `.\scripts\verify.ps1 -Scope unit --change 0009-qualitative-assess`
- [x] 3.2 运行全量 `.\scripts\verify.ps1 --change 0009-qualitative-assess`
- [x] 3.3 与参考实现对照（`pytest tests/test_assess.py -q`）
- [x] 3.4 回填 `docs/verify/0009-qualitative-assess.md`、`docs/regression-log.md`、`docs/traceability.md`、`README.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0009-qualitative-assess.md`
