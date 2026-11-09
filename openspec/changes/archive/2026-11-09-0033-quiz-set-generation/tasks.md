# Tasks

## 1. 存储

- [x] 1.1 `Schema` 增加 `quiz_sets` / `quiz_items` 增量表与索引。
- [x] 1.2 `LearningStore` 增加 save/get/list/submit 结果写入；`deletePath` 级联清理。
- [x] 1.3 `QuizSetStoreTest` 覆盖保存、读取、删除级联；旧表由既有兼容用例守护。

## 2. 领域模型与投影

- [x] 2.1 新增 `QuizSet` / `QuizItem`；复用 `PendingQuestion` 固定答案键。
- [x] 2.2 新增 `QuizSetProjector`（提交前隐藏答案、提交后释放）。
- [x] 2.3 新增 `QuizSetGrader`（复用 `Grading`）；`QuizSetProjectorTest` / `QuizSetGraderTest` 覆盖。

## 3. 服务与 REST

- [x] 3.1 新增 `QuizSetService`：save / get / submit；ungraded 零变更、重复提交幂等。
- [x] 3.2 新增 `QuizSetController`：GET 投影、POST submit、404/422 映射。
- [x] 3.3 `QuizSetApiTest` 覆盖 GET/submit/幂等/掌握度不变。

## 4. 回合与生成

- [x] 4.1 `TurnRequest` 增加 `deep_question` 与题数/难度/题型；`WsTurnHandler` 解析。
- [x] 4.2 新增 `QuizSetGenerator`：结构化生成、`QuizSpec` 校验、一次修复、失败零落库。
- [x] 4.3 新增 `QuizSetTurn` 与 `TurnRunner` 分派；发 `quiz_plan` / `quiz_question` / `quiz_set` / `turn_end`。
- [x] 4.4 `LearningService.recordQuizSetTurn` 落库 `data.quiz_set`；`QuizSetTurnTest` 覆盖事件顺序与失败。

## 5. 验证

- [x] 5.1 `.\mvnw.cmd -q -DskipTests test-compile` 通过。
- [x] 5.2 `.\scripts\verify.ps1 -Change 0033-quiz-set-generation` 通过（893 / 0 / 0）。
- [x] 5.3 回填 `docs/verify/0033-quiz-set-generation.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [ ] 人类审查证据后归档。
- 前端 QuizViewer 与真实模型 E2E 在 `0034` 完成。
