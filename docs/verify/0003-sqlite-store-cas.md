# 验证报告：0003-sqlite-store-cas

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0003-sqlite-store-cas` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 12:58:10（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16、sqlite-jdbc 3.54.1.0 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0003-sqlite-store-cas` |
| 日志 | `artifacts/verify-0003-sqlite-store-cas-20261104-125810.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Workspace Scoped Store | TC-B1 空库不建记录 | `LearningStoreTest#loadMissingReturnsNullAndCreatesNothing` | PASS | |
| Workspace Scoped Store | TC-B2 读写往返 | `LearningStoreTest#createThenLoad` | PASS | |
| Workspace Scoped Store | TC-B6 隔离 | `LearningStoreTest#workspaceIsolation` | PASS | |
| Compare And Set Mutation | TC-B3 版本自增 | `LearningStoreTest#mutateBumpsVersion` | PASS | 含 `updated_at` 增大 |
| Compare And Set Mutation | TC-B4 旧版本被拒 | `LearningStoreTest#staleExpectedVersionRejected` | PASS | 且不覆盖 |
| Compare And Set Mutation | TC-B5 回调异常回滚 | `LearningStoreTest#callbackExceptionRollsBackStateAndEvents` | PASS | |
| Compare And Set Mutation | TC-B9 可合并并发 | `LearningStoreConcurrencyTest#mergeableConcurrentWritesAllApply` | PASS | 6 线程，真实 SQLite |
| Compare And Set Mutation | TC-B10 前置失败不重试 | `LearningStoreConcurrencyTest#preconditionNotRetriedAndNoWrite` | PASS | 断言回调只执行 1 次 |
| Compare And Set Mutation | TC-B11 重试耗尽 | `LearningStoreConcurrencyTest#casExhaustionThrowsAfterConfiguredRetries` | PASS | 断言恰 3 次 |
| Compare And Set Mutation | TC-B12 不同路径并发 | `LearningStoreConcurrencyTest#concurrentDifferentPaths` | PASS | 无数据库忙错误 |
| Compare And Set Mutation | TC-B13 同路径并发 | `LearningStoreConcurrencyTest#concurrentSamePathNeverLosesUpdate` | PASS | 版本号 = 1 + 成功次数 |
| Compare And Set Mutation | TC-B14 写中读不阻塞 | `LearningStoreConcurrencyTest#readerOnSeparateConnectionNotBlockedDuringWrite` | PASS | 独立连接读旧快照 |
| Append Only Events | TC-B7 事件带 revision | `LearningStoreTest#eventCarriesPathAndRevision` | PASS | |
| Append Only Events | TC-B8 只追加 | `LearningStoreTest#eventsAppendOnly` | PASS | |
| Append Only Events | TC-B15 同事务 | `LearningStoreTest#callbackExceptionRollsBackStateAndEvents` | PASS | 状态与事件同时不落 |
| Append Only Events | TC-I4 事件补齐 | `LearningStoreTest#eventsSinceSkipsOlderRevisions` | PASS | |
| Workspace Scoped Store | TC-B16 一回合两路径 | `LearningStoreTest#sequentialTwoPaths` | PASS | |
| Workspace Scoped Store | TC-B17 跨 workspace 并发 | `LearningStoreConcurrencyTest#crossWorkspaceConcurrent` | PASS | |
| Workspace Scoped Store | TC-B14 WAL 启用 | `LearningStoreTest#walEnabled` | PASS | `PRAGMA journal_mode = wal` |
| —（数据等价） | 读参考实现库文件 | `ReferenceWorkspaceCompatibilityTest#readsPathWrittenByReferenceImplementation` | PASS | fixture 由 Python 创建 |
| —（数据等价） | 读参考实现事件 | `ReferenceWorkspaceCompatibilityTest#readsEventsWrittenByReferenceImplementation` | PASS | |
| —（数据等价） | 在旧库上继续写 | `ReferenceWorkspaceCompatibilityTest#continuesMutatingReferenceDatabase` | PASS | 版本 2 → 3 |
| —（DDL 一致） | 六张表列名逐项比对 | `ReferenceWorkspaceCompatibilityTest#tableColumnsMatchReference` | PASS | |

本 change 新增用例：**23 个，全部 PASS**。全量 **50 个，0 失败**。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 存储专项 | `verify.ps1 -Scope storage` | Tests run: 23, Failures: 0, Errors: 0 |
| 全量 | `verify.ps1 -Change 0003-sqlite-store-cas` | BUILD SUCCESS，Tests run: 50, Failures: 0, Errors: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0001` 3 项、`0002` 21 项仍 PASS） |
| 参考实现对照 | Python 侧真实建库 → Java 打开 | 已执行，读取与续写均成功 |

### 跨语言兼容验证方法

1. 在 `../MasteryPath` 用 `.venv` 的 `LearningStore` 真实创建 `learning.db`（建路径 + 一次 mutate + 一条事件）；
2. 原样复制到 `src/test/resources/fixtures/reference-workspace/learning.db`，不做任何手工修改；
3. Java 侧用 `new LearningStore(dir)` 直接打开该库，读取聚合、读取事件、继续 mutate（版本 2 → 3）；
4. 用 `PRAGMA table_info` 逐表比对列名，证明 DDL 逐字一致。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `PRAGMA journal_mode=WAL` 不能在事务内执行，初版在 `autoCommit=false` 后调用，23 个用例全部失败 | 严重（阻断构建） | 改为初始化阶段保持自动提交，建表与迁移完成后再进入事务模式 |
| D2 | sqlite-jdbc 在 `autoCommit=false` 时执行首条语句会自动开事务，再发 SQL `BEGIN IMMEDIATE` 变成嵌套事务 | 严重（阻断构建） | 弃用 SQL `BEGIN`，统一用 JDBC 事务控制；SQLite 写事务本身排他，语义等价 |
| D3 | `endTransaction(false)` 的失败路径又调用 `rollbackQuietly()`，形成无限递归 → `StackOverflowError` | 严重（阻断构建） | `endTransaction` 内部不再调用回滚辅助方法；`finally` 中恢复自动提交失败仅忽略 |
| D4 | `setSessionPreferences` / `setSessionPath` 缺 `beginImmediate()`，报 `database in auto-commit mode` | 严重（单用例失败） | 补上事务开启；两者现均在事务内执行 |
| D5 | 初版 TC-B14 用例用同一个 `LearningStore` 读，无法证明 WAL 的价值（单连接本就互斥） | 一般 | 改为独立 JDBC 连接读取，断言「不被阻塞 + 读到旧快照」 |
| D6 | `JsonSupport` 的 `setVisibility` 仍触发 deprecation 告警 | 一般 | 不影响行为；已在 `0002` 记录，留待统一清理 |
| D7 | 编译日志中文显示为 `????` | 一般 | 仅影响可读性；不影响构建结果 |

> D1–D4 均为**构建期被测试主动发现并修正**的实现缺陷，不是放宽断言。D5 是用例自身设计缺陷的修正——原写法无法证伪。

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 50, Failures: 0` 来自 `artifacts/verify-0003-sqlite-store-cas-20261104-125810.log`；旧库 fixture 在 `src/test/resources/fixtures/reference-workspace/learning.db`（Python 原样产出）。
