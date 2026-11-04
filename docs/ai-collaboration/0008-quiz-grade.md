# AI 协作留痕：0008-quiz-grade

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/domain/grading/**`（新增 3 个文件）、
  `src/main/java/com/masterypath/domain/quiz/**`（新增 2 个文件）、
  `src/main/java/com/masterypath/service/LearningService.java`（补 5 个方法）、
  `src/main/java/com/masterypath/storage/LearningStore.java`（补 `mutateBankEntry`）、
  `src/test/java/com/masterypath/domain/grading/**`、`src/test/java/com/masterypath/domain/quiz/**`、
  `src/test/java/com/masterypath/service/GradeFlowTest.java`、
  `src/test/java/com/masterypath/service/QuizRegistrationTest.java`、
  `src/test/java/com/masterypath/service/QuestionBankTest.java`、
  `scripts/verify.ps1`、`openspec/changes/0008-quiz-grade/**`、`docs/**`。
- 明确不做的事：不实现 `review.scheduled` 与 SM-2（`0010`）、`assess`（`0009`）、
  `repairQuestion`（`0012`）、工具层 `ToolResult` 与 mode 门控（`0013`）。
- 出处：用户指令原文「提交后继续 `0008`」；`docs/migration-plan.md` §5 第 8 行。

## 2. AI 产出

- 新增文件：
  - `Grading`（normalize / similarity / resolveChoice / grade + 三类题型）
  - `Decision`（record）
  - `QuizSpec`（题型推断、选项校验、关键词兜底、`toPending`）
  - 测试 5 个：`GradingTest`（27）、`QuizSpecTest`（20）、`GradeFlowTest`（23）、
    `QuizRegistrationTest`（12）、`QuestionBankTest`（9）
- 修改文件：`LearningService`（补 `registerQuestion` / `grade` / `skipQuestion` /
  `listQuestions` / `tagQuestion` + `bankEntry` / `newEvidenceId` 辅助）；
  `LearningStore`（补 `mutateBankEntry`）；`scripts/verify.ps1`（补 4 个测试类名）
- 关键决策：
  - **`normalize` 的步骤顺序不可调整**：NFKC → 小写 → trim → 折叠空白 → 剥标点 → CJK 删空格。
  - **`ungraded` 必须零状态变更**：在进入 `mutate` 之前就返回，连 `pendingQuestion` 都不清。
  - **`gateCleared` 在追加 attempt 与证据之后计算**：`isMastered` 读的是刚写入的分数。
  - **定性类型不写分数**：`concept`/`design` 判分只记证据，分数只能由 `assess` 打开（`INV-05`）。
  - **降级顺序照抄**：open 先降级，hints 后降级；第三分支 `hints > MAX` 已被第二分支覆盖，
    属冗余但照抄以保持行为一致。
  - **单槽位双检**：事务外短路 + 事务内再查，防竞态。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正（提交前完成）：
  1. `QuizSpec` 首次调用 `PendingQuestion.OptionParser.parse` 编译失败（该嵌套类包级可见）→
     改为复用 `PendingQuestion.setOptions` 的公开解析路径。
  2. `QuizRegistrationTest` 的 lambda 返回 `null` 导致类型推断歧义 →
     改为返回 `tx.progress()`。
  3. `consecutiveQuestionsBudget` 初版用「注册 + 跳过」循环模拟，但跳过会清零计数 →
     改为直接预置计数，并补一个「未达上限允许」的对照用例。
  4. 收尾自查发现工作区 `.gitignore` 被意外删除（`artifacts/`、`target/` 变成未跟踪）→
     用 `git checkout -- .gitignore` 恢复。
  5. 收尾自查发现 `scripts/verify.ps1` 被写入了**两个** UTF-8 BOM，PowerShell 直接解析失败 →
     去重为单个 BOM 后复跑全量，353 项通过。根因是多次用编辑工具改该文件时 BOM 被重复追加。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 91 / 0；全量 353 / 0。
- 证据链接：[`docs/verify/0008-quiz-grade.md`](../verify/0008-quiz-grade.md)
- 日志：`artifacts/verify-0008-quiz-grade-20261104-143201.log`
- 参考实现对照：在 `../MasteryPath` 执行
  `pytest tests/test_grading.py tests/test_study.py tests/test_interactions.py -q` → **16 passed**。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 无法解析的作答判什么 | **`ungraded`，不判错** | `INV-46`；猜测不能被记成负面 |
| `ungraded` 是否清槽位 | **不清** | 零状态变更；学习者可以重答同一题 |
| `gateCleared` 计算时机 | **追加 attempt/证据之后** | `isMastered` 读刚写入的分数；先算恒为 false |
| 定性类型是否写分数 | **不写** | `INV-05`；分数只能由 `assess` 打开 |
| open 的 correct 是否降级 | **降级为 partial** | `INV-35`；开放题不单独授予掌握 |
| 有提示的 correct 是否降级 | **降级为 partial** | `INV-36` |
| `hints > MAX` 冗余分支是否保留 | **保留** | 参考实现原样；删掉虽不影响行为，但偏离逐字复刻 |
| 单槽位检查次数 | **两次**（事务外 + 事务内） | 防竞态；参考实现如此 |
| `normalize` 是否先剥标点再折叠空白 | **先折叠空白再剥标点** | 顺序反了会让 `"a 。 b"` 与 `"a b"` 不等价 |
| 长作答阈值 30 判原始还是归一化长度 | **归一化后** | 参考实现判的是 `len(normalize(...))` |
| `casefold` 与 `toLowerCase` 差异 | **接受** | 已登记 `migration-plan.md` §11；测试数据不含 `ß` 类字符 |
| 题库是否走 CAS | **不走** | 题库是独立表，非 path 聚合；标签是单行更新 |
| 判分是否写题库 | **写** | 参考实现 `save_bank_entry`；`0011` 会在此基础上做标签与筛选 |
