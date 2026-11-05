# AI 协作留痕：0022-settings-task-model

## 1. prompt 范围

- 授权修改的文件：
  - `src/main/java/com/masterypath/settings/ProviderProbe.java`（新增）
  - `src/main/java/com/masterypath/adapter/llm/TaskModel.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/SettingsProbeController.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/LearningController.java`（+`GET /paths/{id}/suggest-question`，构造多一个参数）
  - `src/main/java/com/masterypath/adapter/web/WebConfig.java`（`TaskModel` bean）
  - `src/test/java/com/masterypath/settings/ProviderProbeTest.java`（新增）
  - `src/test/java/com/masterypath/adapter/llm/TaskModelTest.java`（新增）
  - `src/test/java/com/masterypath/adapter/web/SettingsProbeApiTest.java`（新增）
  - `src/test/java/com/masterypath/adapter/web/ApiTestSupport.java`（+探测夹具）
  - `scripts/verify.ps1`（`-Scope unit` / `-Scope api` 补 `*ProviderProbeTest`、`*TaskModelTest`、`*ProbeTest`）
  - `openspec/changes/0022-settings-task-model/**`、`docs/**`
- 明确不做的事：`POST /paths/{id}/eval` 的实现（`0023`）、验收（`0024`）、真实 provider 联调（手工项）。
- 出处：用户指令原文「可以」；`docs/migration-plan.md` §5 第 22 行。

## 2. AI 产出

- 探测：`ProviderProbe`（`validBaseUrl` / `splitBaseUrl` / `discoveryRequest` / `embeddingRequest` /
  `authHeaders` / `statusForHttp` / `classifyModel` / `classifyItems` / `modelItems` / `modelId` /
  `probeProvider` / `fetchModels` / `detectDimension` / `dimensionFromPayload` / `providerCatalog`）
- 任务模型：`TaskModel`（`suggestQuestion` + 5 条确定性回退 + `prompt` / `suggestSessionName`）
- 传输：`SettingsProbeController`（catalog + probe + fetch-models + detect-dimension，
  含 `_probe_fields` 的掩码回退）+ `LearningController.suggestQuestion`
- 接线：`WebConfig.taskModel`（工厂 = `ModelResolution.resolveLlmConfig` → `buildClient`）
- 新增测试：`ProviderProbeTest`（13）、`TaskModelTest`（9）、`SettingsProbeApiTest`（8）

## 3. 人工修正点

- AI 自我修正 2 轮（共 3 处缺陷）：
  1. **`ProviderProbe` 的 anthropic 短路漏了 `warning: manual_entry`**（参考实现明确返回该键）
     → `probeInvalidUrlAndAnthropic` 断言失败暴露，补键。**实现缺陷**。
  2. **`SettingsProbeController` 有两个构造器时 Spring 找不到默认构造** →
     `@Override`/`@Autowired` 缺失导致 `CorsApiTest` / `WsTurnTest` 的
     `ApplicationContext` 加载失败（`No default constructor found`）。
     给单参构造加 `@Autowired`。**实现缺陷**（与 `LearningController` 同类问题，后者先行修复）。
  3. **`ApiTestSupport` 的 fetch-models 缝未注入**，导致该端点走真实 HTTP 而返回 `unreachable`
     → 补 `fetchSeen` / `fetchResult` 注入。**测试夹具缺陷**。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 30 / 0；全量 835 / 0（805 → 835）。
- 证据链接：[`docs/verify/0022-settings-task-model.md`](../verify/0022-settings-task-model.md)
- 日志：`artifacts/verify-0022-settings-task-model-20261105-115043.log`（全量）、
  `artifacts/verify-0022-settings-task-model-20261105-112644.log`（API 专项 169 项）
- 参考实现对照：`pytest tests/test_settings_probe.py tests/test_task_model.py -q` → **17 passed**

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `ProviderProbe` 用静态工具类还是实例 | **静态 + 可注入 `HttpClient`** | 参考实现每个函数都接受可选 `client`；`HttpClient` 是唯一外部依赖，注入即可测试 |
| 探测端点如何测试 | **注入 `Probe` / `DimensionProbe` 函数缝 + JDK `HttpServer`** | Python 用 `monkeypatch`；Java 用构造注入，避免全局状态 |
| 模型分类的关键词个数 | **排除 10 个、嵌入 7 个**（设计初稿写成 9/6，已按代码修正） | `providers.py:38-59` 逐条点数 |
| `classifyModel` 的判断顺序 | **排除优先** | 否则 `whisper` 会先命中 `llm` 兜底；`whisper-1` / `dall-e-3` 断言锁定 |
| anthropic 探测是否发请求 | **不发，直接 `unavailable` + `manual_entry`** | Anthropic 无模型列表端点；参考实现原样 |
| `detect_dimension` 是否默认执行 | **只在显式 POST 时执行** | 这是唯一「花一点钱」的探测；不做后台轮询 |
| `_probe_fields` 的 `api_key` 回退条件 | **`api_key` 为假值且 `provider_id` 非空** | 参考实现 `if not api_key and provider_id`；`protocol` / `base_url` 用 `or` 链 |
| `TaskModel` 失败边界 | **构造、调用、空文本三类全吞** | 规范硬要求「MUST NOT 使接口失败」；`fallbackWithoutClient` / `fallsBackWhenLlmRaises` 锁定 |
| 5 条回退文案 | **逐字复刻并在测试里断言全文** | 参考实现只断言关键词；全文断言更严，不构成放宽 |
| `embedTexts` 是否在本 change 重写 | **不重写，委托 `0021` 的 `HttpEmbedder`** | 端点推导与鉴权头已在 `0021` 落地并有测试；两处实现必然漂移 |
| `suggest-question` 的 404 文案 | **`"unknown path"`** | 与 `mastery` / `sources` 等端点一致 |
| `TaskModel` 放哪个包 | **`adapter.llm`** | `migration-plan` §3.4 映射表指定 |
