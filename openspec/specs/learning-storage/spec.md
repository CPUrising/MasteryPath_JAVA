# Learning Storage Capability Spec

## Purpose

定义路径聚合的持久化与并发语义：每个 workspace 一个 SQLite 库，路径聚合以 JSON 文档加版本号存储，所有写操作通过单一入口做乐观并发控制，并与领域事件同事务提交。

## Requirements

### Requirement: Workspace Scoped Store

系统 SHALL 为每个 workspace 使用独立的 SQLite 数据库文件，跨 workspace 的路径 MUST 互不可见且 MUST NOT 共享锁。

#### Scenario: TC-B1 missing path returns empty

- **GIVEN** 空库
- **WHEN** 读取一个不存在的 `path_id`
- **THEN** 系统 MUST 返回空且 MUST NOT 创建记录

#### Scenario: TC-B2 round trip

- **GIVEN** 已写入 `p1`
- **WHEN** 再次读取 `p1`
- **THEN** 内容 MUST 与写入时相等

#### Scenario: TC-B6 TC-B17 workspace isolation

- **GIVEN** 两个 workspace 使用同名 `path_id`
- **WHEN** 分别写入并读取
- **THEN** 彼此 MUST 互不可见，并发写 MUST 互不阻塞

### Requirement: Compare And Set Mutation

系统 SHALL 通过 `mutate(pathId, fn)` 执行所有写入，并以 `version` 做乐观并发校验；冲突 SHALL 有界重试（≤3 次，带抖动退避），前置条件失败 MUST NOT 重试，重试耗尽 SHALL 抛出并发修改异常。写入 MUST NOT 静默覆盖他人提交。

#### Scenario: TC-B3 successful mutation bumps version

- **GIVEN** 路径 `version=1`
- **WHEN** 一次 `mutate` 成功
- **THEN** `version` MUST 变为 `2` 且 `updated_at` MUST 增大

#### Scenario: TC-B4 stale version rejected

- **GIVEN** 会话 S1 读到 `version=1`，S2 已提交 `version=2`
- **WHEN** S1 用旧版本提交
- **THEN** 系统 MUST 拒绝且 MUST NOT 覆盖 S2 的内容

#### Scenario: TC-B5 callback exception rolls back

- **GIVEN** `mutate` 的回调抛出异常
- **WHEN** 执行
- **THEN** 系统 MUST NOT 落库任何状态，也 MUST NOT 写入事件

#### Scenario: TC-B9 TC-B11 retry and exhaustion

- **GIVEN** 两个可合并的并发写 / 持续冲突达上限
- **WHEN** 执行
- **THEN** 前者 MUST 重试后成功且两处修改都在，后者 MUST 抛并发修改异常

#### Scenario: TC-B10 precondition never retried

- **GIVEN** 对已消失的待答题执行判分
- **WHEN** 执行
- **THEN** 系统 MUST 抛前置条件异常，MUST NOT 重试，MUST NOT 落库

#### Scenario: TC-B12 TC-B13 TC-B14 concurrent access

- **GIVEN** 同一 workspace 两线程并发写不同路径 / 同一路径
- **WHEN** 执行
- **THEN** 前者 MUST 都成功且不冒出数据库忙错误，后者 MUST 一个成功另一个冲突
- **AND** 写入进行中的读 MUST NOT 被阻塞

### Requirement: Append Only Events

系统 SHALL 在写事务内追加领域事件，事件的 `id` 与 `revision` MUST 单调递增；历史事件 MUST NOT 被修改。事件 MUST 只在提交成功时发出。

#### Scenario: TC-B7 event carries post commit revision

- **GIVEN** 一次成功 `mutate`
- **WHEN** 查询事件表
- **THEN** 新增事件 MUST 含 `path_id` 与提交后的 `revision`

#### Scenario: TC-B8 events are append only

- **GIVEN** 已有事件
- **WHEN** 后续读取
- **THEN** 历史事件 MUST 不变

#### Scenario: TC-B15 state and events commit together

- **GIVEN** `mutate` 内抛异常
- **WHEN** 执行
- **THEN** 状态与事件 MUST 同时不落库

#### Scenario: TC-I4 TC-I5 TC-I6 event stream recovery

- **GIVEN** 事件流存在历史
- **WHEN** 按 `since` 拉取
- **THEN** 系统 MUST 返回该 revision 之后的全部事件，且能重建字段级差异
- **AND** 一次失败提交 MUST NOT 产生事件，随后成功提交的 `revision` MUST 连续
