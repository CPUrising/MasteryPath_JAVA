# 验证报告：0007-outline-build-revise

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0007-outline-build-revise` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 14:11:00（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0007-outline-build-revise` |
| 日志 | `artifacts/verify-0007-outline-build-revise-20261104-141059.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Build And Revise | TC-J1 空路径 build | `OutlineBuildTest#buildOnEmptyPath` | PASS | |
| Build And Revise | TC-J1 id 分配与类型写入 | `OutlineBuildTest#buildAssignsIdsAndTypes` | PASS | `m1`/`k1`/`k2` |
| Build And Revise | TC-J1 currentModuleId | `OutlineBuildTest#buildSetsCurrentModule` | PASS | |
| Build And Revise | 模块 order 0..n | `OutlineBuildTest#buildSetsModuleOrder` | PASS | |
| Build And Revise | TC-J2 无 confirm 拒绝 | `OutlineBuildTest#rebuildWithoutConfirmRefused` | PASS | 列出 `k1` |
| Build And Revise | TC-J2 拒绝时零变更 | `OutlineBuildTest#refusedRebuildLeavesStateUntouched` | PASS | `INV-41` |
| Build And Revise | TC-J2 confirm 替换并清态 | `OutlineBuildTest#rebuildWithConfirmReplacesAndResets` | PASS | |
| Build And Revise | **顺序敏感**：非法 payload 先抛错 | `OutlineBuildTest#invalidPayloadThrowsBeforeConfirmCheck` | PASS | 关键 |
| Build And Revise | TC-J3 同名拒绝 | `OutlineBuildTest#duplicateNameRejected` | PASS | |
| Build And Revise | 跨模块同名拒绝 | `OutlineBuildTest#duplicateNameAcrossModulesRejected` | PASS | |
| Build And Revise | 名字 <2 字符拒绝 | `OutlineBuildTest#shortNameRejected` | PASS | |
| Build And Revise | 未知类型拒绝 | `OutlineBuildTest#unknownTypeRejected` | PASS | |
| Build And Revise | modules 缺失/空拒绝 | `OutlineBuildTest#missingModulesRejected` | PASS | |
| Build And Revise | 模块非对象拒绝 | `OutlineBuildTest#nonObjectModuleRejected` | PASS | |
| Build And Revise | 前置按名字解析（前向引用） | `OutlineBuildTest#prerequisitesResolveByNameWithForwardReference` | PASS | 两遍扫描 |
| Build And Revise | 未知前置拒绝 | `OutlineBuildTest#unknownPrerequisiteRejected` | PASS | |
| Build And Revise | materials 只补未接地的 KP | `OutlineBuildTest#materialsGroundOnlyUngroundedPoints` | PASS | |
| Build And Revise | 未命中的来源标签被丢弃 | `OutlineBuildTest#unknownSourceLabelDropped` | PASS | |
| Build And Revise | TC-J6 缺口上报 | `OutlineBuildTest#buildReportsGaps` | PASS | `goal_gap` |
| Build And Revise | 缺口续号（无 KP 场景） | `OutlineBuildTest#gapIdsContinueFromExisting` | PASS | `g1` → `g2` |
| Build And Revise | confirm 重建缺口重置为 g1 | `OutlineBuildTest#rebuildResetsGapIds` | PASS | `resetLearningState` 清空缺口 |
| Build And Revise | build 写覆盖报告 | `OutlineBuildTest#buildWritesCoverage` | PASS | |
| Build And Revise | TC-J4 add_kp 保留进度 | `OutlineReviseTest#addKpPreservesProgress` | PASS | |
| Build And Revise | TC-J4 add_kp 写类型 | `OutlineReviseTest#addKpWritesType` | PASS | |
| Build And Revise | TC-J4 update_kp 保留进度 | `OutlineReviseTest#updateKpKeepsProgress` | PASS | |
| Build And Revise | TC-J5 类型只读（文案逐字） | `OutlineReviseTest#typeChangeRefused` | PASS | `INV-06` |
| Build And Revise | TC-J5 同类型放行 | `OutlineReviseTest#sameTypeAllowed` | PASS | |
| Build And Revise | add_module | `OutlineReviseTest#addModuleOp` | PASS | |
| Build And Revise | update_module | `OutlineReviseTest#updateModuleOp` | PASS | |
| Build And Revise | remove_module 连带清态 | `OutlineReviseTest#removeModuleOp` | PASS | |
| Build And Revise | remove_kp 清态 | `OutlineReviseTest#removeKpOp` | PASS | |
| Build And Revise | move_kp 跨模块 | `OutlineReviseTest#moveKpOp` | PASS | |
| Build And Revise | reorder | `OutlineReviseTest#reorderOp` | PASS | |
| Build And Revise | reorder 未列全拒绝 | `OutlineReviseTest#reorderIncompleteRejected` | PASS | |
| Build And Revise | 未知 op 拒绝 | `OutlineReviseTest#unknownOpRejected` | PASS | |
| Build And Revise | ops 为 null 拒绝 | `OutlineReviseTest#nullOpsRejected` | PASS | |
| Build And Revise | 未知 module_id 拒绝 | `OutlineReviseTest#unknownModuleRejected` | PASS | |
| Build And Revise | 未知 kp_id 拒绝 | `OutlineReviseTest#unknownKpRejected` | PASS | |
| Build And Revise | dropKpState 10 个字段全清 | `OutlineReviseTest#dropKpStateClearsEverything` | PASS | 7 map + 3 list |
| Build And Revise | revise 缺口从 g1 重算 | `OutlineReviseTest#reviseGapIdsRestartFromOne` | PASS | 与 build 续号不同 |
| Build And Revise | revise 重算覆盖 | `OutlineReviseTest#reviseRecomputesCoverage` | PASS | |
| Build And Revise | 空 ops 合法 | `OutlineReviseTest#emptyOpsAllowed` | PASS | |
| Coverage Report | TC-N1 未声明材料 | `CoverageReportTest#unreportedCoverage` | PASS | |
| Coverage Report | TC-N2 未用来源进 missing | `CoverageReportTest#unusedSourceIsMissing` | PASS | |
| Coverage Report | TC-N3 missing 不展开 | `CoverageReportTest#unreportedDoesNotExpandMissing` | PASS | 关键 |
| Coverage Report | TC-N4 环形前置 | `CoverageReportTest#cycleDetected` | PASS | |
| Coverage Report | 无环判定 | `CoverageReportTest#acyclicWhenNoCycle` | PASS | |
| Coverage Report | 悬空前置不算环 | `CoverageReportTest#danglingPrerequisiteIsNotCycle` | PASS | |
| Coverage Report | 空路径接地比 1.0 | `CoverageReportTest#emptyPathGroundingIsOne` | PASS | 不是 0 |
| Coverage Report | 接地比计算 | `CoverageReportTest#groundingRatioCountsGrounded` | PASS | 0.5 |
| Coverage Report | 全覆盖 | `CoverageReportTest#fullyCovered` | PASS | |
| Coverage Report | missing 按 id 升序 | `CoverageReportTest#missingIsSorted` | PASS | |
| Coverage Report | 自环算环 | `CoverageReportTest#selfLoopIsCycle` | PASS | |
| Coverage Report | 三节点长环 | `CoverageReportTest#threeNodeCycleDetected` | PASS | |
| Gap Classification | TC-F2 重生成不闭合 | `GapClassificationTest#regenerateDoesNotCloseGap` | PASS | |
| Gap Classification | TC-F3 使用来源后闭合 | `GapClassificationTest#sourceOmissionClosesWhenUsed` | PASS | |
| Gap Classification | TC-F4 接受缺口 | `GapClassificationTest#acceptGapThenResolved` | PASS | |
| Gap Classification | TC-F4 接受后仍未接地 | `GapClassificationTest#acceptedGapLeavesUngrounded` | PASS | |
| Gap Classification | regenerate 标记 | `GapClassificationTest#regenerateMarksResolution` | PASS | |
| Gap Classification | 未知 gap_id 拒绝 | `GapClassificationTest#unknownGapRejected` | PASS | |
| Gap Classification | 未知 action 拒绝 | `GapClassificationTest#unknownActionRejected` | PASS | |
| —（服务层） | 首次 build 发 `outline.built` | `GapClassificationTest#firstBuildEmitsOutlineBuilt` | PASS | |
| —（服务层） | 重建发 `path.rebuilt` | `GapClassificationTest#rebuildEmitsPathRebuilt` | PASS | |
| —（服务层） | 被拒绝重建不产事件 | `GapClassificationTest#refusedRebuildEmitsNothing` | PASS | `INV-37` |
| —（服务层） | revise 发 `outline.revised` | `GapClassificationTest#reviseEmitsEvent` | PASS | |
| —（服务层） | getCoverage 返回报告 | `GapClassificationTest#getCoverageReturnsReport` | PASS | |
| —（服务层） | getCoverage 未知路径 null | `GapClassificationTest#getCoverageMissingPath` | PASS | |
| —（goal） | build 保留 goal | `GapClassificationTest#buildKeepsGoal` | PASS | `TC-C17` |
| —（goal） | 重建保留 goal | `GapClassificationTest#rebuildKeepsGoal` | PASS | `TC-C17` |

本 change 新增用例：**69 个**（`OutlineBuildTest` 22 + `OutlineReviseTest` 20 +
`GapClassificationTest` 15 + `CoverageReportTest` 12），全部 PASS。
全量 **262 个，0 失败**（193 → 262）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 193, Failures: 0 |
| 全量 | `verify.ps1 -Change 0007-outline-build-revise` | BUILD SUCCESS，Tests run: 262, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0006 的 193 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_outline.py tests/test_eval.py -q`（在 `../MasteryPath`） | **18 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_tc_j1_build_empty_path` | `OutlineBuildTest#buildOnEmptyPath` | 同断言 |
| `test_tc_j2_rebuild_without_confirm_refused` | `OutlineBuildTest#rebuildWithoutConfirmRefused` + `#refusedRebuildLeavesStateUntouched` | 拆为两例 |
| `test_tc_j2b_rebuild_with_confirm_replaces` | `OutlineBuildTest#rebuildWithConfirmReplacesAndResets` | 同断言 |
| `test_tc_j3_duplicate_name_rejected` | `OutlineBuildTest#duplicateNameRejected` | 同断言 |
| `test_tc_j4_revise_preserves_untouched_progress` | `OutlineReviseTest#addKpPreservesProgress` | 同断言 |
| `test_tc_j5_revise_type_change_refused` | `OutlineReviseTest#typeChangeRefused` | 同断言 + 文案逐字 |
| `test_tc_j6_build_reports_gaps` | `OutlineBuildTest#buildReportsGaps` | 同断言 |
| `test_tc_f2_regenerate_does_not_close_coverage_gap` | `GapClassificationTest#regenerateDoesNotCloseGap` | 同断言 |
| `test_tc_f3_source_omission_closes_when_used` | `GapClassificationTest#sourceOmissionClosesWhenUsed` | 同断言 |
| `test_tc_f4_accept_gap_and_resolve` | `GapClassificationTest#acceptGapThenResolved` + `#acceptedGapLeavesUngrounded` | 拆为两例 |
| `test_tc_n1_build_returns_coverage` | `CoverageReportTest#unreportedCoverage` | 同断言 |
| `test_tc_n2_unused_source_is_missing` | `CoverageReportTest#unusedSourceIsMissing` | 同断言 |
| `test_tc_n3_unreported_does_not_expand_missing` | `CoverageReportTest#unreportedDoesNotExpandMissing` | 同断言 |
| `test_tc_n4_cycle_detected` | `CoverageReportTest#cycleDetected` | 同断言 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `OutlineReviseTest#moveKpOp` / `#reorderOp` / `#removeModuleOp` | 参考实现无这三个 op 的直接测试 |
| `OutlineReviseTest#dropKpStateClearsEverything` | 参考实现无 10 个字段的逐项断言 |
| `OutlineBuildTest#gapIdsContinueFromExisting` / `#rebuildResetsGapIds` | 参考实现无缺口续号语义的测试 |
| `OutlineBuildTest#prerequisitesResolveByNameWithForwardReference` | 参考实现无前向引用测试 |
| `GapClassificationTest#refusedRebuildEmitsNothing` | 参考实现无「被拒重建不产事件」断言 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 首次用 `Set-Content` 改测试文件写入 UTF-8 BOM，javac 报 `非法字符: '\ufeff'` | 一般 | 改用 `[System.IO.File]::WriteAllText` + `UTF8Encoding(false)` 去 BOM。后续一律用 Write 工具 |
| D2 | 初版 `gapIdsContinueFromExisting` 预期 `g2`，实际 `g1` | 一般 | **测试写错，非实现错**：`confirm` 重建会先 `resetLearningState` 清空 `sourceGaps`，续号起点变 0。改为两个用例分别验证「无 KP 时续号」与「重建时重置」 |
| D3 | `OutlineBuilder` 使用 `KnowledgeType.fromValue` 解析类型，与参考实现 `KnowledgeType(...)` 语义一致 | 一般 | 无行为差异；`fromValue` 额外支持历史中文值（`0002` 的 `INV-16`），属超集但不影响合法输入 |
| D4 | `eval_outline`（覆盖比/接地比/无环阈值判定与 CLI）未实现 | 一般 | 属 `0023-outline-eval-cli`；已登记验证债 |
| D5 | `propose_objective` / `note_gap`（候选与缺口写入）未实现 | 一般 | 服务层已在 `0017` 落地（`LearningService.proposeObjective` / `noteGap`）；本 change 只实现 `applyProposal` / `rejectProposal` 纯函数 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `eval_outline` 阈值判定与确定性（TC-N5/N6） | `0018` | 需 CLI 与基线对比 |
| `propose_objective` / `note_gap` | `0009` | 捕获-延后流程 |
| 扫描文件夹生成 `TopicSource` | `0016` | 知识库 |
| `reset` 独立入口 | `0012` | 与修复流程同批 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 262, Failures: 0` 来自
`artifacts/verify-0007-outline-build-revise-20261104-141059.log`；参考实现对照见
`pytest tests/test_outline.py tests/test_eval.py` 的 `18 passed`。
