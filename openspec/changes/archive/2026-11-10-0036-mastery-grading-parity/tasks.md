# Tasks

## 1. 判分

- [x] 1.1 新增 `domain/grading/MasteryGrading`，逐字复刻 DeepTutor `grade_answer`（含 SequenceMatcher 相似度）。
- [x] 1.2 新增 `MasteryGradingTest`，覆盖 choice/short/open、空期望、长度边界、关键词比例。

## 2. 掌握度

- [x] 2.1 `MasteryScore` 改为最近 5 次加权 `(0.5,0.7,0.85,0.95,1.0)` + 置信上限 `{1:0.5, 2:0.8}`。
- [x] 2.2 只统计 `independent && !voided` 的作答；重写 `MasteryScoreTest`。

## 3. 服务层

- [x] 3.1 `LearningService.grade` 使用 `MasteryGrading`；移除 `ungraded`、开放题降级与提示降级。
- [x] 3.2 提示仍写 `hints_used` 与 `independent=false`，但不改变判分结果。
- [x] 3.3 更新 `GradeFlowTest`、`StudyApiTest` 等受影响断言。

## 4. 验证

- [x] 4.1 `.\scripts\verify.ps1 -Change 0036-mastery-grading-parity` 通过。
- [x] 4.2 回填 `docs/verify/0036-mastery-grading-parity.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [ ] 人类审查证据后归档。
- [ ] 后续批次：题卡/回合协议、提示词包、学习阶段、导航关系、存储事件、视觉学习、调度器、topic 生成。
