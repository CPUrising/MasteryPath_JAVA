# 追踪矩阵（traceability）

> 把「验收场景 ↔ 规范位置 ↔ Java 测试方法 ↔ 状态」锁在一起。
> 场景编号沿用参考实现 `../MasteryPath/docs/` 的 `TC-*`，不另起编号。
> `状态=通过` 必须能点开到 `docs/verify/<change>.md` 的 PASS 行。
> 本表初始状态全部为「未实现」；每完成一个 change 由执行者回填。

## 图例

| 状态 | 含义 |
| --- | --- |
| 未实现 | 尚未编写对应 Java 测试 |
| 已实现待执行 | 测试已写但未在验证入口跑过 |
| 部分通过 | 同组内部分场景通过 |
| 通过 | 已在 `docs/verify/` 有 PASS 记录 |
| 失败 | 最近一次验证为 FAIL，且未归档 |

## 1. 领域模型与掌握门槛（learning-domain）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-A1 | 名称/类型校验 | `specs/learning-domain` → `TC-A1` | `KnowledgePointTest#nameTooShortRejectedOnConstruction` | 通过（`0002`） |
| TC-A2 | 未知类型拒绝 | `specs/learning-domain` → `TC-A2` | `KnowledgePointTest#unknownTypeRejected` | 通过（`0002`） |
| TC-A3 | 历史中文类型映射 | `specs/learning-domain` → `TC-A3` | `KnowledgePointTest#legacyChineseTypeMapped` | 通过（`0002`） |
| TC-A4 | 忽略未知字段 | `specs/learning-domain` → `TC-A4` | `KnowledgePointTest#unknownFieldsIgnored` | 通过（`0002`） |
| TC-A5 | 旧式选项解析 | `specs/learning-tools` → `TC-K1` | `PendingQuestionTest#legacyOptionStringsParsed` | 通过（`0002`） |
| TC-A6 | 聚合序列化往返 | `specs/learning-domain` → `TC-A4` | `LearningProgressJsonTest#roundTripKeepsAllFields` | 通过（`0002`） |
| TC-C1 | 量化门槛 0.85 未掌握 | `specs/learning-domain` → `TC-C1` | `MasteryPolicyTest#quantitativeBelowGate` | 通过（`0004`） |
| TC-C2 | 量化门槛 0.90 掌握 | `specs/learning-domain` → `TC-C1` | `MasteryPolicyTest#quantitativeAtGate` | 通过（`0004`） |
| TC-C3 | procedure 满分掌握 | `specs/learning-domain` → `TC-C1` | `MasteryPolicyTest#procedureFullMastery` | 通过（`0004`） |
| TC-C4 | concept 量化分不打开 | `specs/learning-domain` → `TC-C4` | `MasteryPolicyTest#conceptIgnoresScore` | 通过（`0004`） |
| TC-C5 | concept 定性通过掌握 | `specs/learning-domain` → `TC-C4` | `MasteryPolicyTest#conceptQualitativePass` | 通过（`0004`） |
| TC-C6 | 声明掌握来源 learner | `specs/learning-domain` → `TC-C6` | `MasteryOverrideServiceTest#overrideMarksLearnerSource` | 通过（`0006`） |
| TC-C7 | 声明不写分 | `specs/learning-domain` → `TC-C6` | `MasteryOverrideServiceTest#overrideDoesNotWriteScore` | 通过（`0006`） |
| TC-C8 | 声明不产证据 | `specs/learning-domain` → `TC-C6` | `MasteryOverrideServiceTest#overrideAddsNoEvidence` | 通过（`0006`） |
| TC-C9 | 下一目标取首个可选 | `specs/learning-domain` → `TC-C9` | `MasteryPolicyTest#nextObjectiveFirstEligible` | 通过（`0004`） |
| TC-C10 | 全掌握返回 complete | `specs/learning-domain` → `TC-C9` | `MasteryPolicyTest#nextObjectiveComplete` | 通过（`0004`） |
| TC-C11 | 待答题优先 | `specs/learning-domain` → `TC-C11` | `MasteryPolicyTest#nextObjectiveAnswerPending` | 通过（`0004`） |
| TC-C12 | 作废不计账 | `specs/learning-domain` → `TC-C12` | `MasteryPolicyTest#voidedAttemptIgnored` | 通过（`0004`） |
| TC-C13 | 三种状态派生 | `specs/learning-domain` → `TC-C9` | `MasteryPolicyTest#objectiveStatusBranches` | 通过（`0004`） |
| TC-C14 | map_summary 一致 | `specs/learning-domain` → `TC-C9` | `MasteryPolicyTest#mapSummaryMatchesGate` | 通过（`0004`） |
| TC-C15 | deferred 跳过 | `specs/learning-domain` → `TC-C9` | `MasteryPolicyTest#deferredSkipped` | 通过（`0004`） |
| TC-C16 | 类型只读 | `specs/learning-tools` → `TC-J5` | `MasteryPolicyTest#typeIsReadOnly` | 通过（`0004`） |
| TC-C17 | goal 保留 | `specs/learning-domain` → `TC-C17` | `GoalPreservationTest#goalSurvivesMutation` | 通过（`0004`） |
| TC-F1 | 模块阈值不推进 | `specs/learning-domain` → `TC-F1` | `MasteryPolicyTest#displayTargetNotGate` | 通过（`0004`） |
| TC-F6 | 前置硬门控 | `specs/learning-domain` → `TC-F6` | `MasteryPolicyTest#prerequisiteHardGate` | 通过（`0004`） |
| TC-F7 | 前置通过但未定性 | `specs/learning-domain` → `TC-F7` | `MasteryPolicyTest#prerequisitePassedButNoQualitative` | 通过（`0004`） |
| TC-F8 | 两条件相与 | `specs/learning-domain` → `TC-F8` | `MasteryPolicyTest#prerequisiteAndQualitative` | 通过（`0004`） |
| TC-F9 | 跨类型拆分 | `specs/learning-tools` → `TC-J3` | `LearningServiceTest#crossTypeSplitOnBuild` | 未实现 |

## 2. 存储与 CAS（learning-storage）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-B1 | 空库读取不建记录 | `specs/learning-storage` → `TC-B1` | `LearningStoreTest#loadMissingReturnsNullAndCreatesNothing` | 通过（`0003`） |
| TC-B2 | 读写往返 | `specs/learning-storage` → `TC-B2` | `LearningStoreTest#createThenLoad` | 通过（`0003`） |
| TC-B3 | 版本自增 | `specs/learning-storage` → `TC-B3` | `LearningStoreTest#mutateBumpsVersion` | 通过（`0003`） |
| TC-B4 | 旧版本被拒 | `specs/learning-storage` → `TC-B4` | `LearningStoreTest#staleExpectedVersionRejected` | 通过（`0003`） |
| TC-B5 | 回调异常回滚 | `specs/learning-storage` → `TC-B5` | `LearningStoreTest#callbackExceptionRollsBackStateAndEvents` | 通过（`0003`） |
| TC-B6 | workspace 隔离 | `specs/learning-storage` → `TC-B6` | `LearningStoreTest#workspaceIsolation` | 通过（`0003`） |
| TC-B7 | 事件带 revision | `specs/learning-storage` → `TC-B7` | `LearningStoreTest#eventCarriesPathAndRevision` | 通过（`0003`） |
| TC-B8 | 事件只追加 | `specs/learning-storage` → `TC-B8` | `LearningStoreTest#eventsAppendOnly` | 通过（`0003`） |
| TC-B9 | 可合并并发重试 | `specs/learning-storage` → `TC-B9` | `LearningStoreConcurrencyTest#mergeableConcurrentWritesAllApply` | 通过（`0003`） |
| TC-B10 | 前置失败不重试 | `specs/learning-storage` → `TC-B10` | `LearningStoreConcurrencyTest#preconditionNotRetriedAndNoWrite` | 通过（`0003`） |
| TC-B11 | 重试耗尽抛异常 | `specs/learning-storage` → `TC-B11` | `LearningStoreConcurrencyTest#casExhaustionThrowsAfterConfiguredRetries` | 通过（`0003`） |
| TC-B12 | 不同路径并发写 | `specs/learning-storage` → `TC-B12` | `LearningStoreConcurrencyTest#concurrentDifferentPaths` | 通过（`0003`） |
| TC-B13 | 同路径并发写 | `specs/learning-storage` → `TC-B13` | `LearningStoreConcurrencyTest#concurrentSamePathNeverLosesUpdate` | 通过（`0003`） |
| TC-B14 | 写中读不阻塞 | `specs/learning-storage` → `TC-B14` | `LearningStoreConcurrencyTest#readerOnSeparateConnectionNotBlockedDuringWrite` | 通过（`0003`） |
| TC-B15 | 状态与事件同事务 | `specs/learning-storage` → `TC-B15` | `LearningStoreTest#callbackExceptionRollsBackStateAndEvents` | 通过（`0003`） |
| TC-B16 | 一回合写两路径 | `specs/learning-storage` → `TC-B12` | `LearningStoreTest#sequentialTwoPaths` | 通过（`0003`） |
| TC-B17 | 跨 workspace 并发 | `specs/learning-storage` → `TC-B17` | `LearningStoreConcurrencyTest#crossWorkspaceConcurrent` | 通过（`0003`） |

## 3. 模式门控（mode-gating）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-D1 | outline 禁 quiz | `specs/mode-gating` → `TC-D1` | `ModeGateTest#quizRefusedInOutline` | 通过（`0005`） |
| TC-D2 | study 禁 build | `specs/mode-gating` → `TC-D2` | `ModeGateTest#buildRefusedInStudy` | 通过（`0005`） |
| TC-D3 | 共享工具放行 | `specs/mode-gating` → `TC-D1` | `ModeGateTest#sharedToolAllowedEverywhere` | 通过（`0005`） |
| TC-D4 | 模式缺失不门控 | `specs/mode-gating` → `TC-D4` | `ModeGateTest#unrecordedModeEnforcesNothing` | 通过（`0005`） |
| TC-D5 | 无大纲准入错误 | `specs/mode-gating` → `TC-D5` | `ModeGateTest#studyWithoutOutlineRefused` | 通过（`0005`） |
| TC-D6 | 有大纲准入放行 | `specs/mode-gating` → `TC-D5` | `ModeGateTest#studyWithOutlineAllowed` | 通过（`0005`） |
| TC-D7 | review 拒未掌握 | `specs/mode-gating` → `TC-K6` | `ModeGateTest#quizOwnedByStudyThenReview` | 部分通过（`0005`） |
| TC-D8 | review 放行已掌握 | `specs/mode-gating` → `TC-K6` | `ModeGateTest#reviewAllowsAssess` | 部分通过（`0005`） |
| TC-D9 | 分类完整性守卫 | `specs/mode-gating` → `TC-D9` | `ToolClassificationTest#defaultClassificationIsComplete` | 通过（`0005`） |
| TC-L1 | 切换写会话模式 | `specs/mode-gating` → `TC-L1` | `SessionModeTest#switchPersists` | 未实现（`0006`） |
| TC-L2 | 按钮切换持久化 | `specs/mode-gating` → `TC-L1` | `SessionModeRestTest#putModePersists` | 未实现（`0015`） |
| TC-L3 | 被拒切换不持久 | `specs/mode-gating` → `TC-L3` | `SessionModeTest#rejectedNotPersisted` | 未实现（`0006`） |
| TC-L4 | 重开会话恢复模式 | `specs/mode-gating` → `TC-L1` | `SessionModeTest#reopenRestores` | 未实现（`0006`） |
| TC-M2 | study 禁改结构 | `specs/mode-gating` → `TC-M2` | `MasteryToolsTest#reviseBlockedInStudy` | 未实现（`0007`） |
| TC-M6 | 工具预算 | `specs/mode-gating` → `TC-M6` | `AgentLoopTest#toolBudgetStops` | 部分通过（`0005`，仅常量） |

## 4. 学习工具（learning-tools）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-J1 | 空路径 build | `specs/learning-tools` → `TC-J1` | `OutlineBuildTest#buildOnEmptyPath` | 通过（`0007`） |
| TC-J2 | 非空重建需确认 | `specs/learning-tools` → `TC-J2` | `OutlineBuildTest#rebuildWithoutConfirmRefused` | 通过（`0007`） |
| TC-J3 | 重名拒绝 | `specs/learning-tools` → `TC-J1` | `OutlineBuildTest#duplicateNameRejected` | 通过（`0007`） |
| TC-J4 | revise 保进度 | `specs/learning-tools` → `TC-J4` | `OutlineReviseTest#addKpPreservesProgress` | 通过（`0007`） |
| TC-J5 | revise 禁改类型 | `specs/learning-tools` → `TC-J4` | `OutlineReviseTest#typeChangeRefused` | 通过（`0007`） |
| TC-J6 | 缺口上报 | `specs/learning-tools` → `TC-J6` | `OutlineBuildTest#buildReportsGaps` | 通过（`0007`） |
| TC-K1 | 注册校验 | `specs/learning-tools` → `TC-K1` | `QuizSpecTest#rejectsOptionsOnShort` | 通过（`0008`） |
| TC-K2 | 单槽位重放 | `specs/learning-tools` → `TC-K2` | `QuizRegistrationTest#secondRegistrationReturnsNull` | 通过（`0008`） |
| TC-E1 | 单槽位（存储层） | `specs/learning-tools` → `TC-K2` | `QuizRegistrationTest#secondRegistrationReturnsNull` | 通过（`0008`） |
| TC-E2 | 判分后可注册新题 | `specs/learning-tools` → `TC-E2` | `QuizRegistrationTest#canRegisterAfterGraded` | 通过（`0008`） |
| TC-K3 | 判分幂等 | `specs/learning-tools` → `TC-K3` | `GradeFlowTest#gradeIsIdempotent` | 通过（`0008`） |
| TC-K4 | 未判定零变更 | `specs/learning-tools` → `TC-K4` | `GradeFlowTest#ungradedLeavesNoTrace` | 通过（`0008`） |
| TC-K5 | 提示降级 | `specs/learning-tools` → `TC-K5` | `GradeFlowTest#hintsDowngradeToPartial` | 通过（`0008`） |
| TC-K6 | review 拒未掌握出题 | `specs/learning-tools` → `TC-K6` | `QuizGradeTest#reviewRejectsUnmastered` | 未实现（`0013`，工具层） |
| —（题库） | 判分入库自动关联知识点 | `../MasteryPath/tests/test_question_bank.py` | `QuestionBankTest#bankEntryCapturesGrading` | 通过（`0008`） |
| —（题库） | 标签**整体替换** | `service.py:1000` | `QuestionBankTest#taggingReplacesInsteadOfAppending` | 通过（`0011`，修正 `0008`） |
| —（题库） | 同批去重保序 | `service.py:1000` | `QuestionBankTest#taggingDeduplicatesWithinBatch` | 通过（`0011`） |
| —（题库） | 空白标签**保留** | `service.py:1000` | `QuestionBankTest#blankTagsPreserved` | 通过（`0011`，修正 `0008`） |
| —（题库） | 空列表清空 | `service.py:1000` | `QuestionBankTest#emptyListClearsTags` | 通过（`0011`） |
| —（题库） | `null` → `"None"` | `service.py:1000` | `QuestionBankTest#nullTagBecomesPythonNone` | 通过（`0011`） |
| —（题库） | 条目不存在**抛错** | `service.py:999` | `QuestionBankTest#tagMissingEntryThrows` | 通过（`0011`，修正 `0008`） |
| —（题库） | `tag` 真值语义（空白过滤） | `service.py:992` | `QuestionBankTest#blankTagStillFilters`、`#falsyTagReturnsAll` | 通过（`0011`，修正 `0008`） |
| TC-H1 | 正文唯一匹配 | `specs/learning-tools` → `TC-H1` | `GradingTest#choiceBodyUniqueMatch` | 通过（`0008`） |
| TC-H2 | 歧义不判分 | `specs/learning-tools` → `TC-H2` | `GradingTest#choiceUnparseableIsUngraded` | 通过（`0008`） |
| TC-H3 | 数字等价 | `specs/learning-tools` → `TC-H3` | `GradingTest#shortNumeric` | 通过（`0008`） |
| TC-H4 | 模糊阈值 | `specs/learning-tools` → `TC-H4` | `GradingTest#shortFuzzyThreshold` | 通过（`0008`） |
| TC-H5 | open 最弱 | `specs/learning-tools` → `TC-H5` | `GradingTest#openOptionalInsufficient` | 通过（`0008`） |
| TC-H6 | 提示降级判分 | `specs/learning-tools` → `TC-H6` | `GradeFlowTest#hintsDowngradeToPartial` | 通过（`0008`） |
| TC-M1 | 捕获不改地图 | `specs/learning-tools` → `TC-M1` | `CaptureTest#proposalDoesNotChangeMap` | 未实现 |
| TC-R1 | void 清除掌握与计划 | `service.py:467-483` | `RepairQuestionTest#voidRemovesMasteryAndMarksBank`、`#voidAbandonsInteraction` | 通过（`0012`） |
| TC-R2 | correct 重判恢复门槛 | `service.py:484-552` | `RepairQuestionTest#correctRegradesStoredAnswerAndRestoresGate`、`#correctMarksInteractionRepaired` | 通过（`0012`） |
| TC-R3 | 修复参数校验（action/reason/expected_answer） | `service.py:405-413` | `RepairQuestionTest#invalidActionRejected`、`#missingReasonRejected`、`#correctWithoutExpectedAnswerRejected` | 通过（`0012` 服务层 + `0017` 工具层） |
| TC-P1 | 画像合并与可见 | `service.py:184-234` | `LearnerProfileTest#mergesAndIsVisible`、`#mergeKeepsOmittedFields` | 通过（`0012`） |
| TC-P2 | 画像边界（白名单/枚举/无变更） | `service.py:197-214` | `LearnerProfileTest#noChangeEmitsNoEvent`、`#unknownKeysSilentlyDropped`、`#teachingStrategyValidated` | 通过（`0012`） |
| —（快照） | `get_state` 19 键 + `INV-07` 投影 | `service.py:236-297` | `PathStateTest`（11 项） | 通过（`0012` 服务层 + `0018` REST） |
| TC-M3 | accept 后成为 KP | `specs/learning-tools` → `TC-M3` | `QualitativeAssessTest#passClearsGate` | 通过（`0009`，定性评估侧） |
| TC-M4 | 覆盖报告持久化 | `specs/learning-tools` → `TC-N1` | `GapClassificationTest#getCoverageReturnsReport` | 通过（`0007`） |
| TC-M5 | 覆盖不达标 | `specs/learning-tools` → `TC-N2` | `EvalApiTest#evalReportsUnusedSource`（`coverage_ratio=0.5`、`passed=false`） | 通过（`0023`） |
| TC-F2 | 目标缺口重生成无效 | `specs/learning-tools` → `TC-F2` | `GapClassificationTest#regenerateDoesNotCloseGap` | 通过（`0007`） |
| TC-F3 | 来源遗漏重生成闭合 | `specs/learning-tools` → `TC-F2` | `GapClassificationTest#sourceOmissionClosesWhenUsed` | 通过（`0007`） |
| TC-F4 | 接受缺口与消解 | `specs/learning-tools` → `TC-F4` | `GapClassificationTest#acceptGapThenResolved` | 通过（`0007`） |
| TC-F5 | 重建需确认 | `specs/learning-tools` → `TC-F2` | `OutlineBuildTest#rebuildWithoutConfirmRefused` | 通过（`0007`） |
| TC-N1 | 覆盖报告字段齐全 | `specs/learning-tools` → `TC-N1` | `CoverageReportTest#unreportedCoverage` | 通过（`0007`） |
| TC-N2 | 未用来源进缺失 | `specs/learning-tools` → `TC-N2` | `CoverageReportTest#unusedSourceIsMissing` | 通过（`0007`） |
| TC-N3 | 未声明不展开 | `specs/learning-tools` → `TC-N1` | `CoverageReportTest#unreportedDoesNotExpandMissing` | 通过（`0007`） |
| TC-N4 | 含环检测 | `specs/learning-tools` → `TC-N2` | `CoverageReportTest#cycleDetected` | 通过（`0007`） |
| TC-N5 | 评测不达标非零退出 | `specs/learning-tools` → `TC-M5` | `OutlineEvalCliTest#failsBelowMinF1`、`#failsOnCycle`、`#failsOutOfBounds`、`#failsOnBaselineRegression` | 通过（`0023`） |
| TC-N6 | 评测可复现 | `specs/learning-tools` → `TC-M5` | `EvalApiTest#evalIsDeterministic`、`OutlineEvalTest#evaluationIsDeterministic` | 通过（`0023`） |
| — | 离线指标（令牌重叠代理） | `../MasteryPath/docs/09-outline-eval.md` §二 | `OutlineEvalTest`（15 项） | 通过（`0023`） |
| — | CLI 形态与参数 | `../MasteryPath/docs/09-outline-eval.md` §一 | `OutlineEvalCliTest`（8 项） | 通过（`0023`） |
| TC-S1 | 路径概览（9 键 / 跳过空 map / name 回退 / 升序） | `../MasteryPath/tests/test_path_binding.py`（`TC-S*`） | `PathBindingTest#listsBuiltPathsSorted`、`#overviewHasNineKeys`、`#emptyMapPathsHidden`、`#nameFallsBackToPathId` | 通过（`0013`） |
| TC-S2 | scratch 派生 / 认领 / 隐藏 | `service.py:99-130` | `PathBindingTest#scratchDerivedFromSession`、`#scratchSanitisesSessionId`、`#scratchFallsBackToDefault`、`#scratchClaimsOwnerWithoutEvent`、`#emptyScratchHiddenFromOverview` | 通过（`0013`） |
| TC-S3 | 会话绑定 | `service.py:132-136` | `PathBindingTest#bindMovesSession`、`#bindEmptySessionIsNoop` | 通过（`0013` 服务层 + `0017` 工具层拒绝） |
| TC-S4 | 删除会话与空 scratch 级联 | `service.py:1014-1029` | `PathBindingTest#deleteSessionRemovesOwnedEmptyScratch`、`#deleteSessionKeepsPathWithKnowledgePoints`、`#deleteSessionKeepsForeignPath`、`#deleteSessionKeepsPathWithOtherSessions` | 通过（`0013`） |
| TC-S5 | 回合内切换路径持久化 | `../MasteryPath/tests/test_turn_path_switch.py` | `TurnRunnerTest#switchPathInsideTurnPersistsBinding` | 通过（`0019`） |
| TC-NAV1 | 课程卡片（筛选 / 截断 / 条件键） | `learning/navigation.py:41-83` | `NavigationTest#listsBuiltTopics`、`#queryFiltersByHaystack`、`#haystackExcludesPathId`、`#queryKeyOnlyWhenTruthy`、`#modulesTruncated`、`#topicsTruncated` | 通过（`0014`） |
| TC-NAV2 | 单条课程（空 map 视为不存在） | `navigation.py:86-104` | `NavigationTest#findTopicReturnsCard`、`#findTopicEmptyMapIsNull`、`#findTopicMissingIsNull`、`#findTopicStripsPathId` | 通过（`0014`） |
| TC-NAV3 | 模块解析（id / 名称 / 1 基序号） | `navigation.py:107-122` | `NavigationTest#resolveModuleById`、`#resolveModuleByName`、`#resolveModuleByOrder`、`#resolveModuleNoMatch` | 通过（`0014`） |
| TC-NAV4 | 会话列表（archived 过滤 / awaiting 复合 / 截断） | `navigation.py:125-159` | `NavigationTest#listsSessions`、`#skipsArchivedSessions`、`#awaitingAnswerIsComposite`、`#sessionsTruncated` | 通过（`0014`） |
| TC-NAV5 | hand-off 卡片 17 键 | `nav_tools.py:43-70` | `NavigationTest#handoffHasSeventeenKeys`、`#handoffDefaults`、`#navigationIsReadOnly` | 通过（`0014` 服务层 + `0017` 工具层） |

## 5. 复习调度（review-scheduling）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-G1 | 间隔序列 | `specs/review-scheduling` → `TC-G1` | `SchedulerTest#intervalSequence`、`#longSequence`、`#sequenceWithOtherEaseFactors`、`#roundsHalfToEven` | 通过（`0010`） |
| TC-G2 | 失败进重学步 | `specs/review-scheduling` → `TC-G2` | `SchedulerTest#failureRelearning`、`ReviewSchedulingTest#reviewFailureSetsFlag` | 通过（`0010`） |
| TC-G3 | 恢复清标志 | `specs/review-scheduling` → `TC-G3` | `SchedulerTest#recoveryClearsFlag`、`ReviewSchedulingTest#failureThenSuccessClearsFlag` | 通过（`0010`） |
| TC-G4 | 会话内只 touch | `specs/review-scheduling` → `TC-G4` | `SchedulerTest#inSessionTouchOnly`、`ReviewSchedulingTest#inSessionPracticeKeepsAnchor` | 通过（`0010`） |
| TC-G5 | 声明轨比值 | `specs/review-scheduling` → `TC-G5` | `SchedulerTest#learnerTrackRatio`、`ReviewSchedulingTest#overrideSeedsLearnerTrack` | 通过（`0010`） |
| TC-G6 | 内部量隐藏 | `specs/review-scheduling` → `TC-G6` | `ReviewSchedulingTest#schedulerInternalsNotExposed` | **部分**（`0010`，仅公开签名；准入拒绝属 `0017`） |
| TC-G7 | 场景不追溯 | `specs/review-scheduling` → `TC-G7` | `SchedulerTest#desiredRetentionNotConsumed`、`#changingRetentionKeepsAnchor`、`LearningApiTest#reviewSettingsClampsToRange` | 通过（`0010` + `0018` 端到端） |

## 6. API / 回合（agent-turn）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-I1 | REST 模式门控 | `specs/agent-turn` → `TC-I1` | `LearningApiTest#mapWriteRequiresOutlineMode`、`#mapWriteWithoutRecordedModeIsAllowed` | 通过（`0018`） |
| TC-I2 | REST 重建需确认 | `specs/agent-turn` → `TC-I2` | `LearningApiTest#rebuildRequiresConfirm`、`#rebuildWithConfirmReplacesMap` | 通过（`0018`） |
| TC-I3 | 投影不含答案 | `specs/agent-turn` → `TC-I3` | `QuestionCardProjectorTest#hidesAnswerAndExplanation` | 通过（`0006`） |
| TC-I4 | 事件补齐 | `specs/agent-turn` → `TC-I4` | `LearningApiTest#eventsSinceFiltersByRevision`、`#eventsForUnknownPathIsEmptyList` | 通过（`0018`） |
| TC-I5 | 失败不产事件 | `specs/agent-turn` → `TC-I4` | `LearningApiTest#failedMapWriteEmitsNoEvent` | 通过（`0018`） |
| TC-I6 | 字段级重建 | `specs/agent-turn` → `TC-I4` | `LearningApiTest#mapWriteWithOpsRevises` | 通过（`0018`，`revise` 差分路径） |
| TC-I7 | 声明有事件无证据 | `specs/agent-turn` → `TC-I7` | `MasteryOverrideServiceTest#overrideEmitsEvent`、`LearningApiTest#overrideKnownObjectiveSucceeds` | 通过（`0006` 服务层 + `0018` REST 侧） |
| TC-E3 | 判分幂等（API） | `specs/learning-tools` → `TC-K3` | `GradeFlowTest#gradeIsIdempotent` | 通过（`0008`，服务层；REST 待 `0014`） |
| TC-E4 | 公共投影 | `specs/agent-turn` → `TC-I3` | `QuestionCardProjectorTest#projectionHasExactlySevenKeys` | 通过（`0006`） |
| — | WS 事件顺序 | `specs/agent-turn` → `WS event order` | `TurnRunnerTest#masteryTurnUsesSharedRunner`、`WsTurnTest#streamsMasteryTurnFrameByFrame` | 通过（`0019`） |
| — | 流失败保留文本 | `specs/agent-turn` → `Partial text survives stream failure` | `TurnRunnerTest#keepsPartialTextWhenStreamFails` | 通过（`0019`） |
| — | 题卡派生（`INV-07` 投影） | `specs/agent-turn` → `question_card` | `TurnRunnerTest#derivesQuestionCardFromToolResult` | 通过（`0019`） |
| — | `session_meta` 时机（`turn_end` 之后） | `specs/agent-turn` → `session_meta` | `TurnRunnerTest#modeSwitchEmitsSessionMeta`、`#noSessionMetaWhenNothingChanged` | 通过（`0019`） |
| — | 错误映射（`LlmError` / 其他异常） | `specs/agent-turn` → `error` | `TurnRunnerTest#mapsLlmError`、`#mapsUnexpectedException` | 通过（`0019`） |
| — | 快照与参考块注入 | `specs/agent-turn` → `extra_context` | `TurnRunnerTest#injectsKnowledgeContextAndSnapshot`、`#snapshotFailureDoesNotSinkTheTurn` | 通过（`0019`） |
| — | 增量正文多帧 | `specs/agent-turn` → `text_delta` | `TurnRunnerTest#forwardsIncrementalTextFrames`、`WsTurnTest#forwardsIncrementalTextFrames` | 通过（`0019`） |
| TC-CN1 | 普通聊天调只读导航工具 + `metadata` 透出 | `../MasteryPath/tests/test_chat_nav.py` | `ChatServiceTest#navToolCallAndHandoffSurface` | 通过（`0020`） |
| — | 聊天回合落库与标题填充 | `chat/service.py:85-188` | `ChatServiceTest#turnPersistsMessagesAndTitle`、`ChatApiTest#streamPersistsMessages` | 通过（`0020`） |
| — | 聊天 SSE 帧格式 | `api/chat_routes.py:59-80` | `ChatApiTest#streamPersistsMessages`、`#streamSetsNoBufferingHeaders` | 通过（`0020`） |
| — | 聊天空消息 / 未知会话短路 | `chat/service.py:93-101` | `ChatServiceTest#blankMessageReportsErrorWithoutPersisting`、`#unknownSessionReportsError` | 通过（`0020`） |
| — | 无模型配置的结构化错误 | `model_resolution.py:72-75` | `ModelResolutionTest#noModelConfigured`、`ChatServiceTest#missingModelReportsStructuredError` | 通过（`0020`） |
| — | 聊天会话偏好合并 + legacy `model_id` | `chat/store.py:155-184` | `ChatStoreTest#preferencesMergeAndMirrorModelId` | 通过（`0020`） |
| — | 聊天库 DDL 与独立文件 | `chat/store.py:13-32` | `ChatStoreTest#schemaMatchesReference`、`#usesSeparateDatabaseFile` | 通过（`0020`） |
| TC-LLM1 | SSE 解析（文本增量 / 分片拼装 / `[DONE]` / 坏参数） | `agents/llm.py:92-158` | `SseParserTest`（10 项） | 通过（`0015`） |
| TC-LLM2 | 非流式客户端（tools / tool_choice / 错误映射） | `agents/llm.py:303-361` | `OpenAiCompatibleClientTest#chatSendsToolsAndParsesCalls`、`#chatMarksInvalidArguments`、`#authErrorIsStructuredAndSafe`、`#httpErrorBranches` | 通过（`0015`） |
| TC-LLM3 | 请求头与端点拼接 | `agents/llm.py:245-295` | `OpenAiCompatibleClientTest#opencodeSessionHeader`、`#explicitHeadersWin`、`#bearerHeaderOnlyWithKey`、`#chatEndpointIdempotent` | 通过（`0015`） |
| TC-LLM4 | 流式客户端与 Fake | `agents/llm.py:167-212, 363-396` | `OpenAiCompatibleClientTest#streamSendsStreamFlagAndParses`、`FakeLlmClientTest`（7 项） | 通过（`0015`） |
| — | 工具 schema 完整性 | `capabilities/mastery/tools.py:911-1140` | `ToolSchemasTest#everyToolHasSchema`、`#requiredParametersMatchExecutors` | 未实现（`0017`） |
| TC-LOOP1 | 回合基本流程（`done` / 提示词框定 / `extraContext`） | `agents/loop.py:30-40, 68-109` | `AgentLoopTest#finishesWithDone`、`#executesToolThenContinues`、`#systemPromptFramesMode`、`#extraContextAppended` | 通过（`0016`） |
| TC-LOOP2 | 工具定义按名字升序 | `tools.py:1123-1140` | `AgentLoopTest#toolDefinitionsSorted` | 通过（`0016`） |
| TC-LOOP3 | 回合预算（`INV-60`） | `loop.py:129-134` | `AgentLoopTest#budgetStopsBeforeExecuting`、`#budgetAccumulatesAcrossRounds` | 通过（`0016`） |
| TC-LOOP4 | 出题结束回合（`INV-44`）+ 轮数耗尽 | `loop.py:170-175` | `AgentLoopTest#endTurnYieldsCardAfterResult`、`#stopsAtMaxRounds` | 通过（`0016`） |
| TC-LOOP5 | 工具失败三降级 + OpenAI 工具历史 | `loop.py:139-169` | `AgentLoopTest#badArgumentsDegrade`、`#unknownToolDegrades`、`#raisingToolDegrades`、`#writesOpenAiToolHistory` | 通过（`0016`） |
| TC-TOOL1 | 工具 schema 完整性（23 份 + 枚举 + required） | `tools.py:911-1140` | `ToolSchemasTest`（5 项） | 通过（`0017`） |
| TC-TOOL2 | 注册表 + 调用期门控（含未记录模式放行） | `tools.py:57-61, 1144-1167` | `MasteryToolsTest#registryIsComplete`、`#buildRefusedInStudy`、`#quizRefusedInOutline`、`#unrecordedModeEnforcesNothing`、`#sharedToolsAlwaysAllowed` | 通过（`0017`） |
| TC-TOOL3 | 服务层异常 → `ToolResult(ok=false)` | `tools.py:715-799` | `MasteryToolsTest#repairToolConvertsException`、`#repairToolRequiresReason`、`#profileToolRecordsPartialFields`、`#profileToolRejectsEmpty` | 通过（`0017`） |
| TC-TOOL4 | 条件挂载（`rag` / `read_source`） | `tools.py:378-385` | `MasteryToolsTest#conditionalMounting`、`#mountsSourceToolsWhenInputsExist`、`#mountingDoesNotMutateRegistry` | 通过（`0017`） |
| TC-TOOL5 | 状态快照 + 模式切换 + 出题判分 | `tools.py:64-195, 508-657` | `StatusAndQuizToolsTest`（17 项） | 通过（`0017`） |
| TC-TOOL6 | 来源工具 + 导航工具 | `tools.py:289-375`、`nav_tools.py` | `SourceToolsTest`（10 项）、`MasteryToolsTest#openSessionFromAnotherTopicRefused`、`#newSessionOnSameTopicRefused`、`#navFailureMessages` | 通过（`0017`） |
| TC-REST1 | 路径 CRUD + 地图写入 + 重建 + override(412) + 缺口(422) | `api/app.py:133-275` | `LearningApiTest`（36 项） | 通过（`0018`） |
| TC-REST2 | 会话 CRUD + 模式三级校验 + 偏好合并 | `api/app.py:470-528` | `SessionApiTest`（14 项） | 通过（`0018`） |
| TC-REST3 | REST 学习端点复用工具层（`_run_tool`） | `api/app.py:531-552` | `StudyApiTest`（10 项） | 通过（`0018`） |
| TC-REST4 | 设置分组读写 + 归一 | `settings/store.py`、`api/app.py:390-406` | `SettingsApiTest`（9 项） | 通过（`0018`） |
| TC-REST5 | CORS 字面量 `*` | `api/app.py:37-48` | `CorsApiTest`（1 项） | 通过（`0018`） |

## 7. 知识库（knowledge-base）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | 文件夹扫描为来源 | `specs/knowledge-base` → `Attach folder then build` | `FolderScannerTest#scanFindsSupportedFilesOnly`、`KnowledgeApiTest#attachFolderSources` | 通过（`0021`） |
| — | 引用解析（标签 / 唯一基名） | `specs/knowledge-base` → `Attach folder then build` | `PathBindingTest`（`0013` 的 `SourceResolver`） | 通过（`0013`） |
| — | 检索不调模型 | `specs/knowledge-base` → `Search returns located chunks` | `KnowledgeServiceTest#searchReturnsRankedHits`、`VectorIndexerTest#searchRanksBySimilarity` | 通过（`0021`） |
| — | 旧来源回退摘要 | `specs/knowledge-base` → `Legacy source falls back to excerpt` | `TurnRunnerTest#snapshotFailureDoesNotSinkTheTurn`（文件读不到退回 excerpt） | 通过（`0019`） |
| — | 条件挂载 | `specs/knowledge-base` → `No knowledge base means no rag tool` | `SourceToolsTest#mountedOnlyWhenInputsExist` | 通过（`0017`） |
| — | 知识库 CRUD 与索引 | `knowledge/service.py:127-245` | `KnowledgeServiceTest`（17 项）、`KnowledgeApiTest`（19 项） | 通过（`0021`） |
| — | 上传路径安全 | `knowledge/store.py:84-107` | `KnowledgeStoreTest#uploadTraversalRejected`、`KnowledgeApiTest#uploadTraversalIs422` | 通过（`0021`） |
| — | 参考块注入 | `knowledge/context.py` | `KnowledgeServiceTest#knowledgeContextBuildsBlock` | 通过（`0021`） |

## 8. 设置与 Task Model（settings-task-model）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | 分组读写 | `specs/settings-task-model` → `Read and write a settings section` | `SettingsApiTest#providersRoundtrip`、`SettingsStoreTest#writePersistsToDisk` | 通过（`0018` / `0020` 落盘） |
| — | 探测失败结构化 | `specs/settings-task-model` → `Probe failure is structured` | `ProviderProbeTest#probeStatusVocabulary`、`#probeInvalidUrlAndAnthropic` | 通过（`0022`） |
| — | 探测不写配置 / 不回显凭据 | `providers.py` 文件头注释三条约束 | `ProviderProbeTest#probeConnectedAndClassifies`、`SettingsProbeApiTest#probeEndpointDoesNotWriteSettings` | 通过（`0022`） |
| — | 掩码回退（用已保存凭据） | `api/app.py:409-433` `_probe_fields` | `SettingsProbeApiTest#probeEndpointUsesSavedCredentials`、`#probeEndpointPrefersSubmittedFields` | 通过（`0022`） |
| — | 无提供方回退 | `specs/settings-task-model` → `Fallback without provider` | `TaskModelTest#fallbackWithoutClient`、`SettingsProbeApiTest#suggestQuestionReturnsDraft` | 通过（`0022`） |
| — | 报错回退 | `specs/settings-task-model` → `Fallback on provider error` | `TaskModelTest#fallsBackWhenLlmRaises`、`#fallsBackWhenTextBlank` | 通过（`0022`） |

## 9. 普通聊天会话（chat-session）

> 本能力由 `0026` 新增，参考实现没有对应 `TC-*`；场景名直接取自
> `openspec/changes/0026-chat-stream-withdraw/specs/chat-session/spec.md`。

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | Incremental delta delivery | `changes/0026-chat-stream-withdraw/specs/chat-session` | `OpenAiCompatibleClientTest#streamChatIsLazy` | 通过（`0026`） |
| — | Turn persists transcript and ends | `changes/0026-chat-stream-withdraw/specs/chat-session` | `ChatApiTest#streamPersistsMessages` | 通过（`0026`） |
| — | Partial text survives upstream failure | `changes/0026-chat-stream-withdraw/specs/chat-session` | `ChatServiceTest#keepsPartialTextOnError` | 通过（`0026`） |
| — | Withdraw truncates tail | `changes/0026-chat-stream-withdraw/specs/chat-session` | `ChatApiTest#withdrawTruncatesTail`、`ChatStoreTest#deleteMessagesFromTruncatesTail` | 通过（`0026`） |
| — | Withdraw keeps earlier history | `changes/0026-chat-stream-withdraw/specs/chat-session` | `ChatStoreTest#deleteMessagesFromKeepsEarlier` | 通过（`0026`） |
| — | Unknown session | `changes/0026-chat-stream-withdraw/specs/chat-session` | `ChatApiTest#withdrawTruncatesTail` | 通过（`0026`） |
| — | Repeated withdrawal is idempotent | `changes/0026-chat-stream-withdraw/specs/chat-session` | `ChatApiTest#withdrawTruncatesTail` | 通过（`0026`） |

## 10. 题卡事件与作答反馈（0031）

> 本能力由 `0031` 新增/细化；场景名取自
> `openspec/changes/0031-quiz-card-event-authority/specs/`。

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | Card event carries turn context | `changes/0031-quiz-card-event-authority/specs/agent-turn` | `TurnRunnerTest#derivesQuestionCardFromToolResult` | 通过（`0031`） |
| TC-I3 | Event projection hides answers | `changes/0031-quiz-card-event-authority/specs/agent-turn` | `TurnRunnerTest#derivesQuestionCardFromToolResult`、`QuestionCardProjectorTest` | 通过（`0031`） |
| — | Reload restores card from history | `changes/0031-quiz-card-event-authority/specs/agent-turn` | `TurnRunnerTest#derivesQuestionCardFromToolResult` + 前端手测 | 通过（`0031`） |
| — | Reload does not duplicate card | `changes/0031-quiz-card-event-authority/specs/agent-turn` | 前端手测（browser_use） | 通过（`0031`） |
| — | Natural-language short needs accepted answers | `changes/0031-quiz-card-event-authority/specs/learning-tools` | `StudyApiTest#naturalLanguageShortNeedsAcceptedAnswers` | 通过（`0031`） |
| — | Numeric short is exempt | `changes/0031-quiz-card-event-authority/specs/learning-tools` | `StudyApiTest#numericShortIsAllowedWithoutAcceptedAnswers` | 通过（`0031`） |
| — | Graded feedback persists | `changes/0031-quiz-card-event-authority/specs/learning-tools` | `StudyApiTest#gradeFeedbackPersistsInSessionMessages` | 通过（`0031`） |
| — | Ungraded persists with zero change | `changes/0031-quiz-card-event-authority/specs/learning-tools` | `StudyApiTest#ungradedGradePersistsWithoutMasteryChange` | 通过（`0031`） |
| — | Repeated grade does not duplicate messages | `changes/0031-quiz-card-event-authority/specs/learning-tools` | `StudyApiTest#repeatedGradeDoesNotDuplicateMessages` | 通过（`0031`） |

## 11. 生成型练习题卷（0033）

> 本能力由 `0033` 新增；场景名取自
> `openspec/changes/0033-quiz-set-generation/specs/`。

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | Quiz set persists with items | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetStoreTest#savesAndReadsQuizSet` | 通过（`0033`） |
| — | Deleting a path cascades quiz sets | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetStoreTest#deletePathCascadesQuizSets` | 通过（`0033`） |
| — | Unsubmitted set hides answers | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetProjectorTest`、`QuizSetApiTest#getHidesAnswersBeforeSubmit` | 通过（`0033`） |
| — | Submit releases answers and is idempotent | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetApiTest#submitReleasesAnswersAndIsIdempotent` | 通过（`0033`） |
| — | Ungraded submit leaves no trace | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetServiceTest#ungradedLeavesNoTrace` | 通过（`0033`） |
| — | Practice does not change mastery | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetServiceTest#submitGradesAndLeavesMasteryUntouched` | 通过（`0033`） |
| — | Empty answers rejected | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetApiTest#emptyAnswersIs422` | 通过（`0033`） |
| — | Unknown set or wrong path is 404 | `changes/0033-quiz-set-generation/specs/quiz-set` | `QuizSetApiTest#unknownSetOrWrongPathIs404` | 通过（`0033`） |
| — | Deep question turn emits plan/questions/set | `changes/0033-quiz-set-generation/specs/agent-turn` | `QuizSetTurnTest#generatesQuizSetWithEvents` | 通过（`0033`） |
| — | Invalid generation fails closed | `changes/0033-quiz-set-generation/specs/agent-turn` | `QuizSetTurnTest#invalidGenerationFailsClosed` | 通过（`0033`） |
| — | Generation repairs once | `changes/0033-quiz-set-generation/specs/agent-turn` | `QuizSetTurnTest#repairsOnce` | 通过（`0033`） |
| — | Unknown capability throws | `changes/0033-quiz-set-generation/specs/agent-turn` | `QuizSetTurnTest#unknownCapabilityThrows` | 通过（`0033`） |

## 12. 练习题卷前端（0034）

> 本能力由 `0034` 新增；场景名取自
> `openspec/changes/0034-quiz-viewer-ui/specs/quiz-viewer/spec.md`。
> 前端场景以 browser_use 与真实模型 E2E 作为验证证据，见 `docs/verify/0034-quiz-viewer-ui.md`。

| ID | 场景 | 规范位置 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| — | Learner starts a practice turn | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | browser_use + 真实模型 E2E（`capability=deep_question`、3 题） | 通过（`0034`） |
| — | Capability resets after the turn | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | browser_use（回合结束后回到「掌握辅导」） | 通过（`0034`） |
| — | Quiz set renders inline | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | browser_use（3 题单卷）+ 真实模型 E2E | 通过（`0034`） |
| — | Generation failure leaves no quiz | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | `QuizSetTurnTest#invalidGenerationFailsClosed` + 前端 error 分支 | 通过（`0034`） |
| — | Successful submit reveals answers | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | browser_use（得分 100%）+ 真实模型 E2E（得分 0.667） | 通过（`0034`） |
| — | Ungraded keeps answers editable | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | 真实模型 E2E（`ungraded` 响应）+ `QuizViewer` 分支 | 通过（`0034`） |
| — | Reload restores an open quiz | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | browser_use（刷新后恢复题卷与作答入口） | 通过（`0034`） |
| — | Reload restores a submitted quiz | `changes/0034-quiz-viewer-ui/specs/quiz-viewer` | browser_use（刷新后复盘与得分保留） | 通过（`0034`） |

## 13. 练习题生成上下文（0035）

> 本能力由 `0035` 新增；场景名取自
> `openspec/changes/0035-quiz-generation-context/specs/quiz-set/spec.md`。

| ID | 场景 | 规范位置 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| — | Prompt carries path and knowledge points | `changes/0035-quiz-generation-context/specs/quiz-set` | `QuizSetTurnTest#promptIncludesPathContext` | 通过（`0035`） |
| — | Requests without an explicit topic still stay on-path | `changes/0035-quiz-generation-context/specs/quiz-set` | 真实模型复验（模糊请求生成线程状态题） | 通过（`0035`） |
| — | Context never leaks grading data | `changes/0035-quiz-generation-context/specs/quiz-set` | `QuizSetTurnTest#promptIncludesPathContext`（断言无 `expected_answer` / `mastery`） | 通过（`0035`） |

## 14. 掌握式判分与掌握度（0036）

> 本能力由 `0036` 修改；场景名取自
> `openspec/changes/0036-mastery-grading-parity/specs/`。

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | Unparseable answer is incorrect | `changes/0036-mastery-grading-parity/specs/learning-tools` | `MasteryGradingTest#emptyExpectedIsIncorrect`、`GradeFlowTest#unparseableChoiceIsIncorrect` | 通过（`0036`） |
| — | Choice compares normalized labels only | `changes/0036-mastery-grading-parity/specs/learning-tools` | `MasteryGradingTest#choiceComparesNormalizedLabel` | 通过（`0036`） |
| — | Short answer matching | `changes/0036-mastery-grading-parity/specs/learning-tools` | `MasteryGradingTest#shortExactMatch`、`#shortFuzzyThreshold`、`#longShortOnlyExact` | 通过（`0036`） |
| — | Open answer keyword ratio | `changes/0036-mastery-grading-parity/specs/learning-tools` | `MasteryGradingTest#openKeywordRatio`、`#openSupportsCjkDelimiters` | 通过（`0036`） |
| — | Hints do not downgrade | `changes/0036-mastery-grading-parity/specs/learning-tools` | `GradeFlowTest#hintsDoNotDowngradeButAreNotIndependent` | 通过（`0036`） |
| — | Single correct answer is capped | `changes/0036-mastery-grading-parity/specs/learning-domain` | `MasteryScoreTest#singleCorrectIsCapped`、`GradeFlowTest#singleCorrectDoesNotClearGate` | 通过（`0036`） |
| — | Two correct answers are capped | `changes/0036-mastery-grading-parity/specs/learning-domain` | `MasteryScoreTest#twoCorrectAreCapped` | 通过（`0036`） |
| — | Three correct answers clear the gate | `changes/0036-mastery-grading-parity/specs/learning-domain` | `MasteryScoreTest#threeCorrectReachOne`、`GradeFlowTest#correctAnswerClearsGate` | 通过（`0036`） |
| — | Hints and voids are excluded | `changes/0036-mastery-grading-parity/specs/learning-domain` | `MasteryScoreTest#voidedAttemptsExcluded`、`#nonIndependentAttemptsExcluded` | 通过（`0036`） |
| — | Recency weights recover from early mistakes | `changes/0036-mastery-grading-parity/specs/learning-domain` | `MasteryScoreTest#recentFiveWeights`、`#onlyLastFiveCount`、`#recoveryRaisesScore` | 通过（`0036`） |

## 15. 题卡与回合协议（0037）

> 本能力由 `0037` 新增；场景名取自
> `openspec/changes/0037-mastery-turn-protocol/specs/`。

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | Answer is graded before the tutor speaks | `changes/0037-mastery-turn-protocol/specs/agent-turn` | `TurnRunnerTest#gradesCardAnswerBeforeTheTutorSpeaks` | 通过（`0037`） |
| — | Answer text is the user message | `changes/0037-mastery-turn-protocol/specs/agent-turn` | `WsTurnTest#gradesCardAnswerOverTheSocket` | 通过（`0037`） |
| — | Skip clears the card before the tutor speaks | `changes/0037-mastery-turn-protocol/specs/agent-turn` | `StatusAndQuizToolsTest#skipWithoutArgumentsClearsOpenQuestion` | 通过（`0037`） |
| — | History is included | `changes/0037-mastery-turn-protocol/specs/agent-turn` | `TurnRunnerTest#gradesCardAnswerBeforeTheTutorSpeaks` | 通过（`0037`） |
| — | Correct body keeps its label | `changes/0037-mastery-turn-protocol/specs/learning-tools` | `StatusAndQuizToolsTest#shuffledChoiceKeepsExpectedBodyAndAllowsFreeText` | 通过（`0037`） |
| — | Card allows free text | `changes/0037-mastery-turn-protocol/specs/learning-tools` | `StatusAndQuizToolsTest#shuffledChoiceKeepsExpectedBodyAndAllowsFreeText` | 通过（`0037`） |
| — | Grade result carries the answer key | `changes/0037-mastery-turn-protocol/specs/learning-tools` | `StatusAndQuizToolsTest#gradeSucceeds`、`TurnRunnerTest#gradesCardAnswerBeforeTheTutorSpeaks` | 通过（`0037`） |
| — | Grade without question id | `changes/0037-mastery-turn-protocol/specs/learning-tools` | `StatusAndQuizToolsTest#shuffledChoiceKeepsExpectedBodyAndAllowsFreeText` | 通过（`0037`） |
| — | Skip without arguments | `changes/0037-mastery-turn-protocol/specs/learning-tools` | `StatusAndQuizToolsTest#skipWithoutArgumentsClearsOpenQuestion`、`ToolSchemasTest#requiredParametersMatchExecutors` | 通过（`0037`） |

## 维护规则

- 新增或改名场景时，必须同步：规范标题、本表、Java 测试方法名。
- 归档前本表不得有「失败」；「未实现」必须在某个 proposal 中声明并落到 `docs/verification-debt.md`。
