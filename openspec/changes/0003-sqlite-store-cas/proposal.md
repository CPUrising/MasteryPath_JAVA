# Proposal: SQLite 存储与 CAS 写入

## Why

`0002` 只定义了实体，没有任何持久化。参考实现的全部业务正确性都建立在一个约定上：
**所有写入都经 `mutate(pathId, fn)`，以 `version` 做乐观并发校验，并与领域事件同事务提交**
（ADR-006 / ADR-012 / `INV-11`、`INV-50`、`INV-51`、`INV-52`）。

这一层如果不等价，后续所有服务层测试都会建立在错误的地基上——例如「判分幂等」要求同一事务内
写 attempt + evidence + interaction，一旦事务边界不同，`INV-22/45` 就无法保证。

## What Changes

- 新增 `LearningStore`：workspace 级 SQLite 文件（`<base>/learning.db`），WAL + `busy_timeout`。
- 逐字复刻 6 张表的 DDL：`paths` / `events` / `interactions` / `question_bank` / `sessions` / `messages`。
- 实现 `load` / `listPaths` / `create` / `mutate` / `eventsSince`。
- 实现 `mutate` 的三条语义：
  1. `BEGIN IMMEDIATE` 事务内 读 → 回调 → `UPDATE ... WHERE version = old`；影响行数为 0 即 CAS 冲突。
  2. 冲突有界重试（≤3 次，抖动退避）；耗尽抛 `CasConflictException`。
  3. 前置条件失败抛 `PreconditionFailedException`，**永不重试**。
- 新增 `Transaction`：回调内可改聚合、`emit` 事件、在同一事务内读写 interaction 与题库。
- 实现会话与消息的基础读写（`upsertSession` / `getSession` / `listSessions` / `appendMessage` / `listMessages` / `deleteSession`）。

## Capabilities

### New Capabilities

无新增能力；本变更实现 `specs/learning-storage` 的全部需求。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/storage/**`（新增 5 个类）、
  `src/test/java/com/masterypath/storage/**`（新增 2 个测试类）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope）：
  - 不实现掌握门槛、判分、调度（`0004`/`0008`/`0010`）。
  - 不实现 `settings.json`、知识库元数据（`0016`/`0017`）。
  - 不实现 `sessions`/`messages` 之外的聊天存储细节（`0013` 需要时再补）。
  - 不做表结构变更：DDL 必须与参考实现逐字一致，否则旧库无法直接打开。
