# Tasks

## 1. 探测基础

- [x] 1.1 `settings/ProviderProbe` 纯函数：`validBaseUrl` / `splitBaseUrl` / `discoveryRequest` / `embeddingRequest` / `authHeaders` / `statusForHttp`
- [x] 1.2 `settings/ProviderProbe` 模型分类：`classifyModel`（排除优先）+ `classifyItems`（去重保序）+ `modelItems` / `modelId`

## 2. 探测操作

- [x] 2.1 `settings/ProviderProbe.probeProvider`：anthropic 短路 + 状态词表 + `service` 过滤 + `no_models` 警告
- [x] 2.2 `settings/ProviderProbe`：`fetchModels` / `detectDimension` / `dimensionFromPayload`
- [x] 2.3 `settings/ProviderProbe`：`embedTexts` 委托 `HttpEmbedder` + `providerCatalog`
      （`embedTexts` 不重复实现：`0021` 的 `HttpEmbedder` 已提供同一端点推导与鉴权头）

## 3. Task Model

- [x] 3.1 `agents/TaskModel`：`suggestQuestion`（LLM 优先，构造/调用/空文本三类失败回退）
- [x] 3.2 `agents/TaskModel`：5 条确定性回退逐字复刻 + `suggestSessionName`

## 4. 传输与接线

- [x] 4.1 `adapter/web/SettingsProbeController`：`/settings/providers/catalog` + `probe` + `fetch-models` + `/settings/models/detect-dimension`（含掩码回退）
- [x] 4.2 `LearningController` 补 `GET /paths/{id}/suggest-question`；`WebConfig` 接线 `TaskModel` 与控制器

## 5. 测试与验证

- [x] 5.1 `ProviderProbeTest`（13）+ `TaskModelTest`（9）+ `SettingsProbeApiTest`（8）；`verify.ps1` 的 `-Scope api` 补 `*ProbeTest`、`*TaskModelTest`
- [x] 5.2 `.\scripts\verify.ps1 -Scope api -Change 0022-settings-task-model`（169 项）→ 全量（835 项）→ `pytest tests/test_settings_probe.py tests/test_task_model.py -q`（17 passed）对照
- [x] 5.3 回填 `docs/verify/0022-settings-task-model.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0022-settings-task-model.md`、`README.md`
