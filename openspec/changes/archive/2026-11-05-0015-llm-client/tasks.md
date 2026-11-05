# Tasks

## 1. 数据载体与错误

- [x] 1.1 `ToolCall`（`name` / `arguments` / `id` / `parseError`）+ `LlmResponse`（`text` / `toolCalls`）+ `StreamChunk`（`text` / `toolCalls`）
- [x] 1.2 `LlmError`（`code` / `retryable`）+ `httpError(status)` 的 6 个分支，消息不含上游原文

## 2. SSE 解析

- [x] 2.1 `SseParser.parse(lines)`：只认 `data:` 行、`[DONE]` 提前结束、文本边解析边产出
- [x] 2.2 工具调用分片按 `index` 归并（`id`/`name` 只取首次非空、`arguments` 累加）、末尾一次性产出、无调用不产出空 chunk、无 `id` 兜底 `call-{index}`

## 3. 客户端

- [x] 3.1 `LlmClient` 接口 + `streamChatEvents` 默认回退到 `chat`
- [x] 3.2 `OpenAiCompatibleClient`：`chat` + `streamChatEvents`、`chatEndpoint` 拼接、`x-opencode-session` 注入（显式头优先）、超时与网络错误映射
- [x] 3.3 `FakeLlmClient`：脚本化响应、`chunkSize` 切分、`calls` / `toolsSeen` 记录

## 4. 测试与验证

- [x] 4.1 `SseParserTest`：文本增量、分片拼装、`[DONE]`、坏参数、无 id 兜底、无调用不产出（10 用例）
- [x] 4.2 `OpenAiCompatibleClientTest`：请求体（`stream` / `tools` / `tool_choice`）、工具调用解析、坏参数、6 个 HTTP 错误分支、opencode 头、显式头优先（14 用例）
- [x] 4.3 `FakeLlmClientTest`：脚本顺序、`chunkSize`、`calls` / `toolsSeen`、默认流式回退（7 用例）
- [x] 4.4 `.\scripts\verify.ps1 -Scope unit -Change 0015-llm-client`（448 通过）
- [x] 4.5 全量 `.\scripts\verify.ps1 -Change 0015-llm-client`（535 通过）
- [x] 4.6 与参考实现对照（`pytest tests/test_llm_client.py -q` → 10 passed；2 个 schema 用例归 `0017`）
- [x] 4.7 回填 `docs/verify/0015-llm-client.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0015-llm-client.md`、`README.md`
