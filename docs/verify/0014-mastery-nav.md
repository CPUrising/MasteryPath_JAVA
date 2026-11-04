# 验证报告：0014-mastery-nav

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0014-mastery-nav` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 19:45:59（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0014-mastery-nav` |
| 日志 | `artifacts/verify-0014-mastery-nav-20261104-194559.log`（全量）<br>`artifacts/verify-0014-mastery-nav-20261104-194519.log`（unit） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

> **编号说明**：参考实现只在 `tests/test_mastery_nav.py` 首行声明 `TC-N*` 家族
> （`"""TC-N* — read-only mastery navigation and hand-off payload (ADR-022)."""`），
> 未细分编号。本报告的 `TC-N1`–`TC-N5` 是 Java 侧分组标签，不是参考实现的正式编号。
> 注意：`docs/traceability.md` 里已有的 `TC-N1`–`TC-N6` 是**大纲覆盖报告**的场景，
> 与本 change 的导航分组**不是同一套编号**，故本报告改用 `TC-NAV1`–`TC-NAV5` 以免混淆。

### 2.1 课程卡片（TC-NAV1）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 列出全部已建课程 + 模块大纲 + 进度 | `NavigationTest#listsBuiltTopics` | PASS | 对应 `test_topics_lists_built_paths_and_filters` 前半 |
| `query` 按名称/目标/模块名过滤，大小写不敏感 | `#queryFiltersByHaystack` | PASS | 对应同一用例后半 |
| `haystack` 不含 `path_id` | `#haystackExcludesPathId` | PASS | 参考实现无此用例 |
| `query` 键只在原始 query 为真值时出现 | `#queryKeyOnlyWhenTruthy` | PASS | 空白是真值：不过滤但键出现 |
| 空 map 的路径不出现 | `#emptyMapPathHidden` | PASS | |
| 模块超 40 截断 + `modules_omitted` | `#modulesTruncated` | PASS | 参考实现无此用例 |
| 未超限时无 `modules_omitted` 键 | `#noModulesOmittedKeyWhenUnderLimit` | PASS | 参考实现无此用例 |
| 课程超 20 截断 + `topics_omitted` | `#topicsTruncated` | PASS | 参考实现无此用例 |

### 2.2 单条课程（TC-NAV2）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 返回单条课程，`modules` 不截断 | `#findTopicReturnsCard` | PASS | |
| 空 map 的路径返回 `null` | `#findTopicEmptyMapIsNull` | PASS | 参考实现无此用例 |
| 空/空白/不存在返回 `null` | `#findTopicMissingIsNull` | PASS | |
| 先 `strip()` 再查库 | `#findTopicStripsPathId` | PASS | 参考实现无此用例 |

### 2.3 模块解析（TC-NAV3）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 按 `module_id` 匹配 | `#resolveModuleById` | PASS | 对应 `test_sessions_and_open_handoff` 的 `module="1"` |
| 按名称匹配，大小写不敏感 | `#resolveModuleByName` | PASS | 参考实现无此用例 |
| 按 1 基序号匹配，容忍尾部句点 | `#resolveModuleByOrder` | PASS | 参考实现无此用例 |
| 空 ref / 无匹配返回 `null` | `#resolveModuleNoMatch` | PASS | |

### 2.4 会话列表（TC-NAV4）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 标题、消息数、最后消息 | `#listsSessions` | PASS | 对应 `test_sessions_and_open_handoff` 前半 |
| 跳过 `archived`（含字符串 `"true"`） | `#skipsArchivedSessions` | PASS | 参考实现无此用例 |
| `awaiting_answer` 复合条件 | `#awaitingAnswerIsComposite` | PASS | 参考实现无此用例 |
| 路径无 pending 时不算在等作答 | `#awaitingAnswerNeedsPathPending` | PASS | 参考实现无此用例 |
| `last_message` 截 240 | `#lastMessageTruncated` | PASS | 参考实现无此用例 |
| 会话超 20 截断 + `sessions_omitted` | `#sessionsTruncated` | PASS | 参考实现无此用例 |
| 路径不存在返回空列表 | `#sessionsForMissingPath` | PASS | |

### 2.5 hand-off 卡片（TC-NAV5）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 17 键 + `emoji` 恒空 | `#handoffHasSeventeenKeys` | PASS | 对应 `test_sessions_and_open_handoff` 的 handoff 断言 |
| `session`/`module` 为 `null` 时取默认值 | `#handoffDefaults` | PASS | 对应 `test_new_session_handoff_and_same_topic_guard` 的 `session_id == ""` |
| 导航层不写库 | `#navigationIsReadOnly` | PASS | 参考实现无此用例 |

本 change 新增用例：**26 个**（`NavigationTest`），全部 PASS。
全量 **504 个，0 失败**（478 → 504）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 纯函数专项 | `verify.ps1 -Scope unit` | Tests run: 417, Failures: 0 |
| 全量 | `verify.ps1 -Change 0014-mastery-nav` | BUILD SUCCESS，Tests run: 504, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0013 的 478 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_mastery_nav.py -q`（在 `../MasteryPath`） | **4 passed** |

### 跨语言数据等价实证

用真实 Python 代码跑同一组操作（临时探针，已删除），输出与 Java 断言逐条一致：

```
== topic_cards ==
keys: ['topics', 'total_topics']
ids: ['p1', 'p2'] / total_topics: 2
has topics_omitted: False / has query: False
card keys: [due_reviews, goal, learning, mastered, modules, name,
            not_started, objectives, path_id, sessions]   ← 10 键
has modules_omitted: False

== query semantics ==
query='p1' ids: []                    ← haystack 不含 path_id
query='statistics' ids: ['p1']
query='MACHINE' ids: ['p2']
query='  ' has query key: True value: '  '   ← 空白是真值
query='  ' ids: ['p1', 'p2']          ← 但不过滤

== find_topic ==
p1 name: Statistics / strip: Statistics
empty: None / missing: None

== resolve_module ==
'm1' -> m1 / 'statistics BASICS' -> m1 / '1' -> m1 / '1.' -> m1
'2' -> None / '' -> None / 'nope' -> None

== topic_sessions ==
keys: ['sessions', 'total_sessions'] / total_sessions: 1
row keys: [awaiting_answer, last_message, messages, running,
           session_id, title, updated_at]   ← 7 键
after archived: ['s1']                  ← 字符串 "true" 也被跳过

== handoff ==
count: 17
card: {due_reviews: 0, emoji: "", kind: "new", mastered: 0, module_id: "",
       module_name: "", objectives: 1, opening_message: "", path_id: "p1",
       path_name: "Statistics", reason: "", session_awaiting: false,
       session_id: "", session_messages: 0, session_running: false,
       session_title: "", session_updated_at: 0.0}
```

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_topics_lists_built_paths_and_filters` | `#listsBuiltTopics` + `#queryFiltersByHaystack` | 参考用例经 `MasteryTopicsTool` 断言；工具层属 `0017`，本 change 在服务层断言同一语义 |
| `test_sessions_and_open_handoff` | `#listsSessions` + `#handoffHasSeventeenKeys` | 同上；`module="1"` 的解析由 `#resolveModuleByOrder` 覆盖 |
| `test_open_rejects_session_from_another_topic` | 未覆盖 | 拒绝发生在工具层（`_topic` + 会话归属校验），属 `0017` |
| `test_new_session_handoff_and_same_topic_guard` | `#handoffDefaults` | 「同一课程拒绝」在工具层（`topic["path_id"] == ctx.path_id`），属 `0017` |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#haystackExcludesPathId` | 参考实现无「按 path_id 搜不到」用例 |
| `#queryKeyOnlyWhenTruthy` | 参考实现无 `query` 条件键用例 |
| `#modulesTruncated` / `#noModulesOmittedKeyWhenUnderLimit` / `#topicsTruncated` | 参考实现无截断与条件键用例 |
| `#findTopicEmptyMapIsNull` / `#findTopicStripsPathId` | 参考实现无 `find_topic` 独立用例 |
| `#resolveModuleByName` / `#resolveModuleByOrder` / `#resolveModuleNoMatch` | 参考实现无 `resolve_module` 独立用例 |
| `#skipsArchivedSessions` | 参考实现无 `archived` 过滤用例 |
| `#awaitingAnswerIsComposite` / `#awaitingAnswerNeedsPathPending` | 参考实现无 `awaiting_answer` 复合条件用例 |
| `#lastMessageTruncated` / `#sessionsTruncated` / `#sessionsForMissingPath` | 参考实现无截断与缺失路径用例 |
| `#navigationIsReadOnly` | 参考实现无「只读」断言 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 未实现工具层 4 个 `Mastery*Tool` 及其失败文案 | 一般 | 属 `0017`。参考实现 `nav_tools.py` 221 行中，工具类只做「参数解析 + 服务层调用 + `ToolResult` 包装」；本 change 覆盖了它们背后的全部数据构造 |
| D2 | `test_open_rejects_session_from_another_topic` 未覆盖 | 一般 | 拒绝在工具层（`_topic` 查不到课程 + 会话不属于该课程），属 `0017`。已登记 `verification-debt.md` |
| D3 | `test_new_session_handoff_and_same_topic_guard` 的「同一课程拒绝」未覆盖 | 一般 | 同上，属 `0017` |
| D4 | `casefold()` 与 `toLowerCase(Locale.ROOT)` 差异 | 低 | 与 `Grading.normalize` 同一处已登记（`migration-plan.md` §11）；影响 `ß` 等少数场景 |
| D5 | `int(ref.rstrip("."))` 的 Python 宽松解析 | 低 | Python 接受 `"1_0"`（=10）与 `" 1 "`；Java `parseInt` 只接受后者。实际输入是「第 N 课」，不会出现下划线。已在此登记 |
| D6 | `topicCards` 的 `modules` 用 `subList` 视图 | 无 | 返回的是 `ArrayList` 副本，调用方修改不影响聚合 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| `MasteryTopicsTool` / `MasterySessionsTool` / `MasteryOpenSessionTool` / `MasteryNewSessionTool` 的参数解析与失败文案 | `0015` | 需 `ToolContext` / `ToolResult` |
| 「打开别的课程的会话」被拒 | `0015` | 判定在工具层 |
| 「同一课程新建会话」被拒 | `0015` | 判定在工具层 |
| `GET /learning/topics` 等 REST 端点 | `0016` | 需 REST 层 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 504, Failures: 0` 来自
`artifacts/verify-0014-mastery-nav-20261104-194559.log`；参考实现对照见
`pytest tests/test_mastery_nav.py` 的 `4 passed`；跨语言数据等价由真实 Python 探针
逐条验证（输出见 §3）。
