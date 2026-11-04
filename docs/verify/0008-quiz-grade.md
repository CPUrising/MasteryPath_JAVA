# 验证报告：0008-quiz-grade

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0008-quiz-grade` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 14:32:01（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0008-quiz-grade` |
| 日志 | `artifacts/verify-0008-quiz-grade-20261104-143201.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Deterministic Grading | TC-H1 正文唯一匹配 | `GradingTest#choiceBodyUniqueMatch` | PASS | |
| Deterministic Grading | TC-H1 标签直接命中 | `GradingTest#choiceLabelMatches` | PASS | |
| Deterministic Grading | TC-H1 小写标签 | `GradingTest#choiceLowercaseLabel` | PASS | |
| Deterministic Grading | TC-H1 选错带 resolved | `GradingTest#choiceWrongLabel` | PASS | |
| Deterministic Grading | TC-H2 无法解析 → ungraded | `GradingTest#choiceUnparseableIsUngraded` | PASS | `INV-46` |
| Deterministic Grading | TC-H2 正文重复歧义 | `GradingTest#choiceAmbiguousBodyIsUngraded` | PASS | |
| Deterministic Grading | TC-H2 空作答 | `GradingTest#emptyChoiceAnswerIsUngraded` | PASS | |
| Deterministic Grading | TC-H3 数字等价 | `GradingTest#shortNumeric` | PASS | |
| Deterministic Grading | TC-H3 相对容差 | `GradingTest#numericTolerance` | PASS | |
| Deterministic Grading | TC-H4 模糊阈值 0.90 | `GradingTest#shortFuzzyThreshold` | PASS | `>=` 边界 |
| Deterministic Grading | TC-H4 长作答只精确 | `GradingTest#longAnswerOnlyExact` | PASS | |
| Deterministic Grading | TC-H4 可接受答案 | `GradingTest#acceptedAnswersMatch` | PASS | |
| Deterministic Grading | TC-H5 可选词不足 → partial | `GradingTest#openOptionalInsufficient` | PASS | |
| Deterministic Grading | TC-H5 全部关键词 → correct | `GradingTest#openAllKeywords` | PASS | |
| Deterministic Grading | TC-H5 必需词全缺 → incorrect | `GradingTest#openMissingAllRequired` | PASS | |
| Deterministic Grading | TC-H5 部分必需词 → partial | `GradingTest#openPartialRequired` | PASS | |
| Deterministic Grading | 开放题无关键词 → ungraded | `GradingTest#openNoKeywordsIsUngraded` | PASS | |
| Deterministic Grading | 开放题空作答 → ungraded | `GradingTest#openEmptyIsUngraded` | PASS | |
| —（归一化） | 剥首尾标点 | `GradingTest#normalizeStripsPunctuation` | PASS | |
| —（归一化） | CJK 删空格 | `GradingTest#normalizeRemovesSpacesForCjk` | PASS | |
| —（归一化） | 全角转半角 | `GradingTest#normalizeFullWidthDigits` | PASS | NFKC |
| —（归一化） | null 安全 | `GradingTest#normalizeNullSafe` | PASS | |
| —（相似度） | 边界 1.0 / 0.0 | `GradingTest#similarityBounds` | PASS | |
| —（相似度） | 皆空为 1.0 | `GradingTest#similarityEmptyBoth` | PASS | |
| —（相似度） | 单字符差异归一 | `GradingTest#similaritySingleDifference` | PASS | |
| —（解析） | 标签优先于正文 | `GradingTest#resolveChoicePrefersLabel` | PASS | |
| —（解析） | 未命中返回空串 | `GradingTest#resolveChoiceMissReturnsEmpty` | PASS | |
| Single Question Slot | TC-K1 无选项推断 short | `QuizSpecTest#infersShort` | PASS | |
| Single Question Slot | TC-K1 有选项推断 choice | `QuizSpecTest#infersChoice` | PASS | |
| Single Question Slot | TC-K1 非法题型拒绝 | `QuizSpecTest#rejectsUnknownType` | PASS | |
| Single Question Slot | TC-K1 缺期望答案拒绝 | `QuizSpecTest#rejectsMissingExpected` | PASS | |
| Single Question Slot | TC-K1 简答带选项拒绝 | `QuizSpecTest#rejectsOptionsOnShort` | PASS | |
| Single Question Slot | TC-K1 开放题带选项拒绝 | `QuizSpecTest#rejectsOptionsOnOpen` | PASS | |
| Single Question Slot | TC-K1 标签不连续拒绝 | `QuizSpecTest#rejectsNonSequentialLabels` | PASS | |
| Single Question Slot | TC-K1 标签重复拒绝 | `QuizSpecTest#rejectsDuplicateLabels` | PASS | |
| Single Question Slot | TC-K1 正文重复拒绝 | `QuizSpecTest#rejectsDuplicateBodies` | PASS | |
| Single Question Slot | TC-K1 正文为空拒绝 | `QuizSpecTest#rejectsEmptyBody` | PASS | |
| Single Question Slot | TC-K1 期望答案不可解析拒绝 | `QuizSpecTest#rejectsUnresolvableExpected` | PASS | |
| Single Question Slot | TC-K1 期望答案写正文可解析 | `QuizSpecTest#resolvesExpectedFromBody` | PASS | |
| Single Question Slot | TC-K1 标签统一大写 | `QuizSpecTest#uppercasesLabels` | PASS | |
| Single Question Slot | TC-K1 旧式字符串选项 | `QuizSpecTest#parsesLegacyStringOptions` | PASS | |
| Single Question Slot | TC-K1 options 非数组拒绝 | `QuizSpecTest#rejectsNonArrayOptions` | PASS | |
| Single Question Slot | 开放题关键词保留 | `QuizSpecTest#keepsOpenKeywords` | PASS | |
| Single Question Slot | 关键词兜底 | `QuizSpecTest#openKeywordsFallBackToExpected` | PASS | |
| Single Question Slot | 非开放题不兜底 | `QuizSpecTest#nonOpenDoesNotFallBack` | PASS | |
| Single Question Slot | toPending 补齐字段 | `QuizSpecTest#toPendingFillsFields` | PASS | |
| Single Question Slot | canonicalLabels 序列 | `QuizSpecTest#canonicalLabelsSequence` | PASS | |
| Single Question Slot | 首次注册成功 | `QuizRegistrationTest#registersFirstQuestion` | PASS | |
| Single Question Slot | 注册更新聚合 | `QuizRegistrationTest#registerUpdatesAggregate` | PASS | |
| Single Question Slot | 注册发事件 | `QuizRegistrationTest#registerEmitsEvent` | PASS | |
| Single Question Slot | TC-K2 二次注册返回 null | `QuizRegistrationTest#secondRegistrationReturnsNull` | PASS | `INV-02` |
| Single Question Slot | TC-K2 保留原题 | `QuizRegistrationTest#rejectedRegistrationKeepsOriginal` | PASS | |
| Single Question Slot | TC-K2 不增加计数 | `QuizRegistrationTest#rejectedRegistrationDoesNotBumpCounter` | PASS | |
| Single Question Slot | TC-E2 判分后可注册 | `QuizRegistrationTest#canRegisterAfterGraded` | PASS | |
| Single Question Slot | 跳过后可注册 | `QuizRegistrationTest#canRegisterAfterSkip` | PASS | |
| Single Question Slot | 连续追问达上限拒绝 | `QuizRegistrationTest#consecutiveQuestionsBudget` | PASS | `INV-60` |
| Single Question Slot | 未达上限允许 | `QuizRegistrationTest#consecutiveQuestionsBelowBudget` | PASS | |
| Single Question Slot | 缺失路径创建聚合 | `QuizRegistrationTest#registerOnMissingPathCreates` | PASS | |
| Single Question Slot | 保留 session/turn | `QuizRegistrationTest#registerKeepsSessionAndTurn` | PASS | |
| Deterministic Grading | TC-K3 首次判分 | `GradeFlowTest#firstGradeIsGraded` | PASS | |
| Deterministic Grading | TC-K3/E3 幂等 | `GradeFlowTest#gradeIsIdempotent` | PASS | `INV-21` |
| Deterministic Grading | TC-K3 忽略新答案 | `GradeFlowTest#idempotentIgnoresNewAnswer` | PASS | |
| Deterministic Grading | TC-K4 ungraded | `GradeFlowTest#unparseableChoiceIsUngraded` | PASS | |
| Deterministic Grading | TC-K4 零状态变更 | `GradeFlowTest#ungradedLeavesNoTrace` | PASS | `INV-46` |
| Deterministic Grading | TC-K4 后可重判 | `GradeFlowTest#canGradeAfterUngraded` | PASS | |
| Deterministic Grading | TC-K5 提示降级 | `GradeFlowTest#hintsDowngradeToPartial` | PASS | `INV-36` |
| Deterministic Grading | TC-K5 提示不打开门槛 | `GradeFlowTest#hintsPreventMastery` | PASS | |
| Deterministic Grading | TC-K5 无提示不降级 | `GradeFlowTest#noHintsStaysCorrect` | PASS | |
| Deterministic Grading | 开放题 correct 降级 | `GradeFlowTest#openCorrectDowngradedToPartial` | PASS | `INV-35` |
| Deterministic Grading | 答对清除门槛 | `GradeFlowTest#correctAnswerClearsGate` | PASS | |
| Deterministic Grading | 答错不清除 | `GradeFlowTest#wrongAnswerDoesNotClearGate` | PASS | |
| Deterministic Grading | 答对推进前沿 | `GradeFlowTest#correctAnswerAdvancesFrontier` | PASS | |
| Deterministic Grading | 判分清空槽位 | `GradeFlowTest#gradeClearsSlot` | PASS | |
| Deterministic Grading | concept 不写分 | `GradeFlowTest#conceptGradingDoesNotWriteScore` | PASS | `INV-05` |
| Deterministic Grading | concept 仍记证据 | `GradeFlowTest#conceptGradingStillRecordsEvidence` | PASS | |
| Deterministic Grading | 未知题目拒绝 | `GradeFlowTest#unknownQuestionRejected` | PASS | |
| Deterministic Grading | 已作废不可判 | `GradeFlowTest#abandonedQuestionNotGradable` | PASS | |
| Deterministic Grading | 判分发两事件 | `GradeFlowTest#gradeEmitsEvents` | PASS | |
| Deterministic Grading | 答错不发 mastered | `GradeFlowTest#wrongAnswerEmitsNoMastered` | PASS | |
| Deterministic Grading | review 标记 | `GradeFlowTest#reviewFlagRecorded` | PASS | |
| Deterministic Grading | 判分写题库 | `GradeFlowTest#gradeWritesBankEntry` | PASS | |
| Deterministic Grading | independent 标记 | `GradeFlowTest#attemptIndependentFlag` | PASS | |
| —（题库） | 判分入库字段齐全 | `QuestionBankTest#bankEntryCapturesGrading` | PASS | |
| —（题库） | 答错也入库 | `QuestionBankTest#wrongAnswerAlsoStored` | PASS | |
| —（题库） | 无标签返回全部 | `QuestionBankTest#listAllWhenNoTag` | PASS | |
| —（题库） | 按标签筛选 | `QuestionBankTest#filterByTag` | PASS | |
| —（题库） | 标签去重保序 | `QuestionBankTest#taggingDeduplicates` | PASS | |
| —（题库） | 空白标签忽略 | `QuestionBankTest#blankTagsIgnored` | PASS | |
| —（题库） | 缺失条目返回 null | `QuestionBankTest#tagMissingEntryReturnsNull` | PASS | |
| —（题库） | 按创建时间倒序 | `QuestionBankTest#bankOrderedByCreationDesc` | PASS | |
| —（题库） | 空题库 | `QuestionBankTest#emptyBank` | PASS | |

本 change 新增用例：**91 个**（`GradingTest` 27 + `QuizSpecTest` 20 + `GradeFlowTest` 23 +
`QuizRegistrationTest` 12 + `QuestionBankTest` 9），全部 PASS。
全量 **353 个，0 失败**（262 → 353）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 284, Failures: 0 |
| 全量 | `verify.ps1 -Change 0008-quiz-grade` | BUILD SUCCESS，Tests run: 353, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0007 的 262 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_grading.py tests/test_study.py tests/test_interactions.py -q` | **16 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_tc_h1_choice_wrong_label_unique_body_matches` | `GradingTest#choiceBodyUniqueMatch` | 同断言 |
| `test_tc_h2_choice_unparseable_is_ungraded` | `GradingTest#choiceUnparseableIsUngraded` | 同断言 |
| `test_tc_h3_short_numeric` | `GradingTest#shortNumeric` | 同断言 |
| `test_tc_h4_short_fuzzy_threshold` | `GradingTest#shortFuzzyThreshold` | 同断言（0.90 边界） |
| `test_tc_h5_open_optional_insufficient_is_partial` | `GradingTest#openOptionalInsufficient` + `#openAllKeywords` | 拆为两例 |
| `test_tc_k1_choice_ok_short_with_options_rejected` | `QuizSpecTest#rejectsOptionsOnShort` + `#infersChoice` | 拆为两例 |
| `test_tc_k2_single_slot_second_quiz_re_presents` | `QuizRegistrationTest#secondRegistrationReturnsNull` + `#rejectedRegistrationKeepsOriginal` | 拆为两例 |
| `test_tc_k3_e3_grade_is_idempotent` | `GradeFlowTest#gradeIsIdempotent` | 同断言 |
| `test_tc_k4_unparseable_choice_is_ungraded_no_state` | `GradeFlowTest#ungradedLeavesNoTrace` | 同断言 |
| `test_tc_k5_hints_downgrade_to_partial` | `GradeFlowTest#hintsDowngradeToPartial` + `#hintsPreventMastery` | 拆为两例 |
| `test_quantitative_gate_advances_after_correct` | `GradeFlowTest#correctAnswerClearsGate` + `#correctAnswerAdvancesFrontier` | 拆为两例 |
| `test_tc_e1_single_open_slot` / `test_tc_e2_after_graded_new_question_is_open` | `QuizRegistrationTest#canRegisterAfterGraded` | 服务层等价覆盖 |
| `test_tc_e4_public_projection_hides_answer` | 已由 `0006` 的 `QuestionCardProjectorTest` 覆盖 | — |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `GradeFlowTest#openCorrectDowngradedToPartial` | 参考实现只在纯函数层测 open，无「服务层降级」断言 |
| `GradeFlowTest#conceptGradingDoesNotWriteScore` / `#conceptGradingStillRecordsEvidence` | 参考实现无 `INV-05` 的判分侧用例 |
| `QuizRegistrationTest#consecutiveQuestionsBudget` | 参考实现无连续追问预算的直接测试（只有 loop 层的工具预算） |
| `QuizRegistrationTest#canRegisterAfterSkip` | 参考实现无 skip 后槽位释放的用例 |
| `QuestionBankTest`（9 项） | 参考实现无题库读写/标签/筛选的独立测试 |
| `GradingTest#normalize*`（4 项） | 参考实现无 `normalize` 的直接测试 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `QuizSpec` 首次调用 `PendingQuestion.OptionParser.parse` 失败（包级可见） | 一般 | 改为复用 `PendingQuestion.setOptions` 的公开解析路径，避免重复实现 |
| D2 | `QuizRegistrationTest` 的 lambda 返回 `null` 导致类型推断歧义 | 一般 | 改为返回 `tx.progress()` |
| D3 | `casefold` 与 `toLowerCase(Locale.ROOT)` 对德语 `ß` 等极少数场景不同 | 一般 | 已登记 `migration-plan.md` §11；测试数据不含此类字符 |
| D4 | `hintsUsed > MAX_HINTS_PER_QUESTION` 分支被 `hintsUsed > 0` 完全覆盖，属冗余 | 一般 | 照抄参考实现以保持行为一致 |
| D5 | `gateCleared` 时未播种复习计划、未发 `review.scheduled` | **重要** | 属 `0010`（SM-2 调度）；已登记验证债 |
| D6 | 工具层的 mode 门控与 `ToolResult` 包装未实现 | 一般 | 属 `0017`；本 change 只做服务层与纯函数 |
| D7 | 工作区的 `.gitignore` 被意外删除，导致 `artifacts/`、`target/` 变成未跟踪文件 | 一般 | 已 `git checkout -- .gitignore` 恢复；两个目录重新被忽略 |
| D8 | `scripts/verify.ps1` 出现**两个** UTF-8 BOM（`EF BB BF EF BB BF`），PowerShell 报 `Unexpected attribute 'CmdletBinding'` | 一般 | 去重为单个 BOM 后复跑，全量 353 项通过。根因：多次用编辑工具改该文件时 BOM 被重复写入 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `review.scheduled` 与 SM-2 状态推进 | `0010` | 需调度器 |
| `assess`（定性评估） | `0009` | 本 change 不含定性通道 |
| `repairQuestion`（void / correct） | `0012` | 与修复流程同批 |
| 工具层 `mastery_quiz` / `mastery_grade` 的 `ToolResult` 与 mode 门控 | `0013` | 需回合循环与工具装配 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 353, Failures: 0` 来自
`artifacts/verify-0008-quiz-grade-20261104-143201.log`；参考实现对照见
`pytest tests/test_grading.py tests/test_study.py tests/test_interactions.py` 的 `16 passed`。
