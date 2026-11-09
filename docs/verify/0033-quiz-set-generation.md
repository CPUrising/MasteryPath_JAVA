# 验证报告：0033-quiz-set-generation

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0033-quiz-set-generation` |
| 执行时间 | 2026-11-09 22:58–22:59（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | JDK 21.0.11 / Maven Wrapper / Spring Boot 3.5.16 |
| 命令 | `.\scripts\verify.ps1 -Change 0033-quiz-set-generation` |

## 2. 变更摘要

后端新增「生成型练习题卷」能力：

- 存储：`quiz_sets` / `quiz_items` 两张增量表（旧表 DDL 逐字不动），`deletePath` 级联清理。
- 领域：`QuizSet` / `QuizItem` / `QuizSetProjector` / `QuizSetGrader` / `QuizSetSpec`。
  投影在提交前隐藏答案，提交后释放答案与解析。
- 服务：`QuizSetService`（save / get / view / submit）。`submit` 全量可判定才写回；
  任一 `ungraded` → 返回逐题原因且零状态变更；已提交 → 返回首次结果。
- REST：`GET /learning/paths/{pathId}/quiz-sets/{setId}`、
  `POST /learning/paths/{pathId}/quiz-sets/{setId}/submit`。
- 回合：`TurnRequest` 支持 `capability="deep_question"`、`num_questions`、
  `difficulty`、`question_types`；`TurnRunner` 分派到 `QuizSetTurn`，
  依次发 `quiz_plan` → `quiz_question` × N → `quiz_set` → `turn_end{reason:"quiz_set"}`。
- 生成：`QuizSetGenerator` 单次结构化生成 + `QuizSpec` 校验 + 一次修复；失败零落库。
- 落库：`LearningService.recordQuizSetTurn` 写 user + assistant `data.quiz_set`，刷新可恢复。

## 3. 验证结果

```
[INFO] Tests run: 893, Failures: 0, Errors: 0, Skipped: 0
[PASS] 测试通过。
```

新增/相关用例：

| 用例 | 覆盖 |
|---|---|
| `QuizSetStoreTest#savesAndReadsQuizSet` | 题卷与题目落库、读取 |
| `QuizSetStoreTest#deletePathCascadesQuizSets` | 删除路径级联清理 |
| `QuizSetProjectorTest` | 提交前无答案键；提交后释放答案 |
| `QuizSetGraderTest` | 选择/短答/开放题判分复用 `Grading` |
| `QuizSetServiceTest#submitGradesAndLeavesMasteryUntouched` | 提交后掌握度不变 |
| `QuizSetServiceTest#ungradedLeavesNoTrace` | `ungraded` 零状态变更 |
| `QuizSetServiceTest#repeatedSubmitIsIdempotent` | 重复提交幂等 |
| `QuizSetServiceTest#viewHidesThenReleasesAnswers` | 投影答案门控 |
| `QuizSetApiTest#getHidesAnswersBeforeSubmit` | GET 不泄漏答案 |
| `QuizSetApiTest#submitReleasesAnswersAndIsIdempotent` | 提交释放答案 + 幂等 + 不写 `learning_evidence` |
| `QuizSetApiTest#emptyAnswersIs422` | 空答案 422 |
| `QuizSetApiTest#unknownSetOrWrongPathIs404` | 未知 set / path 不匹配 404 |
| `QuizSetTurnTest#generatesQuizSetWithEvents` | 事件顺序与落库 |
| `QuizSetTurnTest#invalidGenerationFailsClosed` | 非法生成失败零落库 |
| `QuizSetTurnTest#repairsOnce` | 一次修复后成功 |
| `QuizSetTurnTest#unknownCapabilityThrows` | 未知 capability 显式报错 |

## 4. 回归

| 范围 | 结果 |
|---|---|
| 全量 `verify.ps1` | 893 / 0 / 0 |
| 旧库兼容 | 通过（只新增表，`ReferenceWorkspaceCompatibilityTest` 等既有用例未变） |
| 掌握闭环 | 通过（`GradeFlowTest` / `QualitativeAssessTest` 等未回归） |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 本 change 只做后端；前端 QuizViewer 与真实模型 E2E 留到 `0034` | 低 | 已在 `tasks.md` 的 Workflow follow-up 声明 |
| D2 | 生成器为单次结构化生成，未做 DeepTutor 式的 `planning → quizzing` 多阶段 | 低 | 可观察契约一致；后续可替换生成器 |
| D3 | `QuizSetApiTest` 在首轮 verify 后新增了 `learning_evidence` 为空的断言 | 低 | 已重跑全量，本次数字为该断言后的最终结果 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
