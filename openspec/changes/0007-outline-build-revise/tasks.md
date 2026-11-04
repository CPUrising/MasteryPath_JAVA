# Tasks

## 1. 基础类型

- [x] 1.1 `OutlineException extends IllegalArgumentException`：payload 非法（`INV-40`）
- [x] 1.2 `BuildResult` record：`status` / `modules` / `gaps` / `dropped` / `rebuilt` / `message`
- [x] 1.3 `ReviseResult` record：`status` / `applied` / `droppedKpIds` / `gaps` / `message`
- [x] 1.4 `SourceResolver`：`{label: id}` + 唯一基名别名；`resolve(label)` 未命中返回空串

## 2. build

- [x] 2.1 `parseBuildPayload`：两遍扫描（先分配 id 建 name→id，再解析前置），名字去空白且 ≥2 字符、重名拒绝、类型校验
- [x] 2.2 `materials` 接地：模块级 `materials` 只补给**未声明来源**的知识点
- [x] 2.3 非空路径且无 `confirm` → `refused_rebuild` + `dropped` 列表 + 零状态变更
- [x] 2.4 `confirm` 重建：先 `resetLearningState` 再替换模块；写 `knowledgeTypes`
- [x] 2.5 `currentModuleId` 设为第一个模块（仅当有模块）

## 3. 缺口与覆盖

- [x] 3.1 `detectGaps(modules, selected, existingCount)`：目标无来源 → `goal_gap`；来源未用 → `source_omission`（按 id 排序）
- [x] 3.2 `computeCoverage`：`documents` / `covered` / `missing` / `reported` / `groundingRatio` / `acyclic`
- [x] 3.3 `reported=false` 时 `missing` MUST 为空；无知识点时 `groundingRatio=1.0`
- [x] 3.4 `isAcyclic`：DFS 三色标记，仅遍历图中存在的节点

## 4. revise（8 个 op）

- [x] 4.1 `add_module` / `update_module` / `remove_module`
- [x] 4.2 `add_kp` / `update_kp`（类型只读）/ `remove_kp`
- [x] 4.3 `move_kp` / `reorder`（必须列全且不重复）
- [x] 4.4 `dropKpState`：7 个 map + 3 个 list 全清
- [x] 4.5 未知 op → 抛 `OutlineException`；缺口与覆盖从 0 重算

## 5. 缺口消解与候选

- [x] 5.1 `resolveGap(progress, gapId, action)`：`accept` / `regenerate`，未知 action 或 gap 抛错
- [x] 5.2 `applyProposal` / `rejectProposal`：只在 `pending` 时生效

## 6. 服务层接线

- [x] 6.1 `LearningService.build(pathId, payload, confirm)`：发 `outline.built` 或 `path.rebuilt`
- [x] 6.2 `LearningService.revise(pathId, ops)`：发 `outline.revised`
- [x] 6.3 `LearningService.resolveGap(pathId, gapId, action)`：发 `gap.resolved`
- [x] 6.4 `LearningService.getCoverage(pathId)`

## 7. 测试

- [x] 7.1 `OutlineBuildTest`：TC-J1/J2/J2b/J3/J6
- [x] 7.2 `OutlineReviseTest`：TC-J4/J5 + 8 个 op + `dropKpState` 全字段
- [x] 7.3 `CoverageReportTest`：TC-N1/N2/N3/N4 + `reported` 语义
- [x] 7.4 `GapClassificationTest`：TC-F2/F3/F4

## 8. 验证

- [x] 8.1 运行 `.\scripts\verify.ps1 -Scope unit --change 0007-outline-build-revise`
- [x] 8.2 运行全量 `.\scripts\verify.ps1 --change 0007-outline-build-revise`
- [x] 8.3 与参考实现对照（`pytest tests/test_outline.py tests/test_eval.py -q`）
- [x] 8.4 回填 `docs/verify/0007-outline-build-revise.md`、`docs/regression-log.md`、`docs/traceability.md`、`README.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0007-outline-build-revise.md`
