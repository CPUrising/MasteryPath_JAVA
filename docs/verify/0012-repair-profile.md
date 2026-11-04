# 验证报告：0012-repair-profile

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0012-repair-profile` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 19:17:17（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0012-repair-profile` |
| 日志 | `artifacts/verify-0012-repair-profile-20261104-191717.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 题目修复（TC-R1 / TC-R2 / TC-R3）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| TC-R1 void 清掌握度与计划 | `RepairQuestionTest#voidRemovesMasteryAndMarksBank` | PASS | mastery→0、state 移除、bank=`voided` |
| TC-R1 void 置 ABANDONED | `#voidAbandonsInteraction` | PASS | |
| void 只清同题 pending | `#voidKeepsPendingQuestionOfOtherQuestion` | PASS | 参考实现无此用例 |
| void 记录审计原因 | `#voidRecordsReason` | PASS | |
| TC-R2 correct 重判恢复门槛 | `#correctRegradesStoredAnswerAndRestoresGate` | PASS | 答 9 判错 → 改键为 9 → 正确 |
| TC-R2 interaction 含 repaired | `#correctMarksInteractionRepaired` | PASS | |
| correct 追加证据并作废旧证据 | `#correctAppendsEvidenceAndVoidsPrior` | PASS | 原因逐字 |
| correct 复活 attempt | `#correctRevivesAttempt` | PASS | |
| correct 不覆盖已作废原因 | `#correctKeepsReasonOfAlreadyVoidedEvidence` | PASS | 参考实现无此用例 |
| 非法 action 拒绝 | `#invalidActionRejected` | PASS | 文案逐字 |
| 缺 reason 拒绝 | `#missingReasonRejected` | PASS | 文案逐字 |
| correct 缺 expected_answer 拒绝 | `#correctWithoutExpectedAnswerRejected` | PASS | 文案逐字 |
| 无题可修复拒绝 | `#noQuestionAvailableRejected` | PASS | 文案逐字 |
| 题号回退到 attempt | `#resolvesQuestionIdFromAttempts` | PASS | |
| 无法判分拒绝 | `#ungradableCorrectionRejected` | PASS | 选择题 `zzz` → ungraded |
| 事件 payload 7 键 | `#eventPayloadHasSevenKeys` | PASS | |
| 返回值 8 键 + revision | `#resultHasEightKeys` | PASS | `path_revision = version+1` |
| 定性目标不写分 | `#repairDoesNotFabricateScoreForQualitative` | PASS | `INV-05` |

### 2.2 学习者画像（TC-P1 / TC-P2）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| TC-P1 合并与可见 | `LearnerProfileTest#mergesAndIsVisible` | PASS | 两次写入，changed 正确 |
| TC-P1 合并保留未提交字段 | `#mergeKeepsOmittedFields` | PASS | |
| 记录 `updated_at` | `#recordsUpdatedAt` | PASS | |
| 持久化到聚合 | `#profilePersisted` | PASS | |
| TC-P2 无变更不发事件 | `#noChangeEmitsNoEvent` | PASS | 参考实现无此用例 |
| 白名单外键静默丢弃 | `#unknownKeysSilentlyDropped` | PASS | |
| 全在白名单外时拒绝 | `#allFieldsRejectedWhenNoneAllowed` | PASS | 文案逐字 |
| 空 map 拒绝 | `#emptyFieldsRejected` | PASS | |
| teaching_strategy 枚举校验 | `#teachingStrategyValidated` | PASS | 文案逐字 |
| null 值跳过 | `#nullValuesSkipped` | PASS | |
| 超长值截断 2000 | `#longValueTruncated` | PASS | |
| 事件 payload 含 fields | `#eventPayloadHasChangedFields` | PASS | |
| 可对不存在路径建档 | `#createsPathIfMissing` | PASS | `create_if_missing` |

### 2.3 状态快照

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 路径不存在返回 missing | `PathStateTest#missingPathReturnsMissing` | PASS | 恰 2 键 |
| 活动路径 19 键齐全 | `#activePathHasAllKeys` | PASS | 已用 Python 探针核对键集合 |
| counts 汇总 | `#countsAggregatesStatuses` | PASS | |
| 掌握后 mastered 递增 | `#masteredCountAndNext` | PASS | next → complete |
| reviews 派生字段 | `#reviewsCarryDerivedFields` | PASS | `due` / `interval_index` / `provenance` |
| INV-07 投影 7 键 | `#pendingQuestionProjectionHidesAnswer` | PASS | 不含 `expected_answer` |
| 无待答题为 null | `#noPendingQuestionIsNull` | PASS | |
| review_settings 形状 | `#reviewSettingsShape` | PASS | |
| session_count | `#sessionCountReflectsSessions` | PASS | |
| learner_profile 暴露 | `#learnerProfileExposed` | PASS | |
| map 条目形状 | `#mapEntriesShape` | PASS | |

本 change 新增用例：**42 个**（`RepairQuestionTest` 18 + `LearnerProfileTest` 13 + `PathStateTest` 11），全部 PASS。
全量 **456 个，0 失败**（414 → 456）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 369, Failures: 0 |
| 全量 | `verify.ps1 -Change 0012-repair-profile` | BUILD SUCCESS，Tests run: 456, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0011 的 414 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_repair_profile.py -q`（在 `../MasteryPath`） | **5 passed** |

### 跨语言数据等价实证

用真实 Python 代码跑同一组操作（临时探针，已删除），输出与 Java 断言逐条一致：

```
== void ==
gate_cleared: True / mastery>=0.9: True
action: void / mastery: 0.0
attempt.voided: True / evidence.voided: True
state removed: True / bank.result: voided
result keys: [action, is_correct, knowledge_point_id, mastered, mastery, next,
              path_revision, question_id, status]   ← 9 键（含 next 内部结构）

== correct ==
mastery before: 0.0 → is_correct: True
attempt.is_correct: True / evidence[-1].result: correct
mastery after: 1.0 / state seeded: True
bank.is_correct: True / interaction.repaired: True

== profile ==
changed1: [prior_knowledge, time_budget]
changed2: [time_budget]
prior kept: knows algebra / has updated_at: True
no-change changed: []
state profile time_budget: 1 week

== validation ==
PreconditionError | action must be 'void' or 'correct'
PreconditionError | a reason is required so the repair can be audited
PreconditionError | correcting a question requires the new expected_answer

== get_state ==
19 个键：counts, coverage, due_reviews, gaps, goal, knowledge_bases, learner_profile,
        map, name, next, path_id, pending_question, proposals, review_settings,
        reviews, session_count, sources, status, version
missing: {status: missing, path_id: nope}
```

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_void_removes_mastery_and_marks_bank` | `#voidRemovesMasteryAndMarksBank` | 同断言 |
| `test_correct_regrades_stored_answer_and_restores_gate` | `#correctRegradesStoredAnswerAndRestoresGate` | 同断言 |
| `test_repair_tool_requires_reason_and_valid_action` | `#invalidActionRejected` + `#missingReasonRejected` | 拆为服务层两例（工具层转换属 `0017`） |
| `test_profile_merges_and_is_visible` | `#mergesAndIsVisible` | 同断言 |
| `test_profile_tool_records_partial_fields` | `#mergesAndIsVisible`（服务层等价） | 工具层属 `0017` |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#voidKeepsPendingQuestionOfOtherQuestion` | 参考实现无「不误清别的目标待答题」用例 |
| `#correctKeepsReasonOfAlreadyVoidedEvidence` | 参考实现无「不覆盖已作废原因」用例 |
| `#correctAppendsEvidenceAndVoidsPrior` / `#correctRevivesAttempt` | 参考实现无证据链与 attempt 复活的细粒度断言 |
| `#eventPayloadHasSevenKeys` / `#resultHasEightKeys` | 参考实现无事件/返回值形状断言 |
| `#ungradableCorrectionRejected` | 参考实现无「改正后无法判分」用例 |
| `#repairDoesNotFabricateScoreForQualitative` | 参考实现无定性类型修复的 `INV-05` 用例 |
| `LearnerProfileTest#noChangeEmitsNoEvent` | 参考实现无「无变更不发事件」用例 |
| `#unknownKeysSilentlyDropped` / `#allFieldsRejectedWhenNoneAllowed` | 参考实现无白名单边界用例 |
| `#longValueTruncated` / `#nullValuesSkipped` | 参考实现无清洗边界用例 |
| `PathStateTest`（11 项） | 参考实现无 `get_state` 的独立测试 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 未实现工具层 `MasteryRepairQuestionTool` / `MasteryProfileTool` | 一般 | 属 `0017`（工具层）。参考实现的两个工具类各 34/49 行，只是把服务层异常转成 `ToolResult`；本 change 用服务层用例覆盖同样的校验分支 |
| D2 | `recomputeAfterRepair` 与 `scheduleAfter` 未抽公共函数 | 无（刻意） | 两者条件不同（前者先重算分数，后者按 gateCleared/review/touch 三分支）；参考实现也是两个独立函数 |
| D3 | 原计划 `0012-repair-profile-nav` 拆为三个 change | 无（已批准） | 实测 17 个任务超 `AGENTS.md` §3 上限，人类批准拆分；见 `migration-plan.md` §5.2 |
| D4 | `getState` 文档初稿误写「21 个键」 | 无（已修正） | Python 探针实测为 **19 个键**，已同步修正 `design.md` / `tasks.md` / 测试注释 |
| D5 | `test_repair_tool_requires_reason_and_valid_action` 的工具层断言未移植 | 一般 | 拆为服务层 `#invalidActionRejected` + `#missingReasonRejected`。**注意：这两例只覆盖「校验规则」，不覆盖「异常 → `ToolResult(ok=false)` 的转换」**——后者是参考实现该用例的真正断言对象。已登记到 `verification-debt.md`「工具层：修复与画像」行，`0017` 结算 |
| D6 | `test_chat_nav.py` 端到端未覆盖 | 无 | 依赖 `ChatService` + `AgentLoop` + 假 LLM，挂到 `0019-ws-turn` |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `MasteryRepairQuestionTool` / `MasteryProfileTool` 的工具层包装 | `0017` | 需 `ToolContext` / `ToolResult`。**校验规则**已覆盖；**异常 → `ok=false` 的转换**未覆盖（参考实现 `test_repair_tool_requires_reason_and_valid_action` 的真正断言对象） |
| `test_chat_nav.py` 端到端 | `0019` | 需 `ChatService` + `AgentLoop` |
| `repair_question` 的 REST 端点 | `0018` | 需 REST 层 |
| `getState` 的 REST 端点 | `0018` | 同上 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 456, Failures: 0` 来自
`artifacts/verify-0012-repair-profile-20261104-191717.log`；参考实现对照见
`pytest tests/test_repair_profile.py` 的 `5 passed`；跨语言数据等价由真实 Python 探针
逐条验证（输出见 §3）。
