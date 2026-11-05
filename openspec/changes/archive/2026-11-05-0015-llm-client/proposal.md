# Proposal: LLM 客户端抽象与 OpenAI 兼容适配

## Why

`0001`–`0014` 把「确定性引擎」全部搬完了：掌握门槛、判分、调度、门控、导航。
但导师（tutor）本身还没接上模型——`adapter` 包至今只有 `package-info.java`。

参考实现把模型访问收在一个薄层里（`agents/llm.py`，357 行），并明确三条边界：

1. **domain 层永不 import 供应商 SDK**（`ADR-014`）；循环只依赖 `LLMClient` 协议。
2. **测试必须离线**：`FakeLLMClient` 按脚本返回预置响应，不碰网络。
3. **上游错误体不得回显**：HTTP 状态映射成结构化 `LLMError`，消息里不含上游原文。

没有这一层，`0016` 的回合循环与 `0017` 的工具层都无法测试。

## What Changes

- 新增 `adapter/llm` 包：
  - `LlmClient` 接口：`chat(messages, tools)` + `streamChatEvents(messages, tools)`
  - `ToolCall` / `LlmResponse` / `StreamChunk` 数据载体
  - `LlmError`：`code` + `retryable`，消息安全可展示
  - `SseParser`：把 OpenAI 兼容 SSE 行解析成文本增量 + 拼装的工具调用
  - `OpenAiCompatibleClient`：`java.net.http.HttpClient` 实现，含 `_http_error` 映射
  - `FakeLlmClient`：脚本化离线客户端，记录 `calls` / `toolsSeen`

## Capabilities

### New Capabilities

无新增能力；实现 `specs/agent-turn` 的模型访问边界（该规范未单列客户端章节，
行为以参考实现 `agents/llm.py` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0014` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/adapter/llm/*`（新增 7 个类）
  - `src/test/java/com/masterypath/adapter/llm/*Test.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*LlmTest`、`*SseTest`）
- 影响测试：既有 504 个用例**不修改**。
- 明确不做（Out of scope）：
  - `AgentLoop` 与回合预算 → 属 `0016`。
  - 工具层（`ToolContext` / `ToolResult` / 19 个工具 / schemas）→ 属 `0017`。
  - `test_llm_client.py` 里 2 个 schema 用例（`test_every_tool_has_a_schema`、
    `test_required_parameters_match_the_executors`）→ 属 `0017`，因为 schema 表在工具层。
  - `ModelResolver` / `TaskModel`（从设置解析模型）→ 属 `0021`。
  - 真实 provider 联调 → 需 API Key，属手工验证项（`verification-debt.md` 已登记）。
