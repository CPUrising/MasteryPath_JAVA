# 验证报告：0020-chat-service

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0020-chat-service` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-05 10:18:10（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0020-chat-service` |
| 日志 | `artifacts/verify-0020-chat-service-20261105-101810.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 聊天存储（`ChatStoreTest`，9 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| DDL 列名与顺序与参考实现一致 | `#schemaMatchesReference` | PASS | `sessions` 7 列 / `messages` 6 列 |
| 独立 `chat.db`，不写 `learning.db` | `#usesSeparateDatabaseFile` | PASS | |
| 会话 id 形如 `c` + 12 位 hex | `#sessionIdHasChatPrefix` | PASS | 前端靠前缀区分 |
| 未知会话抛 `UnknownSession` | `#unknownSessionThrows` | PASS | `get` / `update` / `delete` 三处 |
| `getSession` 不含派生字段 | `#listSessionsAddsDerivedFields` | PASS | 只有 `listSessions` 补 `message_count` / `last_message` |
| `preferences` 合并 + `model_id` 回写 | `#preferencesMergeAndMirrorModelId` | PASS | 对应 `test_chat_session_preferences_and_legacy_model_id` |
| `kb_ids` 非列表时保留原值 | `#nonListKbIdsKeepsExisting` | PASS | |
| 删除会话连带删除消息 | `#deleteRemovesMessages` | PASS | |
| `addMessage` 刷新 `updated_at` | `#addMessageTouchesSession` | PASS | |

### 2.2 设置存储（`SettingsStoreTest`，7 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 默认值与参考实现一致 | `#defaultsMatchReference` | PASS | |
| 写入落盘，重开可读 | `#writePersistsToDisk` | PASS | 本 change 把 `0018` 的内存实现换成文件 |
| 未知分组抛 `UnknownSection` | `#unknownSectionThrows` | PASS | |
| 旧式单条模型折叠成列表 | `#legacySingleModelFoldsIntoList` | PASS | |
| 未知协议回退 `openai`、非字典 `detection` 丢弃 | `#providerNormalisation` | PASS | |
| 坏 JSON 等价于空文件 | `#corruptFileFallsBackToDefaults` | PASS | |
| 文件与默认值浅合并 | `#fileMergesIntoDefaults` | PASS | |

### 2.3 模型解析（`ModelResolutionTest`，8 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| `normalizeSelection` 需 `model_id` | `#normalizeSelectionRequiresModelId` | PASS | |
| 按 `active_id` 解析 | `#resolvesActiveModel` | PASS | |
| 按显式选择解析 | `#resolvesExplicitSelection` | PASS | |
| `active_id` 缺失退回首条 | `#fallsBackToFirstModel` | PASS | |
| 选中模型不存在 → `bad_request` | `#unknownSelectionIsBadRequest` | PASS | 文案 `no longer configured` |
| 无模型 → `bad_request` | `#noModelConfigured` | PASS | 文案含 `No language model` |
| 提供方缺 base URL | `#providerWithoutBaseUrl` | PASS | |
| `buildClient` 可用 | `#buildClientFromConfig` | PASS | |

### 2.4 聊天服务（`ChatServiceTest`，11 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| **TC-CN1**：导航工具调用 + `metadata` 透出 | `#navToolCallAndHandoffSurface` | PASS | 对应 `test_chat_nav_tool_call_and_handoff_surface` |
| 回合落库 user + assistant + 标题 | `#turnPersistsMessagesAndTitle` | PASS | 对应 `test_chat_stream_persists_messages` |
| `turn_end` 带 `session_id` / 最新 `title` | `#turnEndCarriesSessionAndTitle` | PASS | |
| 空消息 → error 且不落库 | `#blankMessageReportsErrorWithoutPersisting` | PASS | 文案 `Type a message first.` |
| 未知会话 → error | `#unknownSessionReportsError` | PASS | 文案 `This conversation no longer exists.` |
| 无模型 → `bad_request` | `#missingModelReportsStructuredError` | PASS | 对应 `test_chat_without_model_reports_error` |
| 错误时保留已产出正文 | `#keepsPartialTextOnError` | PASS | |
| 历史跳过空 assistant 行 | `#historySkipsEmptyAssistantRows` | PASS | |
| 附件进入 system 块并记录 `name` | `#attachmentsBecomeSystemBlock` | PASS | |
| 会话 CRUD | `#sessionCrud` | PASS | |
| `resolveModel` 无配置抛错 | `#resolveModelWithoutSettings` | PASS | |

### 2.5 聊天 REST + SSE（`ChatApiTest`，7 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 创建 201；列表含派生字段 | `#createAndList` | PASS | |
| 详情 `{session, messages}`；未知 404 | `#detailAndUnknown` | PASS | |
| PATCH 更新；未知 404 | `#patchAndUnknown` | PASS | |
| DELETE 204 → 再删 404 | `#deleteThen404` | PASS | |
| SSE 含 `text_delta` / `turn_end` 并落库 | `#streamPersistsMessages` | PASS | 用 `asyncDispatch` 读正文 |
| 无模型返回结构化 error 帧 | `#streamWithoutModelReportsError` | PASS | |
| 防缓冲响应头 | `#streamSetsNoBufferingHeaders` | PASS | `X-Accel-Buffering: no` + `text/event-stream` |

本 change 新增用例：**42 个**（`ChatStoreTest` 9 + `SettingsStoreTest` 7 +
`ModelResolutionTest` 8 + `ChatServiceTest` 11 + `ChatApiTest` 7），全部 PASS。
全量 **732 个，0 失败**（690 → 732）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| API 专项 | `verify.ps1 -Scope api -Change 0020-chat-service` | Tests run: 103, Failures: 0 |
| 全量 | `verify.ps1 -Change 0020-chat-service` | BUILD SUCCESS，Tests run: 732, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0019` 的 690 项全部仍通过） |
| 参考实现对照（chat） | `pytest tests/test_chat_nav.py tests/test_preferences.py -q` | **5 passed** |
| 参考实现对照（knowledge_chat 的 chat 部分） | `pytest tests/test_knowledge_chat.py -q -k "chat"` | **16 passed**（含知识库用例，其检索属 `0021`） |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_chat_nav_tool_call_and_handoff_surface` | `ChatServiceTest#navToolCallAndHandoffSurface` | 同断言（`tool_call` / `tool_result` / `metadata.mastery_topics.topics[0].path_id` / `turn_end`） |
| `test_chat_stream_persists_messages` | `#turnPersistsMessagesAndTitle` + `ChatApiTest#streamPersistsMessages` | 同断言（`roles == [user, assistant]`、`content`、`title`） |
| `test_chat_without_model_reports_error` | `#missingModelReportsStructuredError` + `ChatApiTest#streamWithoutModelReportsError` | 同断言（`type=error`、文案含 `No language model`） |
| `test_chat_session_preferences_and_legacy_model_id` | `ChatStoreTest#preferencesMergeAndMirrorModelId` | 同断言（`knowledge_bases` 合并且 `llm_selection` 仍在、`model_id == "m1"`） |
| `test_ws_reports_structured_error_when_no_model` | `ModelResolutionTest#noModelConfigured` | 同断言（`bad_request`、`retryable=false`） |
| `test_chat_injects_knowledge_context` | — | **属 `0021-knowledge-base`**（需真实检索） |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `ChatStoreTest#schemaMatchesReference` | 参考实现无 DDL 列名断言 |
| `ChatStoreTest#listSessionsAddsDerivedFields` | 参考实现无「`getSession` 不含派生字段」用例 |
| `ChatStoreTest#nonListKbIdsKeepsExisting` | 参考实现无 `kb_ids` 非列表的边界用例 |
| `ChatServiceTest#blankMessageReportsErrorWithoutPersisting` | 参考实现无空消息不落库用例 |
| `ChatServiceTest#unknownSessionReportsError` | 参考实现无未知会话文案用例 |
| `ChatServiceTest#historySkipsEmptyAssistantRows` | 参考实现无「历史跳过空 assistant」用例 |
| `ChatServiceTest#attachmentsBecomeSystemBlock` | 参考实现无附件块用例 |
| `ChatServiceTest#keepsPartialTextOnError` | 参考实现无「错误保留正文」的 chat 侧用例 |
| `ChatApiTest#streamSetsNoBufferingHeaders` | 参考实现无 SSE 响应头用例 |
| `SettingsStoreTest`（7 例） | `0018` 的内存实现从未验证落盘 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `SettingsStore` 落盘从 `0022` 提前到本 change | 低 | `resolve_llm_config` 必须读到用户配置，否则 chat 永远报「未配置」；提供方探测与 Task Model **仍属 `0022`** |
| D2 | `knowledgeContext` 恒返回空串 | 低 | 知识库检索属 `0021`；`ChatServiceTest` 不覆盖参考块注入 |
| D3 | `ChatService` 的导航分支「恒走」 | 无 | 参考实现的判定是 `nav_tools and learning_service and tool_capable`，Java 侧前两者恒成立且 `LlmClient` 接口必有流方法，故等价；无工具调用时 `AgentLoop` 直接 `turn_end{done}` |
| D4 | `TurnRunnerTest#chatCapabilityWithoutChatServiceFails` 的文案改了 | 无（必要） | `0019` 的占位实现已由本 change 替换；断言改为「未接入 `ChatService` 时明确失败」，**未放宽**（仍断言异常类型与关键文案） |
| D5 | `ChatApiTest` 用 `asyncDispatch` 读 SSE 正文 | 无（必要） | `StreamingResponseBody` 是异步的，直接 `getContentAsString()` 得到空串 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| 知识库检索、`knowledge_context` 真实注入 | `0021` | 知识库服务本体（索引 / 向量检索） |
| 提供方探测（`/settings/providers/probe`）、Task Model | `0022` | 设置与任务模型 |
| `suggest-question`、`eval` 端点 | `0022` / `0023` | 依赖 Task Model / 评测 CLI |
| 前端联调 | `0024` | 需全部端点就绪 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 732, Failures: 0` 来自
`artifacts/verify-0020-chat-service-20261105-101810.log`；API 专项 103 项来自
`artifacts/verify-0020-chat-service-20261105-101739.log`；参考实现对照见
`pytest tests/test_chat_nav.py tests/test_preferences.py -q` 的 `5 passed` 与
`pytest tests/test_knowledge_chat.py -q -k "chat"` 的 `16 passed`。
