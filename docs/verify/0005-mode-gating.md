# 验证报告：0005-mode-gating

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0005-mode-gating` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 13:24:05（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0005-mode-gating` |
| 日志 | `artifacts/verify-0005-mode-gating-20261104-132405.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Static Fail Closed Classification | TC-D1 outline 禁 quiz | `ModeGateTest#quizRefusedInOutline` | PASS | |
| Static Fail Closed Classification | TC-D2 study 禁 build | `ModeGateTest#buildRefusedInStudy` | PASS | |
| Static Fail Closed Classification | TC-D2 review 也禁 build | `ModeGateTest#buildRefusedInReview` | PASS | 补充用例 |
| Static Fail Closed Classification | outline 允许 build | `ModeGateTest#buildAllowedInOutline` | PASS | |
| Static Fail Closed Classification | TC-D3 共享工具放行 | `ModeGateTest#sharedToolAllowedEverywhere` | PASS | 8 个工具 × 3 模式 |
| Static Fail Closed Classification | 未登记工具视为共享 | `ModeGateTest#unlistedToolIsShared` | PASS | |
| Static Fail Closed Classification | TC-D9 完整性守卫 | `ToolClassificationTest#defaultClassificationIsComplete` | PASS | |
| Static Fail Closed Classification | TC-D9 未归类 fail-closed | `ToolClassificationTest#strayToolIsRejected` | PASS | `mystery_tool` |
| Static Fail Closed Classification | 两集合不相交 | `ToolClassificationTest#sharedAndExclusiveDoNotOverlap` | PASS | |
| Static Fail Closed Classification | 共享工具显式且不入 `TOOL_MODES` | `ToolClassificationTest#sharedMasteryToolsAreExplicit` | PASS | |
| Static Fail Closed Classification | outline 专属集合精确 | `ToolClassificationTest#outlineOnlySetIsExact` | PASS | |
| Static Fail Closed Classification | study/review 集合精确（9 个） | `ToolClassificationTest#studyReviewSetIsExact` | PASS | |
| Static Fail Closed Classification | `allMasteryTools` 排除 rag/read_source | `ToolClassificationTest#allMasteryToolsExcludesRagAndReadSource` | PASS | |
| Static Fail Closed Classification | 分类表不可变 | `ToolClassificationTest#classificationTablesAreImmutable` | PASS | |
| Static Fail Closed Classification | 空清单守卫通过 | `ToolClassificationTest#emptyListPasses` | PASS | |
| Ungated When Mode Unknown | TC-D4 null/空/未知全放行 | `ModeGateTest#unrecordedModeEnforcesNothing` | PASS | 4 种输入 |
| Ungated When Mode Unknown | `enforced` 返回 null | `SessionModeTest#enforcedReturnsNullForUnrecordedModes` | PASS | |
| Ungated When Mode Unknown | `normalize` 回退 study | `SessionModeTest#normalizeFallsBackToStudy` | PASS | |
| Ungated When Mode Unknown | 两函数对合法值一致 | `SessionModeTest#bothResolveKnownValues` | PASS | |
| Ungated When Mode Unknown | 容忍大小写与空白 | `SessionModeTest#parsingToleratesCaseAndWhitespace` | PASS | |
| Ungated When Mode Unknown | `DEFAULT_MODE` 为 study | `SessionModeTest#defaultModeIsStudy` | PASS | |
| Ungated When Mode Unknown | 声明顺序即 MODES 顺序 | `SessionModeTest#declarationOrderIsOutlineStudyReview` | PASS | 影响 `owningModes` 顺序 |
| Rejection Must Point The Way | 文案含 `mastery_mode` 与 `'study'` | `ModeGateTest#wrongModeMessageNamesTarget` | PASS | 对应 Python 同名用例 |
| Rejection Must Point The Way | 文案逐字一致 | `ModeGateTest#wrongModeMessageIsVerbatim` | PASS | 含破折号 `—` |
| Rejection Must Point The Way | build 文案只列 outline | `ModeGateTest#wrongModeMessageForBuild` | PASS | |
| Rejection Must Point The Way | 未知模式按 study 展示 | `ModeGateTest#wrongModeMessageNormalizesUnknownMode` | PASS | |
| Rejection Must Point The Way | 无归属兜底文案 | `ModeGateTest#wrongModeMessageFallsBackWhenNoOwner` | PASS | |
| Study Admission Requires Outline | TC-D5 无大纲拒绝 | `ModeGateTest#studyWithoutOutlineRefused` | PASS | |
| Study Admission Requires Outline | TC-D6 有大纲放行 | `ModeGateTest#studyWithOutlineAllowed` | PASS | |
| Study Admission Requires Outline | outline/review 无准入 | `ModeGateTest#otherModesHaveNoAdmissionGate` | PASS | |
| Study Admission Requires Outline | 准入用 normalize 判 | `ModeGateTest#admissionTreatsUnknownModeAsStudy` | PASS | |
| Review Only Retests Mastered | TC-D7 归属顺序 | `ModeGateTest#quizOwnedByStudyThenReview` | PASS | |
| Review Only Retests Mastered | TC-D7 build 归属 | `ModeGateTest#buildOwnedByOutlineOnly` | PASS | |
| Review Only Retests Mastered | TC-D7 共享无归属 | `ModeGateTest#sharedToolHasNoOwningMode` | PASS | |
| Review Only Retests Mastered | TC-D8 review 允许 assess | `ModeGateTest#reviewAllowsAssess` | PASS | 模式许可级 |
| Review Only Retests Mastered | TC-D8 review 允许全部 9 个 | `ModeGateTest#reviewAllowsAllStudyReviewTools` | PASS | |
| Behavior Budget | TC-M6 预算常量 6/3/2 | `BehaviorBudgetTest` | PASS | 4 项 |
| —（提示词） | 每模式提示词非空且互异 | `SessionModeTest#everyModeHasDistinctInstruction` | PASS | |
| —（提示词） | 提示词逐字复刻 | `SessionModeTest#instructionsMatchReferenceWording` | PASS | |

本 change 新增用例：**42 个**（`ModeGateTest` 21 + `ToolClassificationTest` 9 + `SessionModeTest` 8 + `BehaviorBudgetTest` 4），全部 PASS。
全量 **130 个，0 失败**（88 → 130）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 81, Failures: 0 |
| 全量 | `verify.ps1 -Change 0005-mode-gating` | BUILD SUCCESS，Tests run: 130, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0004 的 88 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_mode.py -q`（在 `../MasteryPath`） | **12 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_tc_d1_quiz_refused_in_outline` | `ModeGateTest#quizRefusedInOutline` | 同断言 |
| `test_tc_d2_build_refused_in_study` | `ModeGateTest#buildRefusedInStudy` | 同断言 |
| `test_tc_d3_shared_tool_allowed` | `ModeGateTest#sharedToolAllowedEverywhere` | Java 侧扩为全表全模式 |
| `test_tc_d4_missing_mode_enforces_nothing` | `ModeGateTest#unrecordedModeEnforcesNothing` | 同三种输入 + 纯空白 |
| `test_tc_d5_study_without_outline_refused` | `ModeGateTest#studyWithoutOutlineRefused` | 同断言 |
| `test_tc_d6_study_with_outline_allowed` | `ModeGateTest#studyWithOutlineAllowed` | 同断言 |
| `test_tc_d7_review_scope_helpers` | `ModeGateTest#quizOwnedByStudyThenReview` | 同断言 + 顺序 |
| `test_tc_d8_review_allows_mastered_gate_tools` | `ModeGateTest#reviewAllowsAssess` | 同断言 |
| `test_tc_d9_classification_complete_and_fail_closed` | `ToolClassificationTest#defaultClassificationIsComplete` + `#strayToolIsRejected` | 拆为两例 |
| `test_every_mounted_tool_is_classified` | `ToolClassificationTest#defaultClassificationIsComplete` | 挂载集待 `0013` 落地后传入 |
| `test_status_and_mode_are_shared_in_every_mode` | `ModeGateTest#sharedToolAllowedEverywhere` | 覆盖 |
| `test_wrong_mode_message_names_target` | `ModeGateTest#wrongModeMessageNamesTarget` | 同断言 + 逐字比对 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `package-info.java` 首次写入时多了一行重复的 `package` 声明，编译报 `class, interface, enum, or record expected` | 一般 | 删除重复行；属编辑错误，非设计问题 |
| D2 | `assertClassificationComplete` 抛 `IllegalStateException` 而非 Python 的 `AssertionError` | 一般 | Java 的 `assert` 默认关闭；参考实现也是显式 `raise` 而非 `assert` 语句，语义上属运行期契约校验。已在 `design.md` §Decisions 4 留痕 |
| D3 | `docs/02-mode-tool-matrix.md:49-50` 把 4 个导航工具列为共享，但参考实现代码的 `SHARED_TOOLS` 不含它们 | 一般 | **以代码为准**；4 个导航工具留待 `0014` 落地时归类。已在 `design.md` §Open Questions 留痕 |
| D4 | 文档 R4 要求 `mastery_grade` 在 review 下也拒绝未掌握 KP，但参考实现代码无此检查（只有 quiz 与 assess 有） | 一般 | 本 change 不涉及；`0008` 落地时按代码行为实现，并在该 change 的验证报告中登记 |

## 5. 未覆盖项（已登记验证债）

以下场景属 `specs/mode-gating` 但**不在本 change 范围**，已登记到 `docs/verification-debt.md`：

| 场景 | 归属 change | 原因 |
|---|---|---|
| TC-L1 / TC-L2 / TC-L4 会话模式持久化与 `session_meta` | `0006`（服务层）/ `0015`（REST） | 需要 `setSessionMode` 与传输层 |
| TC-L3 被拒切换不持久 | `0006` | 同上 |
| TC-M2 study 下 `mastery_revise` 被拒且地图不变 | `0007` | 需要 `revise` 工具本体 |
| TC-M6 单回合工具预算触发 `turn_end` | `0013` | 需要回合循环（本 change 只提供常量） |
| review 下对未掌握 KP 出题的**知识点级**拒绝 | `0008` | 需要工具执行上下文（本 change 只覆盖模式许可级，与 `test_mode.py` 一致） |
| `rag` / `read_source` 条件挂载 | `0016` | 需要知识库上下文 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 130, Failures: 0` 来自 `artifacts/verify-0005-mode-gating-20261104-132405.log`；参考实现对照见 `pytest tests/test_mode.py` 的 `12 passed`。
