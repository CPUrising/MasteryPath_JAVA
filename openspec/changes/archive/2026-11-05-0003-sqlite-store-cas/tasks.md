# Tasks

## 1. 存储骨架

- [x] 1.1 `Schema`：6 张表 DDL 与索引，逐字对齐参考实现
- [x] 1.2 `LearningStore` 构造：建目录、开连接、`PRAGMA journal_mode=WAL`、`busy_timeout=5000`、建表
- [x] 1.3 `LearningStore` 关闭与 `migrate()`（`sessions.preferences` 补列）

## 2. 读路径

- [x] 2.1 `load(pathId)`：不存在返回 `null` 且不建记录（TC-B1）
- [x] 2.2 `listPaths()`：按 `updated_at DESC`
- [x] 2.3 `eventsSince(pathId, revision)`：`revision > ?` 按 `id` 升序

## 3. 写路径（CAS）

- [x] 3.1 `Transaction`：`progress` / `emit` / `require` / `getInteraction` / `getOpenInteraction` / `saveInteraction` / `saveBankEntry`
- [x] 3.2 `create(progress)`：已存在则抛 `PreconditionFailedException`；`version = max(1, version)`
- [x] 3.3 `mutate(pathId, fn, expectedVersion, createIfMissing)`：事务内读 → 回调 → `UPDATE ... WHERE version=?`，`rowcount==0` 即冲突
- [x] 3.4 冲突有界重试（≤3，抖动退避）；耗尽抛 `CasConflictException`；`PreconditionFailedException` 不重试
- [x] 3.5 事件在提交前写入，与状态同事务（TC-B15）

## 4. 会话与消息

- [x] 4.1 `upsertSession` / `getSession` / `listSessions`（含 `preferences` JSON 合并读取）
- [x] 4.2 `appendMessage` / `listMessages` / `deleteSession`（级联删消息）
- [x] 4.3 `setSessionPreferences`（合并写入）/ `setSessionPath`

## 5. 测试

- [x] 5.1 `LearningStoreTest`：TC-B1…B8、B14、B15、B16（16 项）
- [x] 5.2 `LearningStoreConcurrencyTest`：TC-B9…B13、B17（7 项，真实多线程 + 真实 SQLite，无 mock）
- [x] 5.3 `ReferenceWorkspaceCompatibilityTest`：打开 Python 真实建出的库（4 项，含 DDL 列名比对）

## 6. 验证

- [x] 6.1 运行 `.\scripts\verify.ps1 -Scope storage --change 0003-sqlite-store-cas`
- [x] 6.2 运行全量 `.\scripts\verify.ps1 --change 0003-sqlite-store-cas`
- [x] 6.3 回填 `docs/verify/0003-sqlite-store-cas.md`
- [x] 6.4 追加 `docs/regression-log.md`；更新 `docs/traceability.md` 与 `README.md`
