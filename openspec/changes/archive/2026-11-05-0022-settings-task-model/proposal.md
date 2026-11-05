# Proposal: 设置探测与 Task Model

## Why

`0020` 把设置**落盘**做了，但设置中心还缺三块「读之外」的能力：

1. **提供方探测**：`/settings/providers/{catalog,probe,fetch-models}` 与
   `/settings/models/detect-dimension` 全部缺失——前端「测试连接」「自动检测模型」不可用。
2. **Task Model**：`GET /learning/paths/{id}/suggest-question` 缺失——输入框没有草稿提示。
3. `POST /learning/paths/{id}/eval` 缺失（评测本体属 `0023`，端点可在本 change 或 `0023` 落地）。

参考实现对应 `settings/providers.py`（485 行）+ `agents/task_model.py`（76 行）
+ `api/app.py:341-346, 382-387, 408-467`。

## What Changes

- 新增 `settings/ProviderProbe`：`validBaseUrl` / `classifyModel` / `probeProvider` /
  `fetchModels` / `detectDimension` / `providerCatalog` / `embedTexts`
- 新增 `agents/TaskModel`：`suggestQuestion`（LLM 优先 + 确定性回退）/ `suggestSessionName`
- 新增 `adapter/web/SettingsProbeController`：4 个探测端点 + catalog
- `LearningController` 补 `GET /paths/{id}/suggest-question`
- `WebConfig` 接线 `TaskModel`（用 `ModelResolution` 构造客户端）

## Capabilities

### New Capabilities

无新增能力；实现 `specs/settings-task-model` 的三条 Requirement。

### Modified Capabilities

无。`skip_specs: true`。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/settings/ProviderProbe.java`（新增）
  - `src/main/java/com/masterypath/agents/TaskModel.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/SettingsProbeController.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/{LearningController,WebConfig}.java`
  - `src/test/java/com/masterypath/**/{ProviderProbeTest,TaskModelTest,SettingsProbeApiTest}.java`（新增）
  - `scripts/verify.ps1`（`-Scope api` 补 `*ProbeTest`、`*TaskModelTest`）
- 影响测试：既有 805 个用例**不修改**。
- 明确不做（Out of scope）：
  - 大纲评测 CLI 与 `POST /paths/{id}/eval` 的**实现** → 属 `0023-outline-eval-cli`
  - 验收 → 属 `0024-acceptance`
