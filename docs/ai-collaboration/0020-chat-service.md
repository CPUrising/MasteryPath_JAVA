# AI 协作留痕：0020-chat-service

## 1. prompt 范围

- 授权修改的文件：
  - `src/main/java/com/masterypath/storage/SettingsStore.java`（新增）
  - `src/main/java/com/masterypath/storage/ChatStore.java`（新增）
  - `src/main/java/com/masterypath/adapter/llm/ModelResolution.java`（新增）
  - `src/main/java/com/masterypath/service/ChatService.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/ChatController.java`（新增）
  - `src/main/java/com/masterypath/adapter/turn/TurnRunner.java`（chat 分支）
  - `src/main/java/com/masterypath/adapter/web/SettingsController.java`（改为委托 `SettingsStore`）
  - `src/main/java/com/masterypath/adapter/web/WebConfig.java`（`ChatStore` / `SettingsStore` / `ChatService` bean + `ModelResolution` 接线）
  - `src/test/java/com/masterypath/{storage,adapter/llm,service,adapter/web}/*Test.java`（新增 5 个）
  - `src/test/java/com/masterypath/adapter/web/ApiTestSupport.java`（`SettingsController` 构造）
  - `src/test/java/com/masterypath/adapter/turn/TurnRunnerTest.java`（chat 分派文案）
  - `scripts/verify.ps1`（`-Scope api` 补 `*ChatServiceTest`）
  - `openspec/changes/0020-chat-service/**`、`docs/**`
- 明确不做的事：知识库检索与 `knowledge_context`（`0021`）、提供方探测与 Task Model（`0022`）、
  评测 CLI（`0023`）、验收（`0024`）。
- 出处：用户指令原文「继续0020」；`docs/migration-plan.md` §5 第 20 行、§5.4。

## 2. AI 产出

- 存储：`SettingsStore`（`settings.json` + 三组归一）、`ChatStore`（独立 `chat.db` + `_migrate`）
- 解析：`ModelResolution`（`normalizeSelection` / `resolveLlmConfig` / `buildClient`）
- 服务：`ChatService`（会话 CRUD + `streamTurn` 状态机 + 导航工具回合）
- 传输：`ChatController`（5 个会话端点 + SSE 回合）
- 接线：`TurnRunner` 的 `chat` 分支、`WebConfig` 用 `ModelResolution` 构造 `ClientBuilder`
- 新增测试：`ChatStoreTest`（9）、`SettingsStoreTest`（7）、`ModelResolutionTest`（8）、
  `ChatServiceTest`（11）、`ChatApiTest`（7）

## 3. 人工修正点

- AI 自我修正 2 轮：
  1. **`ChatService.navTools` 的返回类型写错**（`Map<String, Object>` vs `Map<String, Tool>`）
     → 编译失败，改为 `Map<String, Tool>`。**实现缺陷，非测试基线**。
  2. **`ChatApiTest` 读 SSE 正文得到空串**：`StreamingResponseBody` 是异步的，
     直接 `getContentAsString()` 读不到 → 改为 `asyncDispatch`。**测试写法问题，未放宽断言**。
- 另修一处 `0019` 遗留断言：`TurnRunnerTest#chatCapabilityIsNotImplementedYet` 原本断言
  文案含 `0020-chat-service`；本 change 已实现 chat，断言改为「未接入 `ChatService` 时
  明确失败」，**异常类型与关键文案仍被锁定**。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 42 / 0；全量 732 / 0（690 → 732）。
- 证据链接：[`docs/verify/0020-chat-service.md`](../verify/0020-chat-service.md)
- 日志：`artifacts/verify-0020-chat-service-20261105-101810.log`（全量）、
  `artifacts/verify-0020-chat-service-20261105-101739.log`（API 专项 103 项）
- 参考实现对照：`pytest tests/test_chat_nav.py tests/test_preferences.py -q` → **5 passed**；
  `pytest tests/test_knowledge_chat.py -q -k "chat"` → **16 passed**

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `SettingsStore` 落盘是否提前到本 change | **是** | `resolve_llm_config` 必须读到用户配置，否则 chat 永远报「未配置」；提供方探测与 Task Model 仍属 `0022` |
| `chat.db` 与 `learning.db` 是否共库 | **不共** | 表名相同、列不同；参考实现也是独立文件 |
| `ChatStore.getSession` 返回 `null` 还是抛错 | **抛 `UnknownSession`** | 对齐参考实现的 `KeyError`；服务层靠它区分「会话没了」 |
| `message_count` / `last_message` 放在哪 | **只在 `listSessions`** | 照抄参考实现；放 `getSession` 会让 `session_detail` 的键集合多出两个 |
| `updateSession` 的 `kb_ids` 非列表怎么办 | **保留原值** | 参考实现 `if not isinstance(kb_ids, list): kb_ids = session["kb_ids"]` |
| `updateSession` 的 `preferences` 是合并还是替换 | **合并** | 否则部分更新会丢 `llm_selection` |
| 空消息 / 未知会话是否先落库 | **都不落** | 参考实现在 `add_message` 之前短路 |
| 导航工具回合的判定 | **恒走** | 参考实现是 `nav_tools and learning_service and tool_capable`；Java 侧前两者恒成立、`LlmClient` 必有流方法，等价 |
| `tool_call` / `tool_result` 的事件形状 | **展平到顶层** | 参考实现 `{"type": ..., **data}`，不是 `{type, data}` 嵌套 |
| `turn_end` 由谁产出 | **服务层自造** | 参考实现带 `session_id` / `title`，`AgentLoop` 的 `turn_end` 只有 `reason` |
| `turn_end` 的 `title` 用快照还是重读 | **重读** | 标题可能在本次回合刚被填充 |
| SSE 是否用 `StreamingResponseBody` | **是** | 逐帧 flush；`X-Accel-Buffering: no` 防反向代理缓冲 |
| `SettingsController` 是否保留原归一逻辑 | **移入 `SettingsStore`** | 避免「UI 写内存、chat 读文件」的双源；`SettingsApiTest` 9 个断言不改 |
| 生产 `ClientBuilder` 的默认行为 | **`resolveLlmConfig` → `buildClient`** | 与参考实现 `_build_llm_client` 一致；无配置时抛 `bad_request` |
