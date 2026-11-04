# 验证报告：0016-agent-loop

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0016-agent-loop` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 20:11:11（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0016-agent-loop` |
| 日志 | `artifacts/verify-0016-agent-loop-20261104-201111.log`（全量）<br>`artifacts/verify-0016-agent-loop-20261104-201030.log`（unit） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

> **编号说明**：参考实现 `tests/test_loop.py` 首行只写
> `"""Agent loop + mode gating at call time + fake client (spec 05 §三, 02)."""`，
> 没有 `TC-*` 编号。本报告的 `TC-LOOP1`–`TC-LOOP5` 是 Java 侧分组标签。

### 2.1 基本流程（TC-LOOP1）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 模型不再请求工具 → `done` | `AgentLoopTest#finishesWithDone` | PASS | |
| 工具执行后进入下一轮 | `#executesToolThenContinues` | PASS | 事件顺序 `tool_call → tool_result → text_delta → turn_end` |
| 系统提示词按模式框定 | `#systemPromptFramesMode` | PASS | 对应 `test_loop_builds_outline_then_finishes` 的提示词断言 |
| 未记录模式按 `study` 框定 | `#unrecordedModeFramesAsStudy` | PASS | 参考实现无此用例 |
| `extraContext` 追加在末尾 | `#extraContextAppended` | PASS | 参考实现无此用例 |
| 自定义提示词覆盖默认 | `#customSystemPromptWins` | PASS | 参考实现无此用例 |

### 2.2 工具定义（TC-LOOP2）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 按名字升序 + 三键齐全 | `#toolDefinitionsSorted` | PASS | 对应 `test_loop_sends_tool_definitions` 的「schema 到达客户端」 |

### 2.3 预算（TC-LOOP3）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 单轮超限 → `budget`，超限那次不执行 | `#budgetStopsBeforeExecuting` | PASS | `INV-60`；参考实现无此用例 |
| 预算跨轮累计 | `#budgetAccumulatesAcrossRounds` | PASS | 参考实现无此用例 |

### 2.4 出题结束回合（TC-LOOP4）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| `endTurn` 后先交付 `tool_result` 再 `card` | `#endTurnYieldsCardAfterResult` | PASS | `INV-44`；参考实现无此用例 |
| 轮数耗尽 → `max_rounds` | `#stopsAtMaxRounds` | PASS | 对应 `test_loop_stops_at_max_rounds` |

### 2.5 工具失败与历史（TC-LOOP5）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 坏参数 → `invalid tool arguments` | `#badArgumentsDegrade` | PASS | 对应 `test_loop_degrades_on_bad_arguments` |
| 未知工具 → `unknown tool: x` | `#unknownToolDegrades` | PASS | 对应 `test_loop_unknown_tool` |
| 工具抛异常 → `tool failed: ...` | `#raisingToolDegrades` | PASS | 对应 `test_loop_survives_a_raising_tool` |
| assistant `tool_calls` + tool 配对 | `#writesOpenAiToolHistory` | PASS | 对应 `test_loop_writes_openai_tool_history` |
| 空正文写 `null` | `#emptyAssistantContentIsNull` | PASS | 参考实现无此用例 |
| 有正文保留正文 | `#assistantContentKept` | PASS | 参考实现无此用例 |
| `text_delta` 先于工具执行 | `#textArrivesBeforeToolRuns` | PASS | 对应 `test_loop_streams_text_before_running_a_tool` |
| 只实现 `chat` 的客户端可用 | `#plainClientWorks` | PASS | 对应 `test_loop_falls_back_to_chat_for_a_client_without_streaming` |

本 change 新增用例：**19 个**（`AgentLoopTest`），全部 PASS。
全量 **554 个，0 失败**（535 → 554）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 467, Failures: 0 |
| 全量 | `verify.ps1 -Change 0016-agent-loop` | BUILD SUCCESS，Tests run: 554, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0015 的 535 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_loop.py -q`（在 `../MasteryPath`） | **10 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_loop_builds_outline_then_finishes` | `#systemPromptFramesMode` + `#executesToolThenContinues` | 参考用例断言 `mastery_build` 真的建出地图；**工具本体属 `0017`**，本 change 只覆盖提示词与事件顺序 |
| `test_loop_refuses_tool_in_wrong_mode` | — | **归 `0017`**：断言对象是 `mastery_build` 的门控文案 |
| `test_loop_unknown_tool` | `#unknownToolDegrades` | 同断言 |
| `test_loop_stops_at_max_rounds` | `#stopsAtMaxRounds` | 同断言 |
| `test_loop_sends_tool_definitions` | `#toolDefinitionsSorted` | 同断言（schema 到达客户端） |
| `test_loop_writes_openai_tool_history` | `#writesOpenAiToolHistory` | 同断言 |
| `test_loop_degrades_on_bad_arguments` | `#badArgumentsDegrade` | 同断言 |
| `test_loop_survives_a_raising_tool` | `#raisingToolDegrades` | 同断言 |
| `test_loop_streams_text_before_running_a_tool` | `#textArrivesBeforeToolRuns` | 同断言（惰性流） |
| `test_loop_falls_back_to_chat_for_a_client_without_streaming` | `#plainClientWorks` | 同断言 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#budgetStopsBeforeExecuting` / `#budgetAccumulatesAcrossRounds` | 参考实现无预算用例（`INV-60` 只有常量） |
| `#endTurnYieldsCardAfterResult` | 参考实现无「出题结束回合」的独立用例 |
| `#unrecordedModeFramesAsStudy` / `#extraContextAppended` / `#customSystemPromptWins` | 参考实现无提示词边界用例 |
| `#emptyAssistantContentIsNull` / `#assistantContentKept` | 参考实现无 `content` 为 `null` 的断言 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 2 个依赖真实工具的用例未覆盖 | 一般 | 归 `0017`（`mastery_build` 本体与门控文案）。已登记 `verification-debt.md` |
| D2 | `run(...)` 的 `enforce_mode_set` 参数未移植 | 无 | 参考实现该参数**从未使用**（死参数）；移植会误导 |
| D3 | `stream` 返回 `Iterator` 而非生成器 | 无 | 用显式状态机实现惰性；`#textArrivesBeforeToolRuns` 证明语义等价 |
| D4 | `ToolContext.service` 声明为 `Object` | 低 | 本 change 不依赖服务层；`0017` 会收窄为 `LearningService`。已登记 |
| D5 | 预算测试初稿写错（每轮 1 次调用） | 无（已修正） | `maxRounds=6` 会先触发 `max_rounds`，永远到不了第 7 次；改为单轮 7 次调用 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `test_loop_builds_outline_then_finishes` 的「地图真的建出来」 | `0017` | 需 `mastery_build` 工具本体 |
| `test_loop_refuses_tool_in_wrong_mode` | `0017` | 需门控文案与 `mastery_build` |
| 23 个工具与 schema 表 | `0017` | 本 change 只做契约与循环 |
| `TurnRunner` / WS 回合 | `0019` | 需 REST/WS 层 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 554, Failures: 0` 来自
`artifacts/verify-0016-agent-loop-20261104-201111.log`；参考实现对照见
`pytest tests/test_loop.py` 的 `10 passed`。
