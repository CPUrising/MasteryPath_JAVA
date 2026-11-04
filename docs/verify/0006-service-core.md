# 验证报告：0006-service-core

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0006-service-core` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 13:40:02（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0006-service-core` |
| 日志 | `artifacts/verify-0006-service-core-20261104-134002.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Mastery Gate | TC-C6 声明来源 learner | `MasteryOverrideServiceTest#overrideMarksLearnerSource` | PASS | `INV-03` |
| Mastery Gate | TC-C7 声明不写分 | `MasteryOverrideServiceTest#overrideDoesNotWriteScore` | PASS | `INV-01`；分数保持 0.1 |
| Mastery Gate | TC-C8 声明不产证据 | `MasteryOverrideServiceTest#overrideAddsNoEvidence` | PASS | `INV-15` |
| Mastery Gate | `gate_cleared` 未掌握→true | `MasteryOverrideServiceTest#gateClearedWhenPreviouslyUnmastered` | PASS | 顺序敏感 |
| Mastery Gate | 已掌握→false | `MasteryOverrideServiceTest#gateNotClearedWhenAlreadyMastered` | PASS | |
| Mastery Gate | 重复声明→false | `MasteryOverrideServiceTest#secondOverrideDoesNotClearGateAgain` | PASS | 幂等语义 |
| Mastery Gate | 声明发 `mastery.override` | `MasteryOverrideServiceTest#overrideEmitsEvent` | PASS | payload 含 id 与 note |
| Mastery Gate | 未知 KP 文案 | `MasteryOverrideServiceTest#unknownPointUsesKnowledgePointWording` | PASS | `unknown knowledge point:` |
| Mastery Gate | 声明推进前沿 | `MasteryOverrideServiceTest#overrideAdvancesFrontier` | PASS | `nextObjective` → complete |
| Mastery Gate | 声明不影响其他 KP | `MasteryOverrideServiceTest#overrideAffectsOnlyTargetPoint` | PASS | |
| Mastery Gate | 声明不新增 attempt/证据 | `MasteryOverrideServiceTest#overrideAddsNoAttempts` | PASS | |
| Mastery Gate | 类型映射不变 | `MasteryOverrideServiceTest#overrideKeepsKnowledgeTypes` | PASS | `INV-06` |
| Learner-Visible Projection | TC-E4 投影隐藏答案 | `QuestionCardProjectorTest#hidesAnswerAndExplanation` | PASS | `INV-07` |
| Learner-Visible Projection | 投影恰好 7 键 | `QuestionCardProjectorTest#projectionHasExactlySevenKeys` | PASS | 白名单 |
| Learner-Visible Projection | 不泄漏关键词与时间戳 | `QuestionCardProjectorTest#hidesKeywordsAndTimestamp` | PASS | |
| Learner-Visible Projection | 字段值逐项正确 | `QuestionCardProjectorTest#fieldValuesMatch` | PASS | |
| Learner-Visible Projection | 选项只含 label/body | `QuestionCardProjectorTest#optionsKeepOnlyLabelAndBody` | PASS | |
| Learner-Visible Projection | null 安全 | `QuestionCardProjectorTest#nullReturnsEmptyMap` | PASS | |
| Learner-Visible Projection | 无选项为空列表 | `QuestionCardProjectorTest#noOptionsYieldsEmptyList` | PASS | |
| Path Lifecycle | 创建发 `path.created` | `PathLifecycleTest#createPathEmitsEvent` | PASS | 仅 1 条事件 |
| Path Lifecycle | 重复创建被拒 | `PathLifecycleTest#duplicateCreateRejected` | PASS | 文案逐字 |
| Path Lifecycle | 重复创建零事件 | `PathLifecycleTest#duplicateCreateEmitsNothing` | PASS | |
| Path Lifecycle | null 字段落空串 | `PathLifecycleTest#nullFieldsBecomeEmpty` | PASS | |
| Path Lifecycle | 改名持久化 | `PathLifecycleTest#renamePersists` | PASS | |
| Path Lifecycle | 改名不校验空名 | `PathLifecycleTest#renameAcceptsEmptyName` | PASS | 照抄参考实现 |
| Path Lifecycle | 改名保留 goal | `PathLifecycleTest#renameKeepsGoal` | PASS | `INV-13` |
| Path Lifecycle | 未知路径改名被拒 | `PathLifecycleTest#renameMissingPathRejected` | PASS | |
| Path Lifecycle | 删除已存在→true | `PathLifecycleTest#deleteExistingPath` | PASS | |
| Path Lifecycle | 删除不存在→false | `PathLifecycleTest#deleteMissingPathReturnsFalse` | PASS | |
| Path Lifecycle | 创建后版本为 1 | `PathLifecycleTest#createSetsVersionOne` | PASS | |
| Path Lifecycle | 空路径可创建 | `PathLifecycleTest#createEmptyPath` | PASS | |
| Path Lifecycle | 预置路径可读 | `PathLifecycleTest#prebuiltPathIsReadable` | PASS | |
| Source Attachment | 整体替换 | `SourceAttachmentTest#attachReplacesWholeList` | PASS | |
| Source Attachment | 第二次覆盖第一次 | `SourceAttachmentTest#attachOverwritesPrevious` | PASS | |
| Source Attachment | 只替换 KB 并保留其他 | `SourceAttachmentTest#knowledgeBaseSourcesPreserveOtherKinds` | PASS | 核心语义 |
| Source Attachment | 清空 KB 不动文件 | `SourceAttachmentTest#clearingKnowledgeBasesKeepsFiles` | PASS | |
| Source Attachment | 事件 count 只计新增 | `SourceAttachmentTest#knowledgeBaseEventCountsOnlyIncoming` | PASS | |
| Source Attachment | 序列化 snake_case | `SourceAttachmentTest#serializedSourceUsesSnakeCase` | PASS | |
| Source Attachment | null 按空列表 | `SourceAttachmentTest#nullSourcesTreatedAsEmpty` | PASS | |
| Source Attachment | 可创建缺失路径 | `SourceAttachmentTest#attachCreatesMissingPath` | PASS | `createIfMissing` |
| Objective State | 延后写入记录 | `ObjectiveStateTest#deferWritesRecord` | PASS | |
| Objective State | 延后跳过该目标 | `ObjectiveStateTest#deferSkipsPoint` | PASS | `TC-C15` |
| Objective State | 延后事件不含 note | `ObjectiveStateTest#deferEventOmitsNote` | PASS | 不对称行为 |
| Objective State | 延后不产证据 | `ObjectiveStateTest#deferAddsNoEvidence` | PASS | |
| Objective State | 延后文案 `unknown objective:` | `ObjectiveStateTest#deferUnknownUsesObjectiveWording` | PASS | |
| Objective State | 已讲解写入记录 | `ObjectiveStateTest#noteExplainedWritesRecord` | PASS | `mastery_credit: false` |
| Objective State | 已讲解不给学分 | `ObjectiveStateTest#noteExplainedGrantsNoMastery` | PASS | |
| Objective State | 已讲解事件含 summary | `ObjectiveStateTest#noteExplainedEventIncludesSummary` | PASS | 与 defer 不对称 |
| Objective State | 已讲解文案 `unknown objective:` | `ObjectiveStateTest#noteExplainedUnknownUsesObjectiveWording` | PASS | |
| Objective State | null summary 落空串 | `ObjectiveStateTest#nullSummaryBecomesEmpty` | PASS | |
| Objective State | defer 与 explained 独立 | `ObjectiveStateTest#deferAndExplainedAreIndependent` | PASS | |
| Objective State | 未知路径延后被拒 | `ObjectiveStateTest#deferMissingPathRejected` | PASS | |
| Path Deletion | 删除路径行 | `PathDeletionTest#deleteRemovesPathRow` | PASS | |
| Path Deletion | 删除不存在→false | `PathDeletionTest#deleteMissingReturnsFalse` | PASS | |
| Path Deletion | 级联会话与消息 | `PathDeletionTest#cascadeRemovesSessionsAndMessages` | PASS | |
| Path Deletion | 级联题库与交互 | `PathDeletionTest#cascadeRemovesBankAndInteractions` | PASS | |
| Path Deletion | **events 保留** | `PathDeletionTest#eventsSurviveDeletion` | PASS | append-only，`INV-38` |
| Path Deletion | 只影响目标路径 | `PathDeletionTest#deleteIsScopedToOnePath` | PASS | |
| Path Deletion | 其他路径事件不受影响 | `PathDeletionTest#otherPathEventsUntouched` | PASS | |
| Path Deletion | 无子数据也可删 | `PathDeletionTest#deletePathWithNoChildren` | PASS | |

本 change 新增用例：**63 个**（`MasteryOverrideServiceTest` 15 + `PathLifecycleTest` 13 +
`ObjectiveStateTest` 12 + `SourceAttachmentTest` 8 + `PathDeletionTest` 8 +
`QuestionCardProjectorTest` 7），全部 PASS。
全量 **193 个，0 失败**（130 → 193）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 129, Failures: 0 |
| 全量 | `verify.ps1 -Change 0006-service-core` | BUILD SUCCESS，Tests run: 193, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0005 的 130 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_policy.py tests/test_interactions.py tests/test_path_events.py tests/test_preferences.py -q` | **29 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_policy.py::test_tc_c6_learner_override_mastered_with_learner_source` | `MasteryOverrideServiceTest#overrideMarksLearnerSource` | 同断言，且走真实事务 |
| `test_policy.py::test_tc_c7_override_does_not_write_score` | `MasteryOverrideServiceTest#overrideDoesNotWriteScore` | 同断言（分数保持 0.1） |
| `test_policy.py::test_tc_c8_override_produces_no_evidence` | `MasteryOverrideServiceTest#overrideAddsNoEvidence` | 同断言 |
| `test_policy.py::test_tc_c15_deferred_skipped_not_mastered` | `ObjectiveStateTest#deferSkipsPoint` | 同语义（Java 侧走服务调用而非手工注入） |
| `test_policy.py::test_tc_c16_type_change_refused` | 已由 `0004` 的 `MasteryPolicyTest#typeIsReadOnly` 覆盖 | — |
| `test_policy.py::test_tc_c17_goal_preserved` | 已由 `0004` 的 `GoalPreservationTest#goalSurvivesMutation` 覆盖 | — |
| `test_interactions.py::test_tc_e4_public_projection_hides_answer` | `QuestionCardProjectorTest#hidesAnswerAndExplanation` | 同断言 + 白名单校验 |
| `test_path_events.py::test_create_path_emits_event` | `PathLifecycleTest#createPathEmitsEvent` | 同断言 |
| `test_preferences.py::test_knowledge_base_sources_preserve_other_kinds` | `SourceAttachmentTest#knowledgeBaseSourcesPreserveOtherKinds` + `#clearingKnowledgeBasesKeepsFiles` | 拆为两例 |

### Java 侧超出参考测试覆盖的部分（参考实现存在测试空白）

| Java 用例 | 补齐的空白 |
|---|---|
| `PathDeletionTest`（8 项） | 参考实现无 `delete_path` 级联范围的直接断言 |
| `ObjectiveStateTest`（12 项） | 参考实现服务层无 `defer_objective` / `note_explained` 的直接测试 |
| `MasteryOverrideServiceTest#overrideEmitsEvent` + `#overrideAddsNoEvidence` | 参考实现缺「`mastery.override` 事件存在且 `learning_evidence` 无新增」的组合断言（`TC-I7` 语义） |
| `PathLifecycleTest#duplicateCreateRejected` | 参考实现无重复创建拒绝的用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `containsExactlyInAnyOrderKeys` 不存在于 AssertJ `MapAssert` | 一般 | 改用 `containsOnlyKeys`（断言强度不变） |
| D2 | `verify.ps1 -Scope unit` 的过滤规则漏掉 `QuestionCardProjectorTest`（类名不含 `Projection`） | 一般 | 补 `*ProjectorTest` |
| D3 | `MasteryInteraction` 无 `setKnowledgePointId`，测试初稿误用 | 一般 | 删除该行（该字段非必需） |
| D4 | `overrideMastery` 未实现 `gateCleared` 时的 `review.scheduled` 事件与学习者首间隔 | **重要** | 属 `0010`（SM-2 调度）；已登记 `docs/verification-debt.md` |
| D5 | `createPath` 的存在性检查在事务外（TOCTOU 窗口） | 一般 | 照抄参考实现，不额外加锁 |
| D6 | 错误文案双套（`unknown knowledge point:` / `unknown objective:`）与事件 payload 不对称（`objective.deferred` 缺 `note`） | 一般 | **刻意照抄**，用 4 个用例逐字锁定。属参考实现行为瑕疵 Q1/Q2，见 `docs/migration-plan.md` §11 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `review.scheduled` 事件与学习者首间隔（0.5 倍） | `0010` | 需 SM-2 调度器 |
| `build` / `revise` / 缺口 / 覆盖报告 | `0007` | 本 change 不含大纲操作 |
| `registerQuestion` / `grade` / `assess` | `0008` | 本 change 不含判分 |
| `listPathOverviews` / `getState` 完整快照 | `0014` | REST 需要时才定形 |
| `ensureScratchPath` / `bindSessionPath` | `0012` | 导航 |
| `recordLearnerProfile` | `0012` | 画像 |
| `reset`（清学习态保留地图） | `0012` | 与修复流程同批 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 193, Failures: 0` 来自
`artifacts/verify-0006-service-core-20261104-134002.log`；参考实现对照见
`pytest tests/test_policy.py tests/test_interactions.py tests/test_path_events.py tests/test_preferences.py`
的 `29 passed`。
