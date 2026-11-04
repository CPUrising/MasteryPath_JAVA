# 验证报告：0013-path-binding-scratch

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0013-path-binding-scratch` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 19:31:29（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0013-path-binding-scratch` |
| 日志 | `artifacts/verify-0013-path-binding-scratch-20261104-193129.log`（全量）<br>`artifacts/verify-0013-path-binding-scratch-20261104-193051.log`（unit） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

> **编号说明**：参考实现只在 `tests/test_path_binding.py` 首行声明 `TC-S*` 家族
> （`"""TC-S* — path listing, switching and scratch paths (ADR-021)."""`），
> 未细分 `S1`–`S4`。本报告的 `TC-S1`–`TC-S4` 是 Java 侧的分组标签，不是参考实现的正式编号。

### 2.1 路径概览（TC-S1）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 列出全部已建路径，按 `path_id` 升序 | `PathBindingTest#listsBuiltPathsSorted` | PASS | 对应 `test_paths_lists_built_paths_and_active` 的 id 集合断言 |
| 概览含 9 个键 | `#overviewHasNineKeys` | PASS | 参考实现无形状断言 |
| `objectives` / `mastered` / `not_started` 反映地图 | `#overviewCountsReflectMap` | PASS | |
| 空 map 的路径被跳过 | `#emptyMapPathsHidden` | PASS | scratch 隐藏的**唯一机制** |
| `name` 为空时回退 `path_id` | `#nameFallsBackToPathId` | PASS | 参考实现无此用例 |

### 2.2 scratch 路径（TC-S2）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| id 由 session 派生，owner 为会话 | `#scratchDerivedFromSession` | PASS | 对应 `test_leave_creates_hidden_scratch_path` 的 `startswith("scratch-s1")` + owner |
| 非法字符替换、首尾下划线剥离 | `#scratchSanitisesSessionId` | PASS | `"a b"`→`scratch-a_b`、`"_x_"`→`scratch-x` |
| 全部非法字符兜底 `default` | `#scratchFallsBackToDefault` | PASS | `"!!!"`→`scratch-default` |
| 建档发 `path.created{scratch:true}` | `#scratchEmitsCreatedEvent` | PASS | payload 含 `goal`/`name`/`scratch` |
| 已存在且无 owner 时认领，不发事件 | `#scratchClaimsOwnerWithoutEvent` | PASS | 参考实现无此用例 |
| 重复调用幂等 | `#scratchIsIdempotent` | PASS | |
| 空 scratch 不出现在概览 | `#emptyScratchHiddenFromOverview` | PASS | 对应 `test_leave_creates_hidden_scratch_path` 末段 |

### 2.3 会话绑定（TC-S3）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 绑定把会话指向新路径 | `#bindMovesSession` | PASS | 对应 `test_switch_binds_turn_and_session` 的 session 断言 |
| 空 session 返回 `null` 且不写库 | `#bindEmptySessionIsNoop` | PASS | 参考实现无此用例 |
| 绑定不存在的会话返回 `null` | `#bindUnknownSessionReturnsNull` | PASS | 对应 `test_switch_unknown_path_is_refused` 的「不写库」侧 |

### 2.4 删除会话（TC-S4）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 删除会话时一并删除自有的空 scratch | `#deleteSessionRemovesOwnedEmptyScratch` | PASS | 对应 `test_deleting_session_removes_owned_empty_scratch` |
| 路径有知识点时不删路径 | `#deleteSessionKeepsPathWithKnowledgePoints` | PASS | 参考实现无此用例 |
| 路径不属于该会话时不删路径 | `#deleteSessionKeepsForeignPath` | PASS | 参考实现无此用例 |
| 会话不存在返回 `false` | `#deleteUnknownSessionReturnsFalse` | PASS | 参考实现无此用例 |
| 路径还有其他会话时不删路径 | `#deleteSessionKeepsPathWithOtherSessions` | PASS | 参考实现无此用例 |

### 2.5 存储层转发

| Scenario | 测试方法 | 结果 |
|---|---|---|
| `listSessions(pathId)` 转发 | `#listSessionsForwards` | PASS |
| `getSession(sessionId)` 转发 | `#getSessionForwards` | PASS |

本 change 新增用例：**22 个**（`PathBindingTest`），全部 PASS。
全量 **478 个，0 失败**（456 → 478）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 391, Failures: 0 |
| 全量 | `verify.ps1 -Change 0013-path-binding-scratch` | BUILD SUCCESS，Tests run: 478, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0012 的 456 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_path_binding.py -q`（在 `../MasteryPath`） | **5 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_paths_lists_built_paths_and_active` | `#listsBuiltPathsSorted` + `#overviewCountsReflectMap` | 参考用例经 `MasteryPathsTool` 断言 id 集合与 `active_path_id`；工具层属 `0015`，本 change 在服务层断言同一语义 |
| `test_switch_binds_turn_and_session` | `#bindMovesSession` | 参考用例断言 `ctx.path_id` 与 session 绑定；`ctx` 属工具层（`0015`），本 change 只断言 session 侧 |
| `test_switch_unknown_path_is_refused` | `#bindUnknownSessionReturnsNull` | 参考用例的「拒绝」发生在工具层（先查路径存在性）；服务层 `setSessionPath` 对未知 session 返回 `null`，语义一致 |
| `test_leave_creates_hidden_scratch_path` | `#scratchDerivedFromSession` + `#emptyScratchHiddenFromOverview` | 同断言（id 前缀、owner、不出现在列表） |
| `test_deleting_session_removes_owned_empty_scratch` | `#deleteSessionRemovesOwnedEmptyScratch` | 同断言 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#overviewHasNineKeys` | 参考实现无概览形状断言 |
| `#nameFallsBackToPathId` | 参考实现无 `name or path_id` 回退用例 |
| `#scratchSanitisesSessionId` / `#scratchFallsBackToDefault` | 参考实现无 id 派生边界用例 |
| `#scratchClaimsOwnerWithoutEvent` | 参考实现无「认领不发事件」用例 |
| `#scratchIsIdempotent` | 参考实现无幂等用例 |
| `#bindEmptySessionIsNoop` | 参考实现无空 session 短路用例 |
| `#deleteSessionKeepsPathWithKnowledgePoints` / `#deleteSessionKeepsForeignPath` / `#deleteSessionKeepsPathWithOtherSessions` | 参考实现只测「该删的删了」，未测「不该删的没删」 |
| `#deleteUnknownSessionReturnsFalse` | 参考实现无此用例 |
| `#listSessionsForwards` / `#getSessionForwards` | 参考实现无转发层用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 未实现工具层 `MasteryPathsTool` / `MasterySwitchTool` / `MasteryLeaveTool` | 一般 | 属 `0015`。参考实现三个类共 91 行，只做「参数解析 + 服务层调用 + `ToolResult` 包装」。本 change 用服务层用例覆盖同样的语义（与 `0012` 同理由） |
| D2 | `test_switch_unknown_path_is_refused` 的「拒绝」在工具层，服务层无对应拒绝 | 一般 | 服务层 `setSessionPath` 对未知 session 返回 `null`（不抛错）；「路径不存在则拒绝切换」的判定在工具层，属 `0015`。已登记 `verification-debt.md` |
| D3 | `TC-S1`–`TC-S4` 是 Java 侧分组标签 | 无 | 参考实现只声明 `TC-S*` 家族，未细分；本报告 §2 已注明 |
| D4 | `test_turn_path_switch.py`（`TC-S5`）未覆盖 | 无 | 依赖 WS 回合，属 `0017` |
| D5 | 概览的 `due_reviews` 只断言键存在，未断言具体数值 | 低 | `dueReviews` 的数值语义已由 `0010` 的 `ReviewSchedulingTest` 覆盖；本 change 只验证「概览把它接进来」 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `MasteryPathsTool` / `MasterySwitchTool` / `MasteryLeaveTool` 的工具层包装 | `0015` | 需 `ToolContext` / `ToolResult` |
| 「切换到不存在的路径」的拒绝 | `0015` | 判定在工具层 |
| `test_turn_path_switch.py`（`TC-S5`） | `0017` | 需 WS 回合 |
| `GET /learning/paths`、`DELETE /learning/sessions/{id}` | `0016` | 需 REST 层 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 478, Failures: 0` 来自
`artifacts/verify-0013-path-binding-scratch-20261104-193129.log`；参考实现对照见
`pytest tests/test_path_binding.py` 的 `5 passed`。
