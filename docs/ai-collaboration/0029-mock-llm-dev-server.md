# AI 协作留痕：0029-mock-llm-dev-server

## 1. prompt 范围

- 用户指令：「本地 Mock LLM开始然后，两个顺手该修的隐患」。
- 按 `openspec/AGENTS.md` §3「一个 change 只含一个行为增量」，拆成两个 change：
  - 本 change 只做本地 Mock LLM 服务。
  - `0030-test-hygiene` 只做测试分类与工作目录。
- 授权修改：
  - 新增 `scripts/mock-llm/mock-llm.mjs`
  - 修改 `README.md`（新增「不配 Key：本地 Mock LLM」小节）
  - 新增 `openspec/changes/0029-mock-llm-dev-server/**`
- 明确不做：
  - 不进 Spring 上下文、不改 `src/main/**` 与 `src/test/**`。
  - 不实现 Anthropic 协议、鉴权、对话持久化。
  - 不被自动化测试引用（测试继续用离线的 `FakeLlmClient`）。

## 2. AI 产出

- `mock-llm.mjs`：
  - `POST /v1/chat/completions`：SSE（逐片、可配延迟）与整包两种响应；
  - `GET /v1/models`：返回 `mock-tutor`，供设置页探测；
  - 剧本：tool 收尾 → 文本；`outline` → `mastery_build`；`study` → `mastery_quiz`；
    含「主题/list」→ `mastery_topics`；其余 → 含 Markdown 的固定文本；
  - 每次请求打印 `mode / 命中剧本 / 工具名` 一行。
- README：启动命令、`base_url` 配置、剧本对照表、与 `FakeLlmClient` 的边界说明。

## 3. 人工修正点

1. **语言选型**：最初考虑 `java MockLlm.java`（与仓库技术栈一致），但单文件无法使用 Jackson，
   需手写 JSON 解析；改为 Node 单文件（`node:http` + 内置 `JSON`），零依赖且 Node 已是前端前置条件。
2. **工具可用性校验**：初版直接按关键词返回工具调用，后加「工具必须出现在请求 `tools` 里」，
   否则会绕过真实模式门控，观察结果失真。
3. **补 `mastery_topics` 剧本**：端到端验证普通聊天链路时需要一次工具回合，故增加该分支。
4. **探测脚本选型（工具问题，非产品问题）**：PowerShell `ClientWebSocket` 因 Tomcat 返回
   `Connection: upgrade, keep-alive` 被 .NET 判为非法头，改用 Node 内置 `WebSocket` 完成 WS 冒烟。
   该脚本只存在于 `artifacts/`，未入库。

未出现「放宽断言」「删除用例」「伪造验证结果」的情况。

## 4. 回归结果

- 用例数 / 失败数：全量 **868 / 0**（生产代码零改动）
- 证据链接：[`docs/verify/0029-mock-llm-dev-server.md`](../verify/0029-mock-llm-dev-server.md)
- 手工证据（gitignored）：`artifacts/verify29/`
  - `models.json`、`turn_topics.sse`、`turn_outline_ws.jsonl`、`turn_quiz_ws.jsonl`

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否把 mock 做成 Spring Profile / Bean | **否** | 目标是模拟供应商端点；改生产装配会扩大范围 |
| 是否把 mock 用到自动化测试 | **否** | `AGENTS.md` 要求离线测试；mock 只服务手工联调 |
| 是否让 mock 无视模式门控 | **否** | 只调用请求 `tools` 中真实存在的工具 |
| 文本分片是否固定 | **是** | 6 字符/片，便于观察逐字渲染；可用 `--delay-ms 0` 关闭延迟 |
| 是否记录对话 | **否** | 无状态，每轮独立判断 |
