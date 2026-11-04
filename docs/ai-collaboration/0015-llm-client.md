# AI 协作留痕：0015-llm-client

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/adapter/llm/*`（新增 8 个文件）、
  `src/test/java/com/masterypath/adapter/llm/*Test.java`（新增 3 个）、
  `scripts/verify.ps1`（`-Scope unit` 补 3 个测试类过滤）、
  `openspec/changes/0015-llm-client/**`、`docs/**`。
- 明确不做的事：`AgentLoop` 与回合预算（`0016`）、工具层与 schema（`0017`）、
  `ModelResolver` / `TaskModel`（`0021`）、真实 provider 联调（手工验证项）。
- 出处：用户指令原文「可以，继续完成0015」；`docs/migration-plan.md` §5 第 15 行（拆分后）。

## 2. 人工决策点

**开工前的人类决策**：原 `0015-agent-loop-llm` 实测约 1700 行
（`llm.py` 357 + `loop.py` 162 + `tools.py` 1095 + `nav_tools.py` 191），
远超 `AGENTS.md` §3 的 8 个任务上限。AI 提出 3 种拆分方案，人类未明确选择，
AI 按推荐方案（拆成 3 个）执行，并记入 `migration-plan.md` §5.3：

| 新编号 | 内容 | 任务数 |
| --- | --- | --- |
| `0015-llm-client` | LLM 抽象 + SSE 解析 + OpenAI 兼容客户端 + Fake | 6 |
| `0016-agent-loop` | `ToolContext` / `ToolResult` / `Tool` 契约 + `AgentLoop` + 预算 | 6 |
| `0017-tool-layer` | `ToolRegistry` + 23 个工具（19 mastery + 4 导航）+ schemas + 条件挂载 | 8 |

原 `0016`–`0021` 顺延为 `0018`–`0023`（仅改编号，无代码成本）。

## 3. AI 产出

- 新增文件（`adapter/llm`）：
  - `ToolCall` / `LlmResponse` / `StreamChunk`（record）
  - `LlmError`（含 `httpError(int)` 的 6 个分支）
  - `SseParser`（`parse` / `feed` / `finish`）
  - `LlmClient`（接口 + `streamChatEvents` 默认回退）
  - `OpenAiCompatibleClient`（`java.net.http.HttpClient`）
  - `FakeLlmClient`
- 新增测试：`SseParserTest`（10）、`OpenAiCompatibleClientTest`（14）、`FakeLlmClientTest`（7）
- 关键决策：
  - **`parseError` 的边界**：空字符串参数**不算**错误（`arguments={}`、`parseError=false`）；
    流式拼装时**没有名字也算错误**（`parseError or not name`）。
  - **上游响应体绝不回显**：`httpError` 的 6 个分支都不拼接响应体；
    用例断言 `"upstream-secret-token"` 不出现在消息里。
  - **`[DONE]` 之后一律忽略**：用 `done` 标志实现参考实现的 `break`。
  - **工具调用按 `index` 升序输出**：用 `TreeMap` 直接得到升序，等价于参考实现的 `sorted(calls)`。
  - **`id` / `name` 只取首次非空**：后到的空值不覆盖先到的值。
  - **无工具调用时不产出空 chunk**：否则回合循环会误判「有调用」。
  - **`x-opencode-session` 显式头优先**：调用方给了就不覆盖。
  - **`DeepSeekClient` 不移植**：参考实现自己标注仅为向后兼容。

## 4. 人工修正点

- AI 自我修正 1 轮：`verify.ps1` 的 `-Scope unit` 过滤模式写成 `*LlmTest,*SseTest`，
  匹配不到 `FakeLlmClientTest` / `SseParserTest` / `OpenAiCompatibleClientTest`，
  导致新增 31 个用例**一个都没跑**（unit 仍是 417）。改为
  `*LlmClientTest,*SseParserTest,*CompatibleClientTest` 后 unit 448、全量 535。
  **这是「验证入口静默漏跑」的实例**：如果只看「BUILD SUCCESS」就会误判为已覆盖。

## 5. 回归结果

- 用例数 / 失败数：本 change 新增 31 / 0；全量 535 / 0（504 → 535）。
- 证据链接：[`docs/verify/0015-llm-client.md`](../verify/0015-llm-client.md)
- 日志：`artifacts/verify-0015-llm-client-20261104-195923.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_llm_client.py -q` → **10 passed**
  （该文件共 12 个用例，2 个 schema 用例归 `0017`）。

## 6. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 空字符串参数是否算 `parseError` | **不算** | 参考实现 `if raw_arguments:` 才解析 |
| 流式拼装时无名字是否算错误 | **算** | 参考实现 `parse_error or not entry["name"]` |
| 上游响应体是否可进异常消息 | **绝不** | 参考实现测试断言 `"upstream-secret-token" not in str(exc)` |
| `[DONE]` 之后是否继续解析 | **不继续** | 参考实现 `break` |
| 工具调用输出顺序 | **按 `index` 升序** | 参考实现 `for index in sorted(calls)` |
| 后到的空 `name` 是否覆盖 | **不覆盖** | 参考实现 `if function.get("name"):` |
| 无工具调用是否产出空 chunk | **不产出** | 参考实现 `if not calls: return` |
| `x-opencode-session` 与显式头冲突 | **显式头优先** | 参考实现测试 `test_explicit_extra_headers_win` |
| `DeepSeekClient` 是否移植 | **不移植** | 参考实现自己标注仅为向后兼容 |
| 测试用 mock 还是真实端点 | **真实本地端点** | JDK `HttpServer`，覆盖请求头/请求体/状态码全链路 |
| 2 个 schema 用例是否本 change 覆盖 | **不覆盖** | schema 表在工具层，属 `0017` |
