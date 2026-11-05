# Tasks

## 1. 服务层缺口

- [x] 1.1 `LearningService.ensureSession(pathId, sessionId, mode, title)`：已存在原样返回，否则建档
- [x] 1.2 `LearningService.recordTurn(pathId, sessionId, userText, assistantText, mode, turnId, card)`：写两条消息 + 更新会话行

## 2. 回合编排

- [x] 2.1 `adapter/llm/ClientBuilder`：`@FunctionalInterface`，`build(selection, sessionKey)`
- [x] 2.2 `adapter/turn/TurnRequest` + `TurnEvent`：与参考实现同名同字段
- [x] 2.3 `TurnRunner` 解析辅助：`sessionMode` / `resolvePathId` / `knowledgeBaseIds` / `sourceIndex`
- [x] 2.4 `TurnRunner.runMastery`：上下文装配 + `mastery_status` 快照注入 + `AgentLoop` 驱动 + `question_card` 派生 + `session_meta`
- [x] 2.5 `TurnRunner` 持久化：`finishInterruptedTurn` / `persistMasteryTurn`

## 3. WS 端点

- [x] 3.1 `adapter/web/WsTurnHandler` + `WebSocketConfig`：`/ws`，逐条 `sendMessage`
- [x] 3.2 `pom.xml` 加 `spring-boot-starter-websocket`

## 4. 测试与验证

- [x] 4.1 `TurnRunnerTest`：事件顺序、`question_card` 派生、`session_meta`、错误映射、部分文本保留、快照注入
- [x] 4.2 `WsTurnTest`：端到端 WS 回合、多帧到达、错误不关 socket
- [x] 4.3 `.\scripts\verify.ps1 -Scope api -Change 0019-ws-turn`
- [x] 4.4 全量 `.\scripts\verify.ps1 -Change 0019-ws-turn`
- [x] 4.5 与参考实现对照（`pytest tests/test_turn_runner.py tests/test_ws_runtime.py -q`）
- [x] 4.6 回填 `docs/verify/0019-ws-turn.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0019-ws-turn.md`、`README.md`
