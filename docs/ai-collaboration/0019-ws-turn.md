# AI 协作留痕：0019-ws-turn

## 1. prompt 范围

- 授权修改的文件：
  - `src/main/java/com/masterypath/adapter/turn/*`（新增 `TurnRunner`；`TurnRequest` / `TurnEvent` 已在前序步骤建好）
  - `src/main/java/com/masterypath/adapter/llm/ClientBuilder.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/WsTurnHandler.java`、`WebSocketConfig.java`（新增）
  - `src/main/java/com/masterypath/adapter/web/WebConfig.java`（+`turnRunner` bean）
  - `src/main/java/com/masterypath/service/LearningService.java`（+`ensureSession` / `recordTurn`）
  - `pom.xml`（+`spring-boot-starter-websocket`）
  - `src/test/java/com/masterypath/adapter/turn/TurnRunnerTest.java`、`src/test/java/com/masterypath/adapter/web/WsTurnTest.java`（新增）
  - `scripts/verify.ps1`（`-Scope api` 补 `*WsTurnTest`）
  - `openspec/changes/0019-ws-turn/**`、`docs/**`
- 明确不做的事：普通聊天（`0020`）、知识库检索（`0021`）、设置解析（`0022`）、
  评测 CLI（`0023`）、验收（`0024`）。
- 出处：用户指令原文「继续下一个阶段的计划」+ AskUserQuestion 答复
  「拆出 0020-chat-service，后续顺延（推荐）」；`docs/migration-plan.md` §5 第 19 行、§5.4。

## 2. AI 产出

- 服务层缺口：`ensureSession`（已存在原样返回，不改 mode）、`recordTurn`（固定写两条消息，`message_count +2`）
- 编排层（`adapter/turn`）：
  - `TurnRunner`：解析辅助（`sessionMode` / `resolvePathId` / `knowledgeBaseIds` / `sourceIndex`）、
    `MasteryTurn` 惰性事件流（快照注入 → `AgentLoop` 驱动 → 题卡派生 → `session_meta` → 落库）、
    `knowledgeContext`（参考块拼装）、`finishInterruptedTurn` / `persistMasteryTurn`
- 传输层：`WsTurnHandler`（`/ws`，逐条 `sendMessage`）、`WebSocketConfig`（注册端点）
- 新增测试：`TurnRunnerTest`（12）、`WsTurnTest`（3）

## 3. 人工修正点

- AI 自我修正 1 轮（测试隔离问题，非实现缺陷）：
  - **`WsTurnTest` 的 `message_count` 断言失败（4 ≠ 2）**：本类走完整 Spring 上下文，
    工作目录是 `target/test-workspace`（跨运行保留），固定 `session_id` 让上一次运行的
    会话残留、`recordTurn` 在其上累加。改为每次运行生成随机后缀（`run`），
    路径与会话 id 都带后缀。**未放宽断言**。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 15 / 0；全量 690 / 0（675 → 690）。
- 证据链接：[`docs/verify/0019-ws-turn.md`](../verify/0019-ws-turn.md)
- 日志：`artifacts/verify-0019-ws-turn-20261105-100051.log`（全量）、
  `artifacts/verify-0019-ws-turn-20261105-100023.log`（API 专项 85 项）
- 参考实现对照：`pytest tests/test_turn_runner.py tests/test_ws_runtime.py -q` → **9 passed**

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `chat` 能力是否本 change 实现 | **不实现，抛 `UnsupportedOperationException`** | `ChatService` 依赖独立 `chat.db` 与模型解析，属另一种行为增量（`AGENTS.md` §3 的 8 任务上限） |
| `ClientBuilder` 是 Spring Bean 还是函数式接口 | **函数式接口** | 参考实现是闭包，测试直接传 lambda；默认实现（读设置）属 `0022` |
| 生产 `ClientBuilder` 的默认行为 | **抛「未配置模型」** | 静默用空客户端会让真实回合以难以诊断的方式失败 |
| `sessionMode` 返回原始值还是归一值 | **原始值（可为 `null`）** | `null` 表示「未记录模式」，门控放行（`INV-09`）；归一成 `study` 会静默开启门控 |
| `resolvePathId` 的优先级 | **会话绑定优先** | 参考实现 `_resolve_path_id`；这是 `TC-S5` 的前提 |
| `mastery_status` 快照注入失败如何处理 | **吞掉** | 状态读取失败不能拖垮整个回合（参考实现 `logger.warning`） |
| `question_card` 是 `AgentLoop` 产出还是派生 | **派生** | 参考实现从 `tool_result.metadata` 检查后额外发一条 |
| `session_meta` 的位置 | **`turn_end` 之后、落库之前** | 前端靠它更新侧栏；顺序反了会先刷新再结束 |
| 落库失败如何处理 | **吞掉（best-effort）** | 内容已流式送达，报错只会让用户困惑 |
| 纯错误回合是否落库 | **不落** | `has_text or has_card` 才落；否则会话里出现空消息对 |
| WS 端点是否需要后台线程 | **不需要** | Spring 的 `WebSocketHandler` 在独立线程池执行；参考实现需要是因为 FastAPI 的事件循环 |
| WS 消息格式 | **`{"type", "data"}`** | 与 REST 的 `{ok, content, ...}` 不同，不可混用 |
| `MasteryTools.all()` 是否还需合并 `NAV_TOOLS` | **不需要** | Java 侧 23 个工具已含 4 个导航工具（`0017` 实测） |
| `WsTurnTest` 用 `MockMvc` 还是真实端口 | **真实端口（`RANDOM_PORT`）** | `MockMvc` 不处理 WebSocket 升级 |
