# MasteryPath-Java Project Context

> 本文件是全局项目上下文（单一真实源之一）。任何 change 的产物都必须与本文件一致。
> 本项目的**行为合同**不在此处重写：它来自参考实现 `../MasteryPath/docs/`（已冻结的 ADR / INV / TC）。
> 本文件只规定**这次重写**的目标、边界、技术栈与工程标准。

## 1. 项目是什么

MasteryPath（精通之路）是一个「以知识地图为中心的掌握式学习智能体」：学习者与导师在 `outline` 模式共创模块/知识点（KP）地图，在 `study` 模式讲与考，在 `review` 模式只重测已掌握点；掌握与否由**引擎的门槛**判定（`memory/procedure` 量化 ≥0.9，`concept/design` 定性通过），并配合 SM-2 间隔复习。

本次工作是把该系统的**后端**从 Python 重写为 Java，行为与对外契约**保持不变**。
自 `0027-web-into-repo` 起，Next.js 前端源码也随本仓库 `web/` 管理（源码来自 `../MasteryPath/web`，行为不变）。

## 2. 边界

### In scope

- Python 包 `masterypath/` 的全部后端职责：领域模型、存储（SQLite + CAS）、掌握策略、模式门控、大纲生成/编辑、出题与判分、定性评估、题目修复、题库、SM-2 调度、Agent Loop 与工具层、REST/WS 接口、知识库（来源加载/索引/检索）、大纲质量评测、设置中心后端、Task Model。
- 与现有前端 `web/` 的接口兼容（REST 路径、WS 事件、JSON 字段名）。
- 与现有数据文件的兼容：Java 版可直接打开参考实现的 `data/learning.db`。
- 验收测试的 1:1 移植：参考实现 `tests/` 的 237 个用例是本次重写的判定标准。

### Out of scope

- **不重写前端**（`web/` 保持 Next.js 14 现状与交互契约，见 `docs/migration-plan.md` §1.3 的策略论证；源码已随 `0027-web-into-repo` 并入本仓库）。
- 不新增产品功能、不调整阈值/默认值、不引入新的领域实体。
- 不做多用户/鉴权/部署编排（与参考实现一致，单工作区单进程）。
- 不做 FSRS 迁移（`INV-31`：SM-2 与 FSRS 二选一，本项目继续 SM-2）。
- 不做二期字段（`scenario` 预设、语义嵌入评测替换），仅保留字段与接口位置。

## 3. 技术栈（锁定，变更需走 change）

| 项 | 选择 | 说明 |
| --- | --- | --- |
| JDK | 21（本机已装 Temurin 21.0.11） | 语言级别 21，启用 record / sealed / pattern matching |
| 构建 | Maven 3.9（提交 `mvnw`/`mvnw.cmd` wrapper） | 本机未装 Maven，必须用 wrapper |
| 框架 | Spring Boot 3.5.16（web / validation / actuator 按需） | 版本已锁定（Maven Central 实测最新 3.5.x）；WebSocket 用 Spring `WebSocketHandler` 或原生 `ServerHttpRequest` 升级 |
| 持久化 | SQLite（`org.xerial:sqlite-jdbc`）+ 手写 JDBC DAO | 表结构与参考实现 DDL **逐字一致**；path 聚合以 JSON 文本存储 + `version` 做 CAS |
| JSON | Jackson（`ObjectMapper`，`FAIL_ON_UNKNOWN_PROPERTIES=false`） | 对齐 Pydantic `extra="ignore"` |
| 测试 | JUnit 5 + AssertJ + `spring-boot-starter-test` | 纯函数层不启动 Spring 上下文 |
| 前端 | 保留 Next.js 14（不重写），源码在本仓库 `web/` | 契约由 `openspec/specs/agent-turn` 冻结 |
| LLM 适配 | `java.net.http.HttpClient` + SSE 解析 | 只实现 OpenAI 兼容协议；测试用 Fake 客户端 |
| 日志 | SLF4J + Logback | 验证日志落 `artifacts/` |

## 4. 工程标准

- **分层依赖方向**：`domain`（模型+纯策略）← `service` ← `adapter`（JDBC / HTTP / WS）。`domain` 不得依赖 Spring、Jackson 之外的任何框架类型，且**零 I/O、零网络**，可离线单测。
- **纯函数优先**：门槛判定、判分、SM-2、模式门控必须是纯函数（无 I/O、无时间隐式依赖，`now` 显式传入或可注入 Clock）。
- **不静默降级**：解析不了就返回「未判定」，不得猜（`INV-33`/`INV-46`）。
- **异常语义与参考实现一致**：`ConcurrentModificationError`→`CasConflictException`，`PreconditionError`→`PreconditionFailedException`。
- **命名**：Java 包名 `com.masterypath.*`；类名与 Python 模块一一对应（映射表见 `docs/migration-plan.md` §3），便于交叉审查。
- **禁止**：为通过测试而放宽断言、删除用例、改环境参数；禁止手写生成物；禁止在 `Build` 阶段修改 `openspec/specs/`。

## 5. 数据与隐私

- 测试数据一律**合成**，不得使用真实个人资料、真实 API Key。
- `data/settings.json` 可能含 provider key，**不入库**；仓库只提交示例结构。
- 学习者画像、题目、证据均为本地 SQLite 文件，不上传。

## 6. 规范资产约定

| 资产 | 位置 | 权威性 |
| --- | --- | --- |
| 行为合同（ADR/INV/TC） | `../MasteryPath/docs/`（只读引用） | 唯一真实源，本仓库不复制正文 |
| 基线规范（OpenSpec 形态） | `openspec/specs/<capability>/spec.md` | 实现阶段的唯一依据 |
| 增量规范 | `openspec/changes/<id>/specs/` | 仅当行为变化时存在 |
| 追踪矩阵 | `docs/traceability.md` | 场景 ↔ 规范 ↔ Java 测试 |
| 验证证据 | `docs/verify/<change-id>.md` | 由执行验证的机器回填 |

- 场景编号沿用参考实现的 `TC-*`（如 `TC-B4`），**不另起一套 T01 编号**，避免双编号漂移。
- 本次是**行为等价重写**：绝大多数 change 应写 `skip_specs: true`；只有确需澄清/补充合同（例如 DDL 兼容、Java 异常映射）时才写增量规范。

## 7. 命令

```powershell
# 编译
.\mvnw.cmd -q -DskipTests compile

# 单元测试
.\mvnw.cmd -q test

# 唯一验证入口（推荐，产出证据）
.\scripts\verify.ps1 --scope all --change <change-id>

# 本地起服务（后端）
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8001"
```

## 8. AI 协作纪律

见 `openspec/AGENTS.md`。三条最常被违反的：

1. 只做当前 change 授权的事，顺手优化另开 change。
2. 没跑过不算通过；`NOT RUN` 必须写进 `docs/verification-debt.md`。
3. 行为变化走增量规范；行为等价重写才允许 `skip_specs: true`。
