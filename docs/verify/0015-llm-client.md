# 验证报告：0015-llm-client

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0015-llm-client` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 19:59:23（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0015-llm-client` |
| 日志 | `artifacts/verify-0015-llm-client-20261104-195923.log`（全量）<br>`artifacts/verify-0015-llm-client-20261104-195850.log`（unit） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

> **编号说明**：参考实现 `tests/test_llm_client.py` 首行只写
> `"""Tool definitions reach the model, and the client parses tool calls safely."""`，
> 没有 `TC-*` 编号。本报告的 `TC-LLM1`–`TC-LLM4` 是 Java 侧分组标签。

### 2.1 SSE 解析（TC-LLM1）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 文本增量 + 工具调用分片拼装 | `SseParserTest#textThenAssembledCalls` | PASS | 对应 `test_stream_parser_yields_text_then_assembles_tool_call_fragments` |
| 坏参数 + 无 id 兜底 | `#invalidArgumentsAndFallbackId` | PASS | 对应 `test_stream_parser_marks_invalid_arguments_and_supplies_an_id` |
| 无名字也算 `parseError` | `#missingNameIsParseError` | PASS | 参考实现无此用例 |
| 空字符串参数不算错误 | `#emptyArgumentsIsNotAnError` | PASS | 参考实现无此用例 |
| `[DONE]` 之后忽略 | `#doneStopsParsing` | PASS | 参考实现无此用例 |
| 无工具调用不产出空片 | `#noCallsMeansNoCallChunk` | PASS | 参考实现无此用例 |
| 非 `data:` / 空行 / 坏 JSON 跳过 | `#ignoresNonDataAndMalformedLines` | PASS | 参考实现无此用例 |
| 多调用按 `index` 升序 | `#multipleCallsSortedByIndex` | PASS | 参考实现无此用例 |
| 后到空 name 不覆盖 | `#laterEmptyNameDoesNotOverwrite` | PASS | 参考实现无此用例 |
| 逐行 `feed` 与一次性 `parse` 一致 | `#feedMatchesParse` | PASS | 参考实现无此用例 |

### 2.2 非流式客户端（TC-LLM2）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 发送 `tools` + `tool_choice=auto`，解析工具调用 | `OpenAiCompatibleClientTest#chatSendsToolsAndParsesCalls` | PASS | 对应 `test_chat_sends_tools_and_parses_tool_calls` |
| 非法参数标记 `parseError` | `#chatMarksInvalidArguments` | PASS | 对应 `test_chat_marks_invalid_arguments` |
| 跳过无名字的调用 | `#chatSkipsNamelessCalls` | PASS | 参考实现无此用例 |
| 无工具时不发 `tools` 字段 | `#chatOmitsToolsWhenEmpty` | PASS | 参考实现无此用例 |
| 401 → `auth_error`，消息不含上游原文 | `#authErrorIsStructuredAndSafe` | PASS | 对应 `test_auth_error_is_structured_and_safe` |
| 404/429/400/5xx/其他 的 code 与 retryable | `#httpErrorBranches` | PASS | 参考实现只测 401 |
| 未配置 base_url / model 时拒绝 | `#requiresConfiguration` | PASS | 参考实现无此用例 |
| anthropic 协议明确拒绝 | `#anthropicProtocolRefused` | PASS | 参考实现无此用例 |

### 2.3 请求头与端点（TC-LLM3）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| opencode 网关注入会话头 | `#opencodeSessionHeader` | PASS | 对应 `test_opencode_gateway_gets_a_routing_session_header` |
| 显式 `extraHeaders` 优先 | `#explicitHeadersWin` | PASS | 对应 `test_explicit_extra_headers_win` |
| 有 key 才带 Bearer | `#bearerHeaderOnlyWithKey` | PASS | 参考实现无此用例 |
| `chatEndpoint` 幂等 | `#chatEndpointIdempotent` | PASS | 参考实现无此用例 |

### 2.4 流式客户端与 Fake（TC-LLM4）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 发送 `stream=true` + `tools`，解析文本与调用 | `OpenAiCompatibleClientTest#streamSendsStreamFlagAndParses` | PASS | 对应 `test_stream_chat_events_sends_stream_and_tools` |
| 流式非 200 也结构化 | `#streamErrorIsStructured` | PASS | 参考实现无此用例 |
| 脚本顺序 + 耗尽提示 | `FakeLlmClientTest#returnsScriptInOrder` | PASS | 参考实现无此用例 |
| 记录 `calls` / `toolsSeen` | `#recordsCallsAndTools` | PASS | 参考实现无此用例 |
| `chunkSize=0` 整段一次 | `#singleChunkWhenChunkSizeZero` | PASS | 参考实现无此用例 |
| `chunkSize>0` 按字符切分 | `#splitsByChunkSize` | PASS | 参考实现无此用例 |
| 工具调用在文本之后 | `#toolCallsComeAfterText` | PASS | 参考实现无此用例 |
| 默认流式回退到 `chat` | `#defaultStreamFallsBackToChat` | PASS | 参考实现无此用例 |
| 默认回退也产出调用片 | `#defaultStreamEmitsCalls` | PASS | 参考实现无此用例 |

本 change 新增用例：**31 个**（`SseParserTest` 10 + `OpenAiCompatibleClientTest` 14 + `FakeLlmClientTest` 7），全部 PASS。
全量 **535 个，0 失败**（504 → 535）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 448, Failures: 0 |
| 全量 | `verify.ps1 -Change 0015-llm-client` | BUILD SUCCESS，Tests run: 535, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0014 的 504 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_llm_client.py -q`（在 `../MasteryPath`） | **10 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_every_tool_has_a_schema` | — | **归 `0017`**：schema 表在工具层 |
| `test_required_parameters_match_the_executors` | — | **归 `0017`**：同上 |
| `test_chat_sends_tools_and_parses_tool_calls` | `#chatSendsToolsAndParsesCalls` | 同断言 |
| `test_chat_marks_invalid_arguments` | `#chatMarksInvalidArguments` | 同断言 |
| `test_auth_error_is_structured_and_safe` | `#authErrorIsStructuredAndSafe` | 同断言（含「不含上游原文」） |
| `test_opencode_gateway_gets_a_routing_session_header` | `#opencodeSessionHeader` | 同断言 |
| `test_explicit_extra_headers_win` | `#explicitHeadersWin` | 同断言 |
| `test_stream_parser_yields_text_then_assembles_tool_call_fragments` | `#textThenAssembledCalls` | 同断言 |
| `test_stream_parser_marks_invalid_arguments_and_supplies_an_id` | `#invalidArgumentsAndFallbackId` | 同断言 |
| `test_stream_chat_events_sends_stream_and_tools` | `#streamSendsStreamFlagAndParses` | 同断言 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#httpErrorBranches` | 参考实现只测 401，未测 404/429/400/5xx/其他 |
| `#doneStopsParsing` / `#noCallsMeansNoCallChunk` / `#ignoresNonDataAndMalformedLines` | 参考实现无 SSE 边界用例 |
| `#multipleCallsSortedByIndex` / `#laterEmptyNameDoesNotOverwrite` | 参考实现无多调用与覆盖顺序用例 |
| `#emptyArgumentsIsNotAnError` / `#missingNameIsParseError` | 参考实现无 `parseError` 边界用例 |
| `#requiresConfiguration` / `#anthropicProtocolRefused` | 参考实现无配置校验用例 |
| `#bearerHeaderOnlyWithKey` / `#chatEndpointIdempotent` | 参考实现无请求头与端点拼接用例 |
| `#streamErrorIsStructured` | 参考实现无流式错误映射用例 |
| `FakeLlmClientTest`（7 项） | 参考实现无 Fake 客户端独立用例 |

### 测试方式说明

参考实现用 `httpx.MockTransport` 拦截传输层。Java 侧改用 JDK 自带
`com.sun.net.httpserver.HttpServer` 起**真实本地端点**，因此请求头、请求体、
状态码映射、SSE 行读取都被真实执行——比 mock 传输层覆盖更完整。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `DeepSeekClient` 未移植 | 无 | 参考实现自己标注「仅为向后兼容，新代码用 `OpenAICompatibleClient`」；Java 侧无历史包袱 |
| D2 | 2 个 schema 用例未覆盖 | 一般 | 归 `0017`（schema 表在工具层）。已登记 `verification-debt.md` |
| D3 | 真实 provider 联调未执行 | 阻塞 | 需 API Key，属手工验证项（`verification-debt.md` 已登记） |
| D4 | `SseParser` 用 `TreeMap` 而非 `dict` | 无 | Python `dict` 保插入序，但参考实现最后 `sorted(calls)`；`TreeMap` 直接给出升序，语义等价 |
| D5 | `LlmClient.streamChatEvents` 返回 `List` 而非 `Iterator` | 低 | 参考实现是生成器；Java 侧回合循环（`0016`）会按需消费。内存代价可接受（单回合最多 6 轮） |
| D6 | `OpenAiCompatibleClient` 用 `BodyHandlers.ofLines()` | 无 | 与参考实现 `response.iter_lines()` 等价；`Stream` 在 try-with-resources 中关闭 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `test_every_tool_has_a_schema` / `test_required_parameters_match_the_executors` | `0017` | schema 表在工具层 |
| 真实 provider 流式与 function calling | 手工验证 | 需 API Key |
| `AgentLoop` 的回合语义 | `0016` | 本 change 只做客户端 |
| `ModelResolver` / `TaskModel` | `0022` | 需设置存储 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 535, Failures: 0` 来自
`artifacts/verify-0015-llm-client-20261104-195923.log`；参考实现对照见
`pytest tests/test_llm_client.py` 的 `10 passed`。
