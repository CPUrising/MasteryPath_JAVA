# Tasks

## 1. 判分纯函数

- [x] 1.1 `Decision` record：`result` / `reason` / `resolved`
- [x] 1.2 `Grading.normalize`：NFKC → 小写 → trim → 折叠空白 → 剥首尾标点 → 含 CJK 则删空格
- [x] 1.3 `Grading.similarity`：Levenshtein 归一化距离
- [x] 1.4 `Grading.resolveChoice`：标签直接命中 → 正文唯一匹配 → 否则空串
- [x] 1.5 `Grading.grade`：按题型分派 choice / short / open

## 2. 出题参数校验

- [x] 2.1 `QuizSpec.normalize`：题型推断（有选项即 choice，否则 short）、非法题型拒绝、缺 `expected_answer` 拒绝
- [x] 2.2 简答/开放题带选项 → 拒绝
- [x] 2.3 choice：标签必须 A、B、C… 连续且唯一；正文非空且去重
- [x] 2.4 choice：`expected_answer` 必须可解析为选项标签
- [x] 2.5 `QuizSpec.openKeywords`：open 题无关键词时用 `expected_answer` 兜底

## 3. 注册题目

- [x] 3.1 `registerQuestion`：已有未终态题目返回 `null`（事务外 + 事务内双检）
- [x] 3.2 连续追问达 `MAX_CONSECUTIVE_QUESTIONS` 时拒绝，文案逐字
- [x] 3.3 写入 `pendingQuestion`、`MasteryInteraction(AWAITING_INPUT)`，发 `question.registered`

## 4. 判分

- [x] 4.1 `grade`：未知题目拒绝；已 `graded` 返回 `already_graded` + 首次结果；非 open 状态拒绝
- [x] 4.2 `ungraded` → 返回 `{status: ungraded, reason}` 且零状态变更
- [x] 4.3 降级：open 的 correct → partial；hints>0 的 correct → partial
- [x] 4.4 追加 `QuizAttempt`（`independent = hintsUsed == 0`）与 `LearningEvidence`
- [x] 4.5 定量类型重算 `masteryLevels`；定性类型不写分
- [x] 4.6 `gateCleared` 在写入之后计算；发 `attempt.graded` 与 `objective.mastered`
- [x] 4.7 写题库条目；清空 `pendingQuestion`、`consecutiveQuestions`

## 5. 跳过与题库

- [x] 5.1 `skipQuestion`：未终态置 `ABANDONED`，清空槽位，发 `question.skipped`
- [x] 5.2 `listQuestions` / `tagQuestion`

## 6. 测试

- [x] 6.1 `GradingTest`：TC-H1…H5 + normalize/similarity 边界
- [x] 6.2 `QuizRegistrationTest`：TC-K1 校验 + TC-K2 单槽位 + 连续追问预算
- [x] 6.3 `GradeFlowTest`：TC-K3/E3 幂等 + TC-K4 ungraded 零变更 + TC-K5 提示降级 + 掌握推进 + concept 不写分
- [x] 6.4 `QuestionBankTest`：判分写库 + 标签 + 筛选

## 7. 验证

- [x] 7.1 运行 `.\scripts\verify.ps1 -Scope unit --change 0008-quiz-grade`
- [x] 7.2 运行全量 `.\scripts\verify.ps1 --change 0008-quiz-grade`
- [x] 7.3 与参考实现对照（`pytest tests/test_grading.py tests/test_study.py tests/test_interactions.py -q`）
- [x] 7.4 回填 `docs/verify/0008-quiz-grade.md`、`docs/regression-log.md`、`docs/traceability.md`、`README.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0008-quiz-grade.md`
