# Design: 本地 Mock LLM 开发服务

## 1. 为什么用 Node 而不是 Java

- 生产代码的模型访问统一走 `LlmClient`（`adapter/llm`），但本 change 要模拟的是**供应商 HTTP 端点**，
  不是内部接口，因此不需要进入 Spring 上下文。
- Node 已是对前端的硬依赖（`README` §0），`node:http` + 内置 `JSON` 足以实现，
  不需要为解析请求体引入 Jackson 或手写 JSON 解析。
- 单文件 `java MockLlm.java` 虽然可行，但要么加 Jackson 依赖、要么手写 JSON 解析，
  对「开发工具」这个定位成本过高。

## 2. 端点与协议

`base_url` 由用户填 `http://127.0.0.1:9099/v1`，与被测的 `OpenAiCompatibleClient` 对齐：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/v1/chat/completions` | 回合调用；`stream` 决定 SSE 或整包 |
| `GET` | `/v1/models` | 设置页「测试连接 / 获取模型列表」 |

SSE 帧格式必须满足 `SseParser` 的解析约定（`src/main/java/com/masterypath/adapter/llm/SseParser.java`）：

```
data: {"choices":[{"delta":{"content":"片"}}]}
data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"mastery_build","arguments":"{\"modules\":[...]}"}}]}}]}
data: [DONE]
```

- 文本按小片（默认 6 个字符）下发，便于肉眼观察逐字渲染。
- 工具调用在文本之后一次性下发；`arguments` 是 JSON 字符串，不是对象。
- 非流式分支返回 `{"choices":[{"message":{"content":...,"tool_calls":[...]}}]}`。

## 3. 剧本选择

只依赖两处请求内容，保持可预测：

1. **模式**：系统提示里的 `Current mode: <mode>`（`AgentLoop` 已写入）。
2. **意图**：最后一条 `role=user` 消息的关键词。

选择顺序：

```
最后一条是 tool            → 总结文本（turn_end = done）
outline + 建大纲关键词      → mastery_build
study   + 出题关键词        → mastery_quiz
其余                        → 固定 Markdown 文本
```

工具必须出现在请求的 `tools[].function.name` 里才会被调用——这样「模式门控拒绝某个工具」时，
mock 不会硬造一个不存在的调用，观察到的仍是真实门控行为。

## 4. 剧本内容

- `mastery_build`：两个模块、四个知识点，覆盖 `memory/procedure/concept/design` 四种类型，
  用来演示大纲、进度环与「先讲后考」。
- `mastery_quiz`：一道 `choice` 题（`question_type` 合法值为 `choice/short/open`），
  `expected_answer` 指向选项标签，带 `explanation`，用于演示题卡与判分反馈。

固定文本刻意包含 `**加粗**`、列表与行内代码，顺带验证前端 Markdown 渲染。

## 5. 已知限制

| 限制 | 说明 |
| --- | --- |
| 只认中文关键词 | 英文关键词只覆盖少量（`outline` / `plan` / `quiz`） |
| 题目固定绑定 `k1` | mock 不解析真实知识点 id；在 mock 自己建出的路径上有效 |
| 不模拟错误 | 想验证 401/500 分支，直接在设置页填错 URL 即可 |
| 不做多轮记忆 | 每轮独立判断，不维护服务端会话状态 |
