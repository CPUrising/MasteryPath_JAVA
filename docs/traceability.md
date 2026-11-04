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
| TC-C6 | 声明掌握来源 learner | `specs/learning-domain` → `TC-C6` | `MasteryPolicyTest#overrideMarksLearnerSource` | 通过（`0004`） |
| TC-C7 | 声明不写分 | `specs/learning-domain` → `TC-C6` | `MasteryPolicyTest#overrideDoesNotWriteScore` | 通过（`0004`） |
| TC-C8 | 声明不产证据 | `specs/learning-domain` → `TC-C6` | `MasteryPolicyTest#overrideAddsNoEvidence` | 通过（`0004`） |
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
| TC-D1 | outline 禁 quiz | `specs/mode-gating` → `TC-D1` | `ModePolicyTest#outlineRejectsQuiz` | 未实现 |
| TC-D2 | study 禁 build | `specs/mode-gating` → `TC-D2` | `ModePolicyTest#studyRejectsBuild` | 未实现 |
| TC-D3 | 共享工具放行 | `specs/mode-gating` → `TC-D1` | `ModePolicyTest#sharedToolAllowed` | 未实现 |
| TC-D4 | 模式缺失不门控 | `specs/mode-gating` → `TC-D4` | `ModePolicyTest#unknownModeAllowsAll` | 未实现 |
| TC-D5 | 无大纲准入错误 | `specs/mode-gating` → `TC-D5` | `ModePolicyTest#admissionErrorNoOutline` | 未实现 |
| TC-D6 | 有大纲准入放行 | `specs/mode-gating` → `TC-D5` | `ModePolicyTest#admissionOkWithOutline` | 未实现 |
| TC-D7 | review 拒未掌握 | `specs/mode-gating` → `TC-K6` | `ModePolicyTest#reviewRejectsUnmastered` | 未实现 |
| TC-D8 | review 放行已掌握 | `specs/mode-gating` → `TC-K6` | `ModePolicyTest#reviewAllowsMastered` | 未实现 |
| TC-D9 | 分类完整性守卫 | `specs/mode-gating` → `TC-D9` | `ToolClassificationTest#guardComplete` | 未实现 |
| TC-L1 | 切换写会话模式 | `specs/mode-gating` → `TC-L1` | `SessionModeTest#switchPersists` | 未实现 |
| TC-L2 | 按钮切换持久化 | `specs/mode-gating` → `TC-L1` | `SessionModeRestTest#putModePersists` | 未实现 |
| TC-L3 | 被拒切换不持久 | `specs/mode-gating` → `TC-L3` | `SessionModeTest#rejectedNotPersisted` | 未实现 |
| TC-L4 | 重开会话恢复模式 | `specs/mode-gating` → `TC-L1` | `SessionModeTest#reopenRestores` | 未实现 |
| TC-M2 | study 禁改结构 | `specs/mode-gating` → `TC-M2` | `MasteryToolsTest#reviseBlockedInStudy` | 未实现 |
| TC-M6 | 工具预算 | `specs/mode-gating` → `TC-M6` | `AgentLoopTest#toolBudgetStops` | 未实现 |

## 4. 学习工具（learning-tools）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-J1 | 空路径 build | `specs/learning-tools` → `TC-J1` | `BuildReviseTest#buildOnEmptyPath` | 未实现 |
| TC-J2 | 非空重建需确认 | `specs/learning-tools` → `TC-J2` | `BuildReviseTest#rebuildRequiresConfirm` | 未实现 |
| TC-J3 | 重名拒绝 | `specs/learning-tools` → `TC-J1` | `BuildReviseTest#duplicateNamesRejected` | 未实现 |
| TC-J4 | revise 保进度 | `specs/learning-tools` → `TC-J4` | `BuildReviseTest#reviseKeepsProgress` | 未实现 |
| TC-J5 | revise 禁改类型 | `specs/learning-tools` → `TC-J4` | `BuildReviseTest#reviseRejectsTypeChange` | 未实现 |
| TC-J6 | 缺口上报 | `specs/learning-tools` → `TC-J6` | `BuildReviseTest#gapsReported` | 未实现 |
| TC-K1 | 注册校验 | `specs/learning-tools` → `TC-K1` | `QuizGradeTest#registrationValidation` | 未实现 |
| TC-K2 | 单槽位重放 | `specs/learning-tools` → `TC-K2` | `QuizGradeTest#singleSlotReplay` | 未实现 |
| TC-K3 | 判分幂等 | `specs/learning-tools` → `TC-K3` | `QuizGradeTest#gradingIdempotent` | 未实现 |
| TC-K4 | 未判定零变更 | `specs/learning-tools` → `TC-K4` | `QuizGradeTest#ungradedNoStateChange` | 未实现 |
| TC-K5 | 提示降级 | `specs/learning-tools` → `TC-K5` | `QuizGradeTest#hintsDowngrade` | 未实现 |
| TC-K6 | review 拒未掌握出题 | `specs/learning-tools` → `TC-K6` | `QuizGradeTest#reviewRejectsUnmastered` | 未实现 |
| TC-H1 | 正文唯一匹配 | `specs/learning-tools` → `TC-H1` | `GradingTest#bodyUniqueMatch` | 未实现 |
| TC-H2 | 歧义不判分 | `specs/learning-tools` → `TC-H2` | `GradingTest#ambiguousUngraded` | 未实现 |
| TC-H3 | 数字等价 | `specs/learning-tools` → `TC-H3` | `GradingTest#numericEquivalence` | 未实现 |
| TC-H4 | 模糊阈值 | `specs/learning-tools` → `TC-H4` | `GradingTest#fuzzyThreshold` | 未实现 |
| TC-H5 | open 最弱 | `specs/learning-tools` → `TC-H5` | `GradingTest#openPartialOnly` | 未实现 |
| TC-H6 | 提示降级判分 | `specs/learning-tools` → `TC-H6` | `GradingTest#hintDowngrade` | 未实现 |
| TC-M1 | 捕获不改地图 | `specs/learning-tools` → `TC-M1` | `CaptureTest#proposalDoesNotChangeMap` | 未实现 |
| TC-M3 | accept 后成为 KP | `specs/learning-tools` → `TC-M3` | `CaptureTest#acceptCreatesKp` | 未实现 |
| TC-M4 | 覆盖报告持久化 | `specs/learning-tools` → `TC-N1` | `CoverageTest#reportPersisted` | 未实现 |
| TC-M5 | 覆盖不达标 | `specs/learning-tools` → `TC-N2` | `OutlineEvalTest#coverageBelowThreshold` | 未实现 |
| TC-F2 | 目标缺口重生成无效 | `specs/learning-tools` → `TC-F2` | `GapTest#goalGapRegenIneffective` | 未实现 |
| TC-F3 | 来源遗漏重生成闭合 | `specs/learning-tools` → `TC-F2` | `GapTest#omissionRegenCloses` | 未实现 |
| TC-F4 | 接受缺口与消解 | `specs/learning-tools` → `TC-F4` | `GapTest#acceptThenResolve` | 未实现 |
| TC-F5 | 重建需确认 | `specs/learning-tools` → `TC-F2` | `GapTest#regenerateNeedsConfirm` | 未实现 |
| TC-N1 | 覆盖报告字段齐全 | `specs/learning-tools` → `TC-N1` | `CoverageTest#fieldsComplete` | 未实现 |
| TC-N2 | 未用来源进缺失 | `specs/learning-tools` → `TC-N2` | `CoverageTest#unusedSourceMissing` | 未实现 |
| TC-N3 | 未声明不展开 | `specs/learning-tools` → `TC-N1` | `CoverageTest#unreportedNoMissing` | 未实现 |
| TC-N4 | 含环检测 | `specs/learning-tools` → `TC-N2` | `CoverageTest#cycleDetected` | 未实现 |
| TC-N5 | 评测不达标非零退出 | `specs/learning-tools` → `TC-M5` | `OutlineEvalTest#belowMinF1Fails` | 未实现 |
| TC-N6 | 评测可复现 | `specs/learning-tools` → `TC-M5` | `OutlineEvalTest#deterministicRepeatable` | 未实现 |

## 5. 复习调度（review-scheduling）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-G1 | 间隔序列 | `specs/review-scheduling` → `TC-G1` | `SchedulerTest#intervalSequence` | 未实现 |
| TC-G2 | 失败进重学步 | `specs/review-scheduling` → `TC-G2` | `SchedulerTest#failureRelearning` | 未实现 |
| TC-G3 | 恢复清标志 | `specs/review-scheduling` → `TC-G3` | `SchedulerTest#recoveryClearsFlag` | 未实现 |
| TC-G4 | 会话内只 touch | `specs/review-scheduling` → `TC-G4` | `SchedulerTest#inSessionTouchOnly` | 未实现 |
| TC-G5 | 声明轨比值 | `specs/review-scheduling` → `TC-G5` | `SchedulerTest#learnerTrackRatio` | 未实现 |
| TC-G6 | 内部量隐藏 | `specs/review-scheduling` → `TC-G6` | `SchedulerTest#internalsHidden` | 未实现 |
| TC-G7 | 场景不追溯 | `specs/review-scheduling` → `TC-G7` | `SchedulerTest#scenarioNoRetroApply` | 未实现 |

## 6. API / 回合（agent-turn）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| TC-I1 | REST 模式门控 | `specs/agent-turn` → `TC-I1` | `LearningApiTest#putMapModeConflict` | 未实现 |
| TC-I2 | REST 重建需确认 | `specs/agent-turn` → `TC-I2` | `LearningApiTest#rebuildRequiresConfirm` | 未实现 |
| TC-I3 | 投影不含答案 | `specs/agent-turn` → `TC-I3` | `QuestionCardProjectionTest#hidesAnswer` | 未实现 |
| TC-I4 | 事件补齐 | `specs/agent-turn` → `TC-I4` | `EventsApiTest#sinceRecovers` | 未实现 |
| TC-I5 | 失败不产事件 | `specs/agent-turn` → `TC-I4` | `EventsApiTest#failureNoEvent` | 未实现 |
| TC-I6 | 字段级重建 | `specs/agent-turn` → `TC-I4` | `EventsApiTest#fieldLevelReplay` | 未实现 |
| TC-I7 | 声明有事件无证据 | `specs/agent-turn` → `TC-I7` | `LearningApiTest#overrideEventNoEvidence` | 未实现 |
| TC-E3 | 判分幂等（API） | `specs/learning-tools` → `TC-K3` | `LearningApiTest#gradeIdempotent` | 未实现 |
| TC-E4 | 公共投影 | `specs/agent-turn` → `TC-I3` | `StatusProjectionTest#noAnswerFields` | 未实现 |
| — | WS 事件顺序 | `specs/agent-turn` → `WS event order` | `WebSocketTurnTest#eventOrder` | 未实现 |
| — | 流失败保留文本 | `specs/agent-turn` → `Partial text survives stream failure` | `WebSocketTurnTest#partialTextOnError` | 未实现 |

## 7. 知识库（knowledge-base）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | 文件夹扫描为来源 | `specs/knowledge-base` → `Attach folder then build` | `SourceLoaderTest#scanFolder` | 未实现 |
| — | 引用解析 | `specs/knowledge-base` → `Attach folder then build` | `SourceLoaderTest#resolveByLabelAndBasename` | 未实现 |
| — | 检索不调模型 | `specs/knowledge-base` → `Search returns located chunks` | `KnowledgeServiceTest#searchNoLlm` | 未实现 |
| — | 旧来源回退摘要 | `specs/knowledge-base` → `Legacy source falls back to excerpt` | `ReadSourceTest#legacyExcerptFallback` | 未实现 |
| — | 条件挂载 | `specs/knowledge-base` → `No knowledge base means no rag tool` | `ToolMountingTest#ragOnlyWithKb` | 未实现 |

## 8. 设置与 Task Model（settings-task-model）

| ID | 场景 | 规范位置 | Java 测试 | 状态 |
| --- | --- | --- | --- | --- |
| — | 分组读写 | `specs/settings-task-model` → `Read and write a settings section` | `SettingsApiTest#readWriteSection` | 未实现 |
| — | 探测失败结构化 | `specs/settings-task-model` → `Probe failure is structured` | `ProviderProbeTest#failureStructured` | 未实现 |
| — | 无提供方回退 | `specs/settings-task-model` → `Fallback without provider` | `TaskModelTest#fallbackWithoutProvider` | 未实现 |
| — | 报错回退 | `specs/settings-task-model` → `Fallback on provider error` | `TaskModelTest#fallbackOnError` | 未实现 |

## 维护规则

- 新增或改名场景时，必须同步：规范标题、本表、Java 测试方法名。
- 归档前本表不得有「失败」；「未实现」必须在某个 proposal 中声明并落到 `docs/verification-debt.md`。
