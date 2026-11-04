# Proposal: 工具层（19 个工具 + schema 表）

## Why

`0016` 定义了「工具长什么样」与「回合怎么跑」，但一个具体工具都没有——
`AgentLoop` 拿到的是空表，模型无从调用任何能力。

参考实现 `capabilities/mastery/tools.py`（1095 行）与 `nav_tools.py`（191 行）
把全部能力暴露成 19 个工具，并有三条硬性约束：

1. **门控在调用期**（`ADR-005`）：每个模式专属工具执行前先查 `ModeGate`，
   被拒时返回 `ok=false` + 纠正文案，而不是抛异常。
2. **服务层异常 → `ToolResult(ok=false)`**：工具是「异常 → 结果」的转换层，
   模型永远看到可读文本，回合永不崩。
3. **条件挂载**（`ADR-023`）：`rag` 只在有知识库时挂载，`read_source` 只在有来源时挂载。

没有这一层，`0018` 的 REST 与 `0019` 的 WS 都无法驱动真实业务。

## What Changes

- 补齐服务层 4 个缺口（工具层的前置）：
  - `LearningService.setSessionMode(pathId, sessionId, mode)`
  - `LearningService.proposeObjective(...)` / `noteGap(...)`
  - `LearningStore.getOpenInteraction(pathId)`
- 新增 `adapter/tool` 下的工具实现：
  - `ToolSchemas`：19 个工具的 JSON Schema（手写，与参考实现逐字一致）
  - 共享：`MasteryStatusTool` / `MasteryModeTool` / `MasteryPathsTool` /
    `MasterySwitchTool` / `MasteryLeaveTool`
  - 大纲：`MasteryBuildTool` / `MasteryReviseTool`
  - 学习：`MasteryQuizTool` / `MasteryGradeTool` / `MasterySkipQuestionTool` /
    `MasteryNoteExplainedTool` / `MasteryDeferObjectiveTool`
  - 修复与画像：`MasteryRepairQuestionTool` / `MasteryProfileTool` / `MasteryAssessTool`
  - 捕获：`MasteryProposeObjectiveTool` / `MasteryNoteGapTool`
  - 来源：`MasteryRagTool` / `MasteryReadSourceTool`
  - 导航：`MasteryTopicsTool` / `MasterySessionsTool` / `MasteryOpenSessionTool` /
    `MasteryNewSessionTool`
  - `MasteryTools`：工具注册表 + `mountedTools(ctx)` 条件挂载

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-tools` 与 `specs/mode-gating` 的工具暴露面
（行为以参考实现 `tools.py` / `nav_tools.py` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0016` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/service/LearningService.java`（+3 方法）
  - `src/main/java/com/masterypath/storage/LearningStore.java`（+1 方法）
  - `src/main/java/com/masterypath/adapter/tool/*`（新增约 20 个类）
  - `src/test/java/com/masterypath/adapter/tool/*Test.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*ToolTest`、`*ToolsTest`）
- 影响测试：既有 554 个用例**不修改**。
- 明确不做（Out of scope）：
  - `TurnRunner` / WS 回合 → 属 `0019`。
  - 知识库服务本体（`KnowledgeService.search`）→ 属 `0020`；本 change 只按接口调用。
  - REST 端点 → 属 `0018`。
