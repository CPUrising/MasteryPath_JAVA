# Proposal: 本地 Mock LLM 开发服务

## Why

体验 agent 功能目前只有一条路：配置真实 provider（`data/settings.json` 的 `base_url` + 明文 Key），
再启动后端与前端手工点。这带来三个问题：

1. **不可复现**：同一句话每次回答不同，出不出工具调用、先问还是先讲全看模型。
2. **有成本与等待**：每次联调都消耗真实请求，还要等首字。
3. **看不见内部**：前端只显示最终文本，看不到 `tool_call` / `tool_result` 事件，
   排查「为什么没出题」「为什么提示模式不允许」只能翻日志。

`0025-manual-verification` 已经把真实 provider 的流式与 function calling 验过一次，
但那次验证无法反复执行（见 `docs/verification-debt.md` 第 67 行）。

## What Changes

- 新增**开发用**的本地 OpenAI 兼容服务 `scripts/mock-llm/mock-llm.mjs`（Node 内置模块，零依赖）：
  - `POST /v1/chat/completions`：同时支持 `stream: true`（SSE 逐帧）与 `stream: false`。
  - `GET /v1/models`：返回一个固定模型，供设置页「测试连接 / 获取模型」使用。
  - 按请求里的系统提示 `Current mode: <mode>` 与最后一条用户消息关键词选择剧本：
    | 条件 | 动作 |
    | --- | --- |
    | 最后一条消息是 `tool` | 输出一段总结文本（结束回合） |
    | `outline` + 含「大纲/规划/开始学」+ 挂了 `mastery_build` | 调用 `mastery_build` |
    | `study` + 含「出题/考我/练习」+ 挂了 `mastery_quiz` | 调用 `mastery_quiz` |
    | 含「主题/课程/list」+ 挂了 `mastery_topics` | 调用 `mastery_topics`（供普通聊天链路使用） |
    | 其余 | 输出一段含 Markdown 的固定文本 |
  - 每次请求向 stdout 打印一行：模式、命中的剧本、工具名，便于观察 agent 行为。
- `README.md` 增加「不配 Key 体验 agent」小节：启动 mock → 设置 `base_url` → 跑通建大纲/出题。

## Capabilities

### New Capabilities

无。Mock 服务是开发工具，不进入 Spring 上下文，不改变 `openspec/specs/` 中的任何契约。

### Modified Capabilities

无。`skip_specs: true`：REST 路径、WS 事件、工具 schema、判分规则均不变。

## Impact

- 新增：`scripts/mock-llm/mock-llm.mjs`
- 修改：`README.md`（新增一小节）
- 不受影响：`src/main/**`、`src/test/**`、数据库、前端源码、基线规范。
- 约束：该服务只用于本地开发与手工演示，**不得**被自动化测试引用（测试仍用 `FakeLlmClient`，离线）。
- 明确不做：不实现 Anthropic 协议、不做鉴权、不持久化对话、不模拟知识库嵌入模型。
