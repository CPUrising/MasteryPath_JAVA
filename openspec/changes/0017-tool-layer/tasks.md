# Tasks

## 1. 服务层缺口

- [x] 1.1 `LearningStore.getOpenInteraction(pathId)`：非终态交互，按 `created_at` 降序取首个
- [x] 1.2 `LearningService.setSessionMode(pathId, sessionId, mode)`：`ensureSession` + 改 `mode` + `upsertSession`
- [x] 1.3 `LearningService.proposeObjective(...)` / `noteGap(...)`：追加到聚合 + 发事件

## 2. schema 与共享工具

- [x] 2.1 `ToolSchemas`：19 份 JSON Schema（与参考实现 `TOOL_PARAMETERS` 逐字一致）
- [x] 2.2 `MasteryStatusTool`（`status=empty` 重写 + `intake_needed`）/ `MasteryModeTool`（`mode_changed` + 持久化失败不失败）
- [x] 2.3 `MasteryPathsTool` / `MasterySwitchTool` / `MasteryLeaveTool`

## 3. 大纲与学习工具

- [x] 3.1 `MasteryBuildTool`（`built` 时附 `map` / `next`）/ `MasteryReviseTool`
- [x] 3.2 `MasteryQuizTool`（单槽位重放 + review 门控 + `endTurn`）/ `MasteryGradeTool`（`ungraded` → `ok=false`）
- [x] 3.3 `MasterySkipQuestionTool` / `MasteryNoteExplainedTool` / `MasteryDeferObjectiveTool`

## 4. 修复、画像、评估、捕获

- [x] 4.1 `MasteryRepairQuestionTool`（按 action 分支文案）/ `MasteryProfileTool`（白名单过滤 + 全空拒绝）
- [x] 4.2 `MasteryAssessTool`（review 门控文案与服务层不同）
- [x] 4.3 `MasteryProposeObjectiveTool`（名称 ≥ 2）/ `MasteryNoteGapTool`（`kind` 校验）

## 5. 来源与导航

- [x] 5.1 `MasteryRagTool`（三级校验 + 检索异常降级）/ `MasteryReadSourceTool`（20000 截断）
- [x] 5.2 `MasteryTopicsTool` / `MasterySessionsTool` / `MasteryOpenSessionTool` / `MasteryNewSessionTool`（文案逐字）
- [x] 5.3 `MasteryTools` 注册表 + `mountedTools(ctx)` 条件挂载（不改原表）

## 6. 测试与验证

- [x] 6.1 `ToolSchemasTest`：19 份 schema 齐全、`required` 与执行体一致（5 用例）
- [x] 6.2 `MasteryToolsTest`：门控（含未记录模式放行）、服务层异常 → `ok=false`、条件挂载（15 用例）
- [x] 6.3 各工具专项：`StatusAndQuizToolsTest`（17）、`SourceToolsTest`（10）
- [x] 6.4 `.\scripts\verify.ps1 -Scope unit -Change 0017-tool-layer`（514 通过）
- [x] 6.5 全量 `.\scripts\verify.ps1 -Change 0017-tool-layer`（601 通过）
- [x] 6.6 与参考实现对照（6 个测试文件共 37 passed）
- [x] 6.7 回填 `docs/verify/0017-tool-layer.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0017-tool-layer.md`、`README.md`
