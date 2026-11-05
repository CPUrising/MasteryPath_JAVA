# 验证报告：0019-ws-turn

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0019-ws-turn` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-05 10:01:33（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0019-ws-turn` |
| 日志 | `artifacts/verify-0019-ws-turn-20261105-100051.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 回合编排（`TurnRunnerTest`，12 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 事件序列 `tool_call → tool_result → text_delta → turn_end` | `#masteryTurnUsesSharedRunner` | PASS | 对应 `test_ws_mastery_uses_shared_runner` |
| 会话落库两条消息（`message_count == 2`） | 同上 | PASS | `recordTurn` 固定 `+2` |
| 题卡从 `tool_result.metadata` 派生 | `#derivesQuestionCardFromToolResult` | PASS | 序列 `tool_call → tool_result → question_card → turn_end{card}` |
| 题卡不含答案（`INV-07`） | 同上 | PASS | 断言无 `expected_answer` / `explanation` |
| 模式切换发 `session_meta` | `#modeSwitchEmitsSessionMeta` | PASS | 对应 `test_ws_mode_switch_emits_session_meta` |
| `session_meta` 在 `turn_end` **之后** | 同上 | PASS | 顺序不可调换 |
| 模式未变不发 `session_meta` | `#noSessionMetaWhenNothingChanged` | PASS | |
| `LlmError` → `error{error_code}` | `#mapsLlmError` | PASS | 对应 `test_ws_mastery_maps_llm_error` |
| 纯错误回合不落库 | 同上 | PASS | `getSessionDetail` 返回 `null` |
| 非 `LlmError` → `internal_error` | `#mapsUnexpectedException` | PASS | 对应 `test_ws_mastery_never_drops_the_socket` |
| 流中断保留已产出正文 | `#keepsPartialTextWhenStreamFails` | PASS | 对应 `test_ws_keeps_partial_text_when_the_stream_fails` |
| 正文按片转发（多帧） | `#forwardsIncrementalTextFrames` | PASS | 对应 `test_ws_forwards_incremental_text_frames` |
| 注入参考块 + 快照 | `#injectsKnowledgeContextAndSnapshot` | PASS | 对应 `test_mastery_runner_injects_knowledge_context` |
| 无路径时不注入快照 | `#snapshotFailureDoesNotSinkTheTurn` | PASS | 快照注入只在 `path_id` 非空时发生 |
| `chat` 能力明确抛 `UnsupportedOperationException` | `#chatCapabilityIsNotImplementedYet` | PASS | 属 `0020-chat-service` |
| **TC-S5**：回合内 `mastery_switch` 持久化新绑定 | `#switchPathInsideTurnPersistsBinding` | PASS | 对应 `test_turn_path_switch.py#test_ws_switch_persists_session_path` |

### 2.2 WS 端点（`WsTurnTest`，3 例，真实端口）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 逐帧推送 + 会话落库 | `#streamsMasteryTurnFrameByFrame` | PASS | `RANDOM_PORT` + `StandardWebSocketClient` |
| 错误映射且不静默关 socket | `#mapsLlmErrorWithoutDroppingTheSocket` | PASS | 收到 `error` 帧后正常关闭 |
| 多帧到达 | `#forwardsIncrementalTextFrames` | PASS | `chunkSize=3`，`deltas >= 2` |

`WsTurnTest` 同时证明「WS 实现已安装」——`spring-boot-starter-websocket` 在依赖里，
端点能完成握手与逐帧推送（对应 `test_ws_runtime.py#test_a_websocket_implementation_is_installed`）。

本 change 新增用例：**15 个**（`TurnRunnerTest` 12 + `WsTurnTest` 3），全部 PASS。
全量 **690 个，0 失败**（675 → 690）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| API 专项 | `verify.ps1 -Scope api -Change 0019-ws-turn` | Tests run: 85, Failures: 0 |
| 全量 | `verify.ps1 -Change 0019-ws-turn` | BUILD SUCCESS，Tests run: 690, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0018 的 675 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_turn_runner.py tests/test_ws_runtime.py -q` | **9 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_ws_mastery_uses_shared_runner` | `TurnRunnerTest#masteryTurnUsesSharedRunner` + `WsTurnTest#streamsMasteryTurnFrameByFrame` | 同断言（事件序列 + `message_count == 2`） |
| `test_ws_mastery_maps_llm_error` | `#mapsLlmError` + `WsTurnTest#mapsLlmErrorWithoutDroppingTheSocket` | 同断言（`model_not_found`） |
| `test_ws_mastery_never_drops_the_socket` | `#mapsUnexpectedException` | 同断言（`internal_error`） |
| `test_mastery_runner_injects_knowledge_context` | `#injectsKnowledgeContextAndSnapshot` | 同断言（`Reference material` / `gravity.md` / 快照抬头） |
| `test_ws_mode_switch_emits_session_meta` | `#modeSwitchEmitsSessionMeta` | 同断言（`mastery_session_mode == "study"`） |
| `test_ws_keeps_partial_text_when_the_stream_fails` | `#keepsPartialTextWhenStreamFails` | 同断言（`messages[1].content == "partial answer"`） |
| `test_ws_forwards_incremental_text_frames` | `#forwardsIncrementalTextFrames` + `WsTurnTest#forwardsIncrementalTextFrames` | 同断言（拼接等于原文，帧数 ≥ 2） |
| `test_ws_runtime.py#test_a_websocket_implementation_is_installed` | `WsTurnTest`（端到端） | Java 侧用「端点真的能跑」间接证明，比断言依赖存在更强 |
| `test_turn_path_switch.py#test_ws_switch_persists_session_path` | `#switchPathInsideTurnPersistsBinding` | 同断言（`session["path_id"] == "p2"`） |
| `test_create_path_with_knowledge_bases` | — | **属 `0021-knowledge-base`**（`POST /learning/paths` 的 `knowledge_base_ids`） |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#noSessionMetaWhenNothingChanged` | 参考实现无「模式未变不发 `session_meta`」用例 |
| `#derivesQuestionCardFromToolResult` 的 `INV-07` 断言 | 参考实现未断言题卡不含答案 |
| `#mapsLlmError` 的「纯错误回合不落库」断言 | 参考实现未断言 `_finish_interrupted_turn` 的 `has_text or has_card` 条件 |
| `#snapshotFailureDoesNotSinkTheTurn` | 参考实现无「无路径不注入快照」用例 |
| `#chatCapabilityIsNotImplementedYet` | 参考实现无「未实现能力明确失败」用例（Java 侧为分阶段交付的显式契约） |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `chat` 能力抛 `UnsupportedOperationException` | 低 | 属 `0020-chat-service`；`design.md` 决策 1 已声明，且有用例锁定该行为 |
| D2 | `knowledgeService` 传 `null`，知识库正文拼装未实现 | 低 | 属 `0021-knowledge-base`；本 change 只做文件来源与 `excerpt`（`design.md` 决策 16） |
| D3 | 生产 `ClientBuilder` 直接抛「未配置模型」 | 低 | 从设置解析模型属 `0022-settings-task-model`；在此之前真实回合应明确失败而非静默用空客户端 |
| D4 | `WsTurnHandler` 同步迭代而非「后台线程 + 队列」 | 无 | Spring 的 `WebSocketHandler` 本身在独立线程池执行，不需要额外线程（`design.md` 决策 10） |
| D5 | `WsTurnTest` 用 `RANDOM_PORT` 而非 `MockMvc` | 无（必要） | `MockMvc` 不处理 WebSocket 升级 |
| D6 | `WsTurnTest` 的 id 带随机后缀 | 无（必要） | 走完整 Spring 上下文时工作目录是 `target/test-workspace`（跨运行保留），固定 id 会让 `message_count` 累加 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| 普通聊天（`ChatService` / `ChatStore` / SSE 端点） | `0020` | 独立 `chat.db` 与模型解析，属另一种行为增量 |
| 知识库检索与 `knowledge_context` 真实实现 | `0021` | 知识库服务本体 |
| 设置落盘、提供方探测、`resolve_llm_config` | `0022` | 设置与任务模型 |
| `suggest-question`、`eval` 端点 | `0022` / `0023` | 依赖 Task Model / 评测 CLI |
| 前端联调 | `0024` | 需全部端点就绪 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 690, Failures: 0` 来自
`artifacts/verify-0019-ws-turn-20261105-100051.log`；API 专项 85 项来自
`artifacts/verify-0019-ws-turn-20261105-100023.log`；参考实现对照见
`pytest tests/test_turn_runner.py tests/test_ws_runtime.py -q` 的 `9 passed`。
