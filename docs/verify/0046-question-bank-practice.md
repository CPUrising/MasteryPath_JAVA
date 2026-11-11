# 验证报告：0046-question-bank-practice

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0046-question-bank-practice` |
| 执行时间 | 2026-11-11 03:22（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链 | JDK 21.0.11 / Maven Wrapper / Spring Boot 3.5.16 |
| 命令 | `.\scripts\verify.ps1 --scope all --change 0046-question-bank-practice` |
| 日志 | `artifacts/verify-0046-question-bank-practice-20261111-032204.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 变更摘要

- 新增 `QuestionBankService`：检索（路径 / 关键字 / 结果 / 多标签 AND）、派生标签汇总、
  标签重命名 / 合并 / 删除 / 批量打标、刷题队列、作答判分。
- 新增纯函数 `QuestionBankGrading`：把题库条目适配为 `PendingQuestion` 并复用 `Grading`；
  仅 `choice` / `short` 且答案可落位时才算可刷。
- 新增白名单投影 `QuestionBankProjector.practiceCard`（11 键，隐藏答案）。
- 新增 `QuestionBankPracticeController`：`/questions/tags|search|practice|{id}/attempt|tags/*`。
- 新增 `LearningStore.getBankEntry` / `listAllBankEntries`（只读）。
- Space 页「题库」tab 升级为工作台：筛选、单条/批量标签、标签管理弹窗、刷题器。
- **零 DDL 变更**：未新增表/列，标签管理完全派生自 `question_bank.data.tags`；
  刷题只回写 `user_answer` / `is_correct` / `result`，不触碰掌握闭环。

## 3. 验证结果

```
[INFO] Tests run: 977, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[PASS] 测试通过。
```

基线 944（`0045`）→ 977（新增 33 个用例：`QuestionBankGradingTest` 8、
`QuestionBankProjectorTest` 2、`QuestionBankPracticeServiceTest` 15、`QuestionBankApiTest` 8）。

### 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 |
|---|---|---|---|
| Question Bank Search | 多标签 AND | `QuestionBankPracticeServiceTest#tagsAreAnd`、`QuestionBankApiTest#searchFiltersAndKeepsManagementView` | PASS |
| Question Bank Search | `wrong` 结果别名 | `QuestionBankPracticeServiceTest#wrongResultAlias` | PASS |
| Question Bank Search | 关键字命中题干/知识点 | `QuestionBankPracticeServiceTest#keywordMatchesQuestionAndPoint` | PASS |
| Derived Tag Summary | 计数与空白过滤 | `QuestionBankPracticeServiceTest#tagSummaryCounts`、`QuestionBankApiTest#tagSummary` | PASS |
| Global Tag Management | 重命名合并去重 | `QuestionBankPracticeServiceTest#renameTag`、`QuestionBankApiTest#tagMutations` | PASS |
| Global Tag Management | 合并 / 删除 / 批量 | `QuestionBankPracticeServiceTest#mergeTags`、`#deleteTag`、`#batchTag` | PASS |
| Global Tag Management | 空白标签 422 | `QuestionBankPracticeServiceTest#blankTagRejected`、`QuestionBankApiTest#blankTagIs422` | PASS |
| Practice Queue Hides Answers | 可刷题判定 | `QuestionBankGradingTest#notPracticeable`、`#gradeNotPracticeable` | PASS |
| Practice Queue Hides Answers | 数量夹取 | `QuestionBankPracticeServiceTest#practiceQueueFiltersAndClamps` | PASS |
| Practice Queue Hides Answers | 错题优先 | `QuestionBankPracticeServiceTest#practiceQueueWrongFirst` | PASS |
| Practice Queue Hides Answers | 投影不含答案键 | `QuestionBankProjectorTest#practiceCardWhitelist`、`QuestionBankApiTest#practiceHidesAnswers` | PASS |
| Deterministic Bank Attempt | 只回写题库条目 | `QuestionBankPracticeServiceTest#attemptWritesBack`、`QuestionBankApiTest#attemptGraded` | PASS |
| Deterministic Bank Attempt | `ungraded` 零写入 | `QuestionBankPracticeServiceTest#ungradedWritesNothing`、`QuestionBankApiTest#attemptUngraded` | PASS |
| Deterministic Bank Attempt | 未知条目 404 / 不可刷 422 | `QuestionBankPracticeServiceTest#attemptRejectsUnknownAndUnpracticeable`、`QuestionBankApiTest#attemptErrors` | PASS |
| Deterministic Bank Attempt | 掌握闭环不变 | `QuestionBankPracticeServiceTest#attemptLeavesMasteryUntouched` | PASS |
| Additive Storage | 旧库可打开、无 DDL 变更 | `ReferenceWorkspaceCompatibilityTest`（既有，全绿） | PASS |

### 前端静态检查

| 命令 | 结果 |
|---|---|
| `cd web; npm run typecheck` | PASS（无输出即通过） |
| `cd web; npm run lint` | PASS（No ESLint warnings or errors） |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 浏览器手工验收（Space → 题库 → 刷题交互）未执行 | 一般 | 已登记 `docs/verification-debt.md`；REST 契约由 `QuestionBankApiTest` 覆盖，前端只做了 typecheck/lint |
| D2 | 刷题不保存历史，只保留最近一次作答 | 低 | 设计决策：避免新增表/DDL；与「练习不影响掌握」一致 |
| D3 | `open` / `written` 题不可刷 | 低 | 题库无关键词/标准答案，无法确定性判分（INV-33 / INV-46） |
| D4 | 标签重命名为逐条单行事务 | 低 | 标签规模小；`mutateBankEntry` 单行原子，失败不留半写 |

## 5. 结论

- [x] Ready for archive（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
