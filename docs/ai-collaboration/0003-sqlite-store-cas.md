# AI 协作留痕：0003-sqlite-store-cas

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/storage/**`（新增 6 个类）、
  `src/test/java/com/masterypath/storage/**`（3 个测试类）、
  `src/test/resources/fixtures/reference-workspace/learning.db`（Python 真实产出）。
- 明确不做的事：不实现掌握门槛/判分/调度、不实现 `settings.json` 与知识库元数据、
  不改表结构（DDL 必须逐字一致）。
- 出处：`openspec/changes/0003-sqlite-store-cas/proposal.md` §Impact。

## 2. AI 产出

- 新增文件：
  - `Schema`（DDL 常量，含 6 张表与 5 个索引）
  - `Transaction`（`emit` / `require` / 题目与题库同连接读写）
  - `LearningStore`（连接管理、CAS mutate、事件、会话与消息）
  - `CasConflictException`、`PreconditionFailedException`
  - 测试 3 个：`LearningStoreTest`（16）、`LearningStoreConcurrencyTest`（7）、
    `ReferenceWorkspaceCompatibilityTest`（4）
- 关键决策：
  - 单连接 + 进程内互斥（与参考实现一致），**不用连接池**——连接池会让 `TC-B13` 的
    「同 path 并发必有一个冲突」失效。
  - 事务控制用 JDBC（`setAutoCommit`），不用 SQL `BEGIN IMMEDIATE`。
  - 重试逻辑抽为包级 `retryCas(Supplier)`，使「重试次数与耗尽行为」可被直接测试（`TC-B11`），
    无需 mock 内部方法。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正（全部在提交前完成，且均被测试主动发现）：
  1. `PRAGMA journal_mode=WAL` 在 `autoCommit=false` 后执行 → 23 个用例全挂。改为初始化阶段自动提交。
  2. 在 `autoCommit=false` 下再发 SQL `BEGIN IMMEDIATE` → 嵌套事务报错。改为 JDBC 事务控制。
  3. `endTransaction(false)` 失败路径调用 `rollbackQuietly()` → 无限递归 `StackOverflowError`。去掉递归。
  4. `setSessionPreferences` / `setSessionPath` 漏开事务 → `database in auto-commit mode`。补 `beginImmediate()`。
  5. `@TempDir` 同时用于字段与方法参数 → 冲突。统一用字段。
  6. `tearDown` 在 setUp 失败时 NPE → 加判空。
  7. TC-B14 初版用同一 store 读取，无法证伪 WAL 价值 → 改用独立 JDBC 连接。

## 4. 回归结果

- 用例数 / 失败数：存储专项 23 / 0；全量 50 / 0。
- 证据链接：[`docs/verify/0003-sqlite-store-cas.md`](../verify/0003-sqlite-store-cas.md)
- 日志：`artifacts/verify-0003-sqlite-store-cas-20261104-125810.log`
- 跨语言证据：`fixtures/reference-workspace/learning.db` 由 Python 的 `LearningStore` 真实创建，
  Java 侧读取、续写、DDL 列名比对均通过。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否用连接池 | **不用** | 连接池会让「同 path 并发写必有一方冲突」失效，破坏 `TC-B13` |
| 事务开启方式 | JDBC `setAutoCommit` | sqlite-jdbc 在手动模式下自动开事务，SQL `BEGIN` 会嵌套 |
| 重试次数与退避 | 3 次，`1ms*(n+1) + rand(0..2ms)` | 与参考实现数值一致，保证 `TC-B9` 的时序假设成立 |
| 前置失败是否重试 | **不重试** | `INV-11c` / `ADR-012`；用「回调执行次数 == 1」断言 |
| DDL 是否可微调 | **不可** | 逐字一致才能直接打开旧库；由 `tableColumnsMatchReference` 固化 |
| TC-B14 的验证方式 | 独立连接读 | 同连接读本就互斥，无法证明 WAL 的作用（原写法无法证伪） |
