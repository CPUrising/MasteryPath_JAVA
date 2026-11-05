# Design: SQLite 存储与 CAS 写入

## Context

参考实现 `learning/storage.py`（669 行）用 Python 标准库 `sqlite3` + `threading.RLock`：

- 一个 `LearningStore` = 一个 workspace = 一个 `learning.db` 文件（`INV-52`）。
- 单连接 + `check_same_thread=False` + 全局 `RLock`，所以进程内写入是**串行**的。
- `mutate` 在 `BEGIN IMMEDIATE` 里做 CAS；`UPDATE ... WHERE version=?` 的 `rowcount==0` 即冲突。
- 冲突重试 ≤3 次并抖动退避；`PreconditionError` 直接抛出、不重试（ADR-012）。
- `WAL` + `busy_timeout=5000` 让跨进程写等待而不是抛 `SQLITE_BUSY`。

## Goals / Non-Goals

**Goals**
1. 表结构逐字一致，能直接打开参考实现的 `data/learning.db`。
2. `mutate` 语义等价：CAS、有界重试、前置失败不重试、状态与事件同事务。
3. TC-B1…B17 全部有对应 Java 用例，且**用真实 SQLite + 真实多线程**。

**Non-Goals**
1. 不引入连接池（参考实现是单连接 + 锁；连接池会改变并发语义，属行为变化）。
2. 不引入 JPA/ORM（聚合是 JSON 文档 + 版本列，ORM 反而绕）。
3. 不做 schema 版本迁移框架（参考实现只有一处 `_migrate()` 加 `preferences` 列，照抄即可）。

## Decisions

1. **单连接 + `synchronized` 锁，而非连接池**
   - 选择：`LearningStore` 持有一个 `Connection`，所有方法 `synchronized`。
   - 理由：与参考实现完全一致。连接池会让「两个线程并发 mutate 同一 path」的行为从
     「一个成功、一个 CAS 冲突」变成「两个都可能成功」，直接破坏 `TC-B13`。
   - 放弃的方案：HikariCP（性能更好，但改变语义）。

2. **`mutate` 的 Java 形态：`Function<Transaction, T>`**
   - 选择：`<T> T mutate(String pathId, Function<Transaction, T> fn)`。
   - 理由：回调只能通过 `Transaction` 改聚合与发事件，无法绕过事务边界；
     `Function` 允许返回结果（参考实现也是返回 `_T`）。
   - 放弃的方案：`Consumer<Transaction>`（丢掉了「mutate 返回结果」的能力，服务层要额外读一次）。

3. **异常映射**
   - `ConcurrentModificationError` → `CasConflictException`（`RuntimeException`）。
   - `PreconditionError` → `PreconditionFailedException`（`RuntimeException`）。
   - 理由：命名更符合 Java 惯例，且语义一一对应；两者都是非受检，避免污染所有方法签名。

4. **重试与退避**
   - 选择：最多 3 次；每次 `sleep(1ms * attempt + random(0..2ms))`。
   - 理由：与参考实现的 `0.001 * (attempt+1) + random()*0.002` 数值一致，行为可对照。
   - 放弃的方案：指数退避（数值不同，会让 `TC-B9` 的时序假设失效）。

5. **JDBC 层用原生 SQL + 手写行映射**
   - 选择：`PreparedStatement` + `ResultSet` 手写映射。
   - 理由：6 张表、10 个查询，手写比引入 MyBatis/JPA 更短更可控；也便于逐字对齐 SQL。
   - 放弃的方案：Spring `JdbcTemplate`（会引入 Spring 依赖到 storage 层；虽然可接受，
     但 `synchronized` + 单连接的语义用原生 JDBC 表达更直接）。

6. **时间戳用 `REAL`（epoch 秒）**
   - 选择：与参考实现一致，Java 侧用 `double`。
   - 理由：旧库已有 `REAL` 值；改成 `INTEGER` 毫秒会破坏互读。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 单连接 + 全局锁导致吞吐低 | 性能 | 与参考实现一致；本项目单用户本地使用，可接受 |
| SQLite JDBC 默认不启用 WAL / busy_timeout | 并发用例失败 | 初始化时显式 `PRAGMA journal_mode=WAL` 与 `busy_timeout=5000`，并有 `TC-B14` 断言 |
| `synchronized` 与 JDBC 自动提交混用导致事务边界错乱 | 数据损坏 | 统一 `setAutoCommit(false)` + 显式 `commit/rollback`；禁止在 `mutate` 内调用自动提交语句 |
| 多线程用例在慢机器上偶发失败 | 假阴性 | 用例只断言最终状态与异常类型，不断言具体时序；重试次数固定为 3 |

## Migration Plan

1. `Schema`（DDL 常量）+ `LearningStore` 骨架（连接、PRAGMA、建表）。
2. `Transaction`（emit / require / interaction / bank）。
3. `mutate` 的 CAS + 重试。
4. 会话与消息读写。
5. 测试：`LearningStoreTest`（TC-B1…B8、B15、B16、B14）+ `LearningStoreConcurrencyTest`（TC-B9…B13、B17）。
6. 跑 `verify.ps1`，回填报告。

## Open Questions

- 无。DDL 与语义以 `../MasteryPath/masterypath/learning/storage.py` 为唯一依据。
