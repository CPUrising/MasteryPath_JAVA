# Design: LLM 客户端抽象与 OpenAI 兼容适配

## Context

参考实现 `agents/llm.py`（357 行）分四块：

| 块 | 行号 | 内容 |
| --- | --- | --- |
| 数据载体 | 18–44 | `ToolCall` / `LLMResponse` / `StreamChunk` |
| 错误 | 46–89 | `LLMError` + `_http_error` 状态映射 |
| SSE 解析 | 92–158 | `parse_stream_lines`：文本增量 + 工具调用分片拼装 |
| 客户端 | 161–396 | `LLMClient` 协议 / `FakeLLMClient` / `DeepSeekClient` / `OpenAICompatibleClient` |

Java 侧 `pom.xml` 已有 `spring-boot-starter-web`（含 Jackson），无需新增依赖：
HTTP 用 JDK 自带 `java.net.http.HttpClient`，JSON 用 `JsonSupport.objectMapper()`。

## Goals / Non-Goals

**Goals**

1. `ToolCall` 的 `parseError` 语义：参数不是合法 JSON 对象时置真，`arguments` 归空。
2. `_http_error` 的 6 个分支与 `code` / `retryable` 逐字复刻，**消息不含上游原文**。
3. `parse_stream_lines` 的分片拼装：按 `index` 归并，`id`/`name` 只取首次非空，
   `arguments` 累加；`[DONE]` 提前结束；无 `id` 时兜底 `call-{index}`。
4. `OpenAiCompatibleClient` 的请求体：`stream` 标志、`tools` + `tool_choice=auto`。
5. `FakeLlmClient` 的 `chunkSize` 切分与 `calls` / `toolsSeen` 记录。

**Non-Goals**

1. 不做 `AgentLoop`（`0016`）。
2. 不做工具层与 schema（`0017`）。
3. 不做 `ModelResolver` / `TaskModel`（`0021`）。
4. 不做 Anthropic 协议（参考实现也只抛「not supported yet」）。

## Decisions

1. **`LlmClient` 用接口而非抽象类**
   - 参考实现是 `Protocol`（结构化类型）。Java 用 `interface` 最接近。
   - `streamChatEvents` 给默认实现：回退到 `chat` 并整段作为一个 `StreamChunk`。
     这样「只实现 `chat` 的旧客户端」也能工作（参考实现 `loop.py:98-105` 有同样的回退）。

2. **`ToolCall.parseError` 的判定**
   - 参考实现：`parse_error = not isinstance(arguments, dict)`，且解析失败时 `arguments = {}`。
   - 关键：**空字符串参数不算错误**（`arguments = {}`，`parse_error = False`）。
   - 流式拼装时额外一条：`parse_error or not entry["name"]`——**没有名字也算错误**。

3. **`_http_error` 的 6 个分支**
   ```
   401/403 → auth_error,        retryable=false
   404     → model_not_found,   retryable=false
   429     → rate_limited,      retryable=true
   400     → bad_request,       retryable=false
   >=500   → upstream_error,    retryable=true
   其他    → upstream_error,    retryable=false（消息含状态码）
   ```
   - **消息里绝不出现上游响应体**：参考实现测试 `test_auth_error_is_structured_and_safe`
     断言 `"upstream-secret-token" not in str(exc)`。

4. **SSE 解析只认 `data:` 前缀行**
   - 参考实现：`if not line or not line.startswith("data:"): continue`。
   - 空行、`event:` 行、注释行全部跳过。
   - `data: [DONE]` 立即 `break`，**不再产出**任何 chunk。

5. **工具调用分片按 `index` 归并，输出按 `index` 升序**
   - 参考实现：`calls.setdefault(index, {...})`，最后 `for index in sorted(calls)`。
   - `id` / `name` 只在非空时覆盖（后到的空值不冲掉先到的值）。
   - `arguments` 用字符串累加，最后一次性 `json.loads`。

6. **`parse_stream_lines` 的产出顺序**
   - 文本 chunk 边解析边产出（`yield`）。
   - 工具调用**只在流末尾产出一次**（一个 `StreamChunk` 携带全部调用）。
   - 无任何工具调用时**不产出**空的工具 chunk（`if not calls: return`）。

7. **`OpenAiCompatibleClient` 的 `_chat_endpoint`**
   - `base_url` 已以 `/chat/completions` 结尾则原样使用，否则追加。
   - 参考实现：`base.rstrip("/")` 后判断。

8. **`x-opencode-session` 头只在 host 以 `opencode.ai` 结尾时注入**
   - 且**显式 `extraHeaders` 优先**：若调用方已给该头，不覆盖。
   - 参考实现测试 `test_explicit_extra_headers_win` 断言这一点。

9. **`FakeLlmClient` 的 `chunkSize`**
   - `0` 表示整段一次产出；`>0` 按字符数切分。
   - 工具调用在文本之后**单独一个 chunk** 产出（参考实现 `stream_chat_events` 顺序）。

10. **`DeepSeekClient` 不移植**
    - 参考实现自己标注「Kept for backwards compatibility; new code uses
      `OpenAICompatibleClient`」。Java 侧没有历史包袱，直接不实现。
    - 已登记到本 change 的 verify 报告偏差表。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 上游错误体被拼进异常消息 | 泄漏密钥/内部信息 | 用例断言 `"upstream-secret-token"` 不出现在消息里 |
| 空字符串参数被判成 `parseError` | 模型发 `arguments: ""` 时被误判为坏参数 | 用例断言 `""` → `arguments={}`、`parseError=false` |
| 流式拼装时后到的空 `name` 覆盖先到的 | 工具名丢失 | 用例构造「先有 name 后无 name」的分片 |
| 无 `id` 时不兜底 | 后续 `tool_call_id` 为空，OpenAI 拒绝历史 | 用例断言 `call-2` 兜底 |
| `[DONE]` 之后继续解析 | 可能读到垃圾数据 | 用例断言 `[DONE]` 后无 chunk |
| 无工具调用时产出空 chunk | 循环误判「有调用」 | 用例断言只产出文本 chunk |
| `extraHeaders` 被 `x-opencode-session` 覆盖 | 调用方无法自定义 | 用例断言显式头优先 |

## Migration Plan

1. 数据载体：`ToolCall` / `LlmResponse` / `StreamChunk` / `LlmError`。
2. `SseParser`（纯函数，可离线单测）。
3. `OpenAiCompatibleClient`（`chat` + `streamChatEvents`）。
4. `FakeLlmClient`。
5. 测试：`SseParserTest` + `OpenAiCompatibleClientTest` + `FakeLlmClientTest`。
6. `verify.ps1 -Scope unit` → 全量 → `pytest tests/test_llm_client.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/agents/llm.py` 为唯一依据。
- 参考实现测试空白（本 change 会补）：
  - `_http_error` 的 404 / 429 / 400 / 5xx / 其他分支（参考只测 401）；
  - `parse_stream_lines` 的 `[DONE]` 提前结束与「无工具调用不产出空 chunk」；
  - `FakeLlmClient` 的 `chunkSize` 切分；
  - `LlmClient` 默认 `streamChatEvents` 回退。
