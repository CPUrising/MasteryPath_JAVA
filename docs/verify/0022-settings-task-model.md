# 验证报告：0022-settings-task-model

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0022-settings-task-model` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-05 11:50:43（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0022-settings-task-model` |
| 日志 | `artifacts/verify-0022-settings-task-model-20261105-115043.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 提供方探测（`ProviderProbeTest`，13 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| URL 校验：接受 http/https，拒绝凭据/非法端口/非 URL | `#validBaseUrlRejectsCredentialsAndJunk` | PASS | 对应 `test_valid_base_url` |
| 模型分类：排除词优先于嵌入提示词 | `#classifyModelUsesKeywords` | PASS | 对应 `test_classify_model` |
| 状态词表 | `#statusVocabulary` | PASS | 401/403→`auth_error`；404/405/501→`unavailable`；429→`rate_limited` |
| 推理后缀剥离 | `#splitBaseUrlStripsInferenceSuffixes` | PASS | 7 个后缀 |
| **探测成功：路径 + Bearer 头 + 分类 + 不回显凭据** | `#probeConnectedAndClassifies` | PASS | 对应 `test_probe_connected_and_classifies`（含 `"sk-secret" not in str(result)`） |
| `service` 过滤 | `#probeFiltersByService` | PASS | 对应 `test_probe_filters_by_service` |
| 过滤后为空 → `warning: no_models` | `#probeWarnsWhenNothingLeft` | PASS | 参考实现无独立用例，由 `test_probe_filters_by_service` 的同分支推断 |
| 401 / 404 / 连接被拒 | `#probeStatusVocabulary` | PASS | 对应 `test_probe_status_vocabulary`（Python 用 `ConnectTimeout`，Java 用关闭端口覆盖 `unreachable`） |
| 非法 URL + anthropic 短路 | `#probeInvalidUrlAndAnthropic` | PASS | 对应 `test_probe_invalid_url_and_anthropic` |
| **ollama 发现路径 `/api/tags`** | `#ollamaDiscoveryPath` | PASS | 对应 `test_ollama_discovery_path` |
| 维度探测 1536 | `#detectDimension` | PASS | 对应 `test_detect_dimension` |
| 维度探测不可用 | `#detectDimensionUnavailable` | PASS | 对应 `test_detect_dimension_unavailable` |
| 厂商目录 + 返回副本 | `#providerCatalog` | PASS | 对应 `test_provider_catalog_endpoint` 的后端部分 |

### 2.2 Task Model（`TaskModelTest`，9 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 无客户端回退 | `#fallbackWithoutClient` | PASS | 对应 `test_fallback_without_client` |
| 回退随动作变化 | `#fallbackReflectsAction` | PASS | 对应 `test_fallback_reflects_action` |
| **5 条回退文案逐字复刻** | `#fallbackTextIsVerbatim` | PASS | 参考实现只断言关键词，此处锁定全文 |
| 缺省知识点名 | `#fallbackUsesDefaultName` | PASS | 「这个知识点」 |
| 有模型时原样返回 | `#usesLlmWhenAvailable` | PASS | 对应 `test_uses_llm_when_available` |
| 模型抛错时回退 | `#fallsBackWhenLlmRaises` | PASS | 对应 `test_falls_back_when_llm_raises` |
| 返回空白文本时回退 | `#fallsBackWhenTextBlank` | PASS | 参考实现同分支，无独立用例 |
| 提示词内容 + 最近 6 条 | `#promptCarriesRecentTail` | PASS | 参考实现无独立用例 |
| 会话名 40 字 / 缺省文案 | `#sessionName` | PASS | 参考实现无独立用例 |

### 2.3 探测端点与建议下一问（`SettingsProbeApiTest`，8 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 厂商目录端点 | `#catalogEndpoint` | PASS | 对应 `test_provider_catalog_endpoint` |
| protocol 默认 + llm 分组读回 | `#protocolDefaultAndBackCompat` | PASS | 对应 `test_provider_protocol_default_and_back_compat` |
| **掩码回退：空 key 用已保存凭据** | `#probeEndpointUsesSavedCredentials` | PASS | 对应 `test_probe_endpoint_uses_saved_credentials` |
| 提交值优先于已保存值 | `#probeEndpointPrefersSubmittedFields` | PASS | 参考实现无独立用例，锁定 `a or b or fallback` |
| **探测不写配置** | `#probeEndpointDoesNotWriteSettings` | PASS | 规范「只读探测」的硬约束 |
| fetch-models / detect-dimension | `#fetchModelsAndDetectDimensionEndpoints` | PASS | 参考实现经真实函数覆盖，Java 用探测缝断言字段 |
| 建议下一问非空 | `#suggestQuestionReturnsDraft` | PASS | 对应 `test_api_suggest_question` |
| 未知路径 404 | `#suggestQuestionUnknownPath` | PASS | 参考实现无独立用例 |

本 change 新增用例：**30 个**（`ProviderProbeTest` 13 + `TaskModelTest` 9 +
`SettingsProbeApiTest` 8），全部 PASS。
全量 **835 个，0 失败**（805 → 835）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| API 专项 | `verify.ps1 -Scope api -Change 0022-settings-task-model` | Tests run: 169, Failures: 0 |
| 全量 | `verify.ps1 -Change 0022-settings-task-model` | BUILD SUCCESS，Tests run: 835, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0021` 的 805 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_settings_probe.py tests/test_task_model.py -q` | **17 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_valid_base_url` | `ProviderProbeTest#validBaseUrlRejectsCredentialsAndJunk` | 同 6 条断言 |
| `test_classify_model` | `#classifyModelUsesKeywords` | 同 6 条断言（含 `whisper-1` / `dall-e-3` → 空串） |
| `test_probe_connected_and_classifies` | `#probeConnectedAndClassifies` | 同断言（`{id: services}`、凭据不出现在结果里） |
| `test_probe_filters_by_service` | `#probeFiltersByService` | 同断言（只剩 `bge-m3`） |
| `test_probe_status_vocabulary` | `#probeStatusVocabulary` | 401 / 404 / 超时三支；Java 以关闭端口覆盖 `unreachable` |
| `test_probe_invalid_url_and_anthropic` | `#probeInvalidUrlAndAnthropic` | 同断言（`invalid_url` / `unavailable`） |
| `test_ollama_discovery_path` | `#ollamaDiscoveryPath` | 同断言（`seen == ["/api/tags"]`、`llama3:latest`） |
| `test_detect_dimension` | `#detectDimension` | 同断言（1536） |
| `test_detect_dimension_unavailable` | `#detectDimensionUnavailable` | 同断言 |
| `test_provider_catalog_endpoint` | `SettingsProbeApiTest#catalogEndpoint` + `ProviderProbeTest#providerCatalog` | 同断言（4 个 id 必含） |
| `test_provider_protocol_default_and_back_compat` | `SettingsProbeApiTest#protocolDefaultAndBackCompat` | 同断言（`protocol == openai`、llm 分组原样读回） |
| `test_probe_endpoint_uses_saved_credentials` | `SettingsProbeApiTest#probeEndpointUsesSavedCredentials` | 同断言（捕获四个字段） |
| `test_fallback_without_client` | `TaskModelTest#fallbackWithoutClient` | 同断言（含知识点名） |
| `test_fallback_reflects_action` | `#fallbackReflectsAction` | 同断言（「卡片」/「复习」） |
| `test_uses_llm_when_available` | `#usesLlmWhenAvailable` | 同断言（原样返回） |
| `test_falls_back_when_llm_raises` | `#fallsBackWhenLlmRaises` | 同断言（非空回退） |
| `test_api_suggest_question` | `SettingsProbeApiTest#suggestQuestionReturnsDraft` | 同断言（200 + 非空 `suggestion`） |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `ProviderProbeTest#statusVocabulary` / `#splitBaseUrlStripsInferenceSuffixes` | 参考实现无状态映射与后缀剥离的单元用例 |
| `ProviderProbeTest#probeWarnsWhenNothingLeft` | 参考实现无 `warning: no_models` 断言 |
| `ProviderProbeTest#providerCatalog` 的副本语义 | 参考实现不测共享常量是否会被改写 |
| `TaskModelTest#fallbackTextIsVerbatim` | 参考实现只断言关键词，未锁定全文 |
| `TaskModelTest#fallbackUsesDefaultName` / `#fallsBackWhenTextBlank` / `#promptCarriesRecentTail` / `#sessionName` | 参考实现无对应用例 |
| `SettingsProbeApiTest#probeEndpointPrefersSubmittedFields` | 参考实现无「提交值优先」用例 |
| `SettingsProbeApiTest#probeEndpointDoesNotWriteSettings` | 参考实现无「探测不写配置」用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `ProviderProbe.providerCatalog` 是 Java 静态常量 + 副本，Python 是模块级元组 | 无 | 都以「不可被调用方改写」为准 |
| D2 | 探测用 JDK `HttpClient` 替代 `httpx`，测试用 JDK `HttpServer` 替代 `MockTransport` | 无 | 请求路径、头、状态码映射都真实执行；不 mock 传输层 |
| D3 | `embedTexts` 未在本 change 实现，委托 `0021` 的 `HttpEmbedder` | 无 | 端点推导与鉴权头已在 `0021` 落地并有测试；避免两处漂移 |
| D4 | `SettingsProbeController` 用注入的 `Probe` / `DimensionProbe` 替代 Python 的 `monkeypatch` | 无 | 同一测试缝，且不污染全局状态 |
| D5 | `ProviderProbe` 无独立「超时」用例 | 低 | 20s 超时在测试里不可等待；`unreachable` 分支已覆盖，超时分支与 `HttpEmbedder` 共用同一异常映射 |
| D6 | `TaskModelTest` 放在 `adapter/llm` 包（`migration-plan` §3.4 写作 `adapter.llm.TaskModel`） | 无 | 与计划一致 |
| D7 | `ApiTestSupport` 新增 `probeSeen` / `fetchSeen` / `dimensionSeen` 三个观测字段 | 无（必要） | 探测端点需要断言「收到的字段」，而注入缝在夹具里 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `POST /paths/{id}/eval` | `0023-outline-eval-cli` | 评测本体与 CLI |
| `embedTexts` 的 provider 端到端 | `0024` | 需真实 Key，属手工验证项 |
| 真实 Google/Gemini 等非目录厂商探测 | — | 参考实现目录只有 6 家；`custom` 协议覆盖其余 |
| 前端「测试连接」联调 | `0024` | 需全部端点就绪 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 835, Failures: 0` 来自
`artifacts/verify-0022-settings-task-model-20261105-115043.log`；API 专项 169 项来自
`artifacts/verify-0022-settings-task-model-20261105-112644.log`；参考实现对照见
`pytest tests/test_settings_probe.py tests/test_task_model.py -q` 的 `17 passed`。
