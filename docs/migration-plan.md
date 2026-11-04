# MasteryPath Java 重写 · 迁移计划

> 依据：`AI协作严谨工作流规范-SDD-OpenSpec.md`（下称「规范」）。
> 状态：**已批准（2026-11-04，人类逐项确认 §9 的 Q1–Q4）**。可进入 Build 阶段。
> 合同来源：`../MasteryPath/docs/`（ADR-001…023、INV-01…60、TC-*），本仓库不复制其正文。

---

## 1. 范围决策与论证

### 1.1 结论

| 决策项 | 选择 | 一句话理由 |
| --- | --- | --- |
| 重写范围 | **仅后端**（`masterypath/` → Java） | 行为合同全在后端，前端只是契约消费者；换语言不换契约，前端零改动即可复用 |
| 技术栈 | Spring Boot 3.5.x + Java 21 + **Maven Wrapper** | Web/WS/JSON/测试开箱即用；本机无 Maven，用 wrapper 锁定版本、保证可复现（见 §1.5） |
| 持久化 | SQLite + 手写 JDBC，聚合存 JSON + `version` CAS | 与参考实现 DDL 逐字一致，可直接打开旧库做数据兼容回归 |
| 验收标准 | 参考实现 237 个 Python 用例的等价移植 | 「重写」的唯一可验证定义是「同一输入得到同一行为」 |

### 1.2 为什么不是「后端 + 前端一起重写」

| 维度 | 仅后端 | 前后端一起重写 |
| --- | --- | --- |
| 判定依据 | 已有 237 个用例 + 60 条不变量，可直接判定对错 | 前端目前只有 `tsc --noEmit` + `next build`，**没有行为级验收用例**，重写后无法判定对错 |
| 风险 | 低：错误会被测试抓住 | 高：UI 行为漂移无证据可查，等于「氛围编程」 |
| 契约稳定性 | 前端作为**契约消费者**反向验证后端（最有力的验收） | 契约两边同时变，谁对谁错无从判断 |
| 工作量 | 后端约 7.5k 行 Python 的等价物 | 后端 + 约 40 个 React 组件 + i18n + 设计令牌 |

**因此：前端保留是「用已有资产做验收」的策略，不是偷懒。** 若将来确实要换前端，应作为**独立 change**（如 `00xx-web-rewrite`），在前端补齐行为级验收用例（Playwright/组件测试）后再做——否则违反规范第 3.2 节「不得伪造证据」。

### 1.3 如果你确实想重写前端

按规范必须满足的前置条件（现在**不满足**）：

1. 前端先建立行为级验收用例（页面级 E2E 或组件测试），并有基线证据；
2. 前端重写单独成 change，且 `.openspec.yaml` 写明 `skip_specs: false`（因为会改变交互合同）；
3. 与后端 change 串行，不并行——避免「两边都没验证」的窗口。

建议：先完成后端（G0–G7），前端重写作为 **G8 可选阶段**再评估。届时后端已稳定，前端重写有确定的契约可依。

### 1.4 明确不做（Out of scope）

- 不改阈值、默认值、枚举值、事件名、REST 路径、WS 事件名、JSON 字段名。
- 不引入新领域实体、不引入鉴权/多租户、不做 FSRS 迁移、不做 `scenario` 二期字段。
- 不修参考实现里的已知小问题（如 `session_meta` 晚于 `turn_end`）——若确实要修，单独开 change 并写增量规范。

### 1.5 Maven / Maven Wrapper / Gradle 的取舍（本机实测）

**关键澄清：Wrapper 不是 Maven 的替代品，而是把 Maven 版本固定在项目里。**

| | Maven（本体） | Maven Wrapper（`mvnw.cmd`） | Gradle |
| --- | --- | --- | --- |
| 是什么 | 构建工具，需单独安装并配 PATH | 项目自带的引导脚本，首次运行自动下载**指定版本**的 Maven | 另一种构建工具（也有 `gradlew`） |
| 需要预装吗 | 需要 | **不需要**，有 JDK 即可 | 不需要 |
| 版本一致性 | 各机器可能不同 | 由 `.mvn/wrapper/maven-wrapper.properties` 固定 | 同 wrapper |
| 首次构建代价 | — | 下载 Maven 发行包（约 9MB） | 下载 Gradle 发行包（约 130MB） |

`.\mvnw.cmd test` 的真实行为：检查本地是否有指定版本的 Maven → 没有则下载 → 用它执行 `test`。**「本机没装 Maven」不是障碍，恰恰是使用 wrapper 的理由。**

本机实测结论（2026-11-04）：

| 检查项 | 结果 | 对计划的影响 |
| --- | --- | --- |
| `java -version` | Temurin 21.0.11+9-LTS | ✅ 满足 |
| `mvn -v` / `gradle -v` | 均不存在 | 必须用 wrapper |
| `~/.m2/wrapper/dists/` | 已有 `apache-maven-3.8.6` | ✅ wrapper 机制在本机跑通过，可作离线兜底 |
| `~/.m2/repository` | 有 Spring Boot 2.7.9 / 4.0.x，**无 3.5.x、无 sqlite-jdbc** | 首次构建需联网 |
| Maven Central 可达性 | `repo.maven.apache.org` 返回 200 | ✅ 可下载 |
| Spring Boot 3.5.x 最新版 | 3.5.16（`maven-metadata.xml`） | 锁定 3.5.16 |

**选 Maven 而非 Gradle 的理由**：Spring Boot 官方文档与绝大多数示例以 Maven 为主（排错命中率高）；本项目单模块、无自定义构建逻辑，Gradle 的增量与脚本灵活性用不上；`pom.xml` 的声明式依赖清单更利于「依赖变更可审查」（规范要求新增依赖需 ADR）。

---

## 2. 「行为等价」的可验证定义

三个维度同时满足才算等价，缺一即视为未完成：

| 维度 | 判定方式 | 落在哪 |
| --- | --- | --- |
| **纯逻辑等价** | 同一输入 → 同一输出（门槛、判分、SM-2、门控、覆盖报告） | `domain` 层单测，对照 Python 同名用例 |
| **数据等价** | Java 版能读参考实现的 `data/learning.db`，写出后 Python 版仍能读 | `0003` 与 `0019` 的双向读取用例 |
| **协议等价** | REST 响应字段、WS 事件序列与载荷键名一致 | `0014`/`0015` 的契约测试 + 前端联调 |

**禁止的做法**：以「代码看起来一样」作为等价证据；以「我读过 Python 源码」作为通过依据。

---

## 3. 架构映射表（Python → Java）

包根 `com.masterypath`。类名与 Python 模块一一对应，便于交叉审查。

### 3.1 领域层 `domain`（零 I/O、零 Spring）

| Python | Java | 说明 |
| --- | --- | --- |
| `learning/models.py` | `domain.model.*` | 枚举 + record/class 实体；Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` 对齐 `extra="ignore"` |
| `learning/policy.py` | `domain.policy.MasteryPolicy` | 门槛、`nextObjective`、`mapSummary`；`Clock` 注入 |
| `learning/grading.py` | `domain.grading.Grader` | choice/short/open 三套规则，纯函数 |
| `learning/mastery.py` | `domain.policy.MasteryScore` | 近期加权掌握度 |
| `learning/scheduler.py` | `domain.schedule.Sm2Scheduler` | SM-2 间隔序列与 EF |
| `learning/question_card.py` | `domain.projection.QuestionCardProjector` | 公共投影，剔除答案 |
| `capabilities/mastery/mode.py` | `domain.mode.ModePolicy` | 静态分类表 + 守卫 + `admissionError` |
| `capabilities/mastery/behavior.py` | `domain.mode.BehaviorBudget` | 预算常量与判定 |

### 3.2 存储层 `storage`

| Python | Java | 说明 |
| --- | --- | --- |
| `learning/storage.py`（669 行） | `storage.LearningStore` + `storage.JdbcDao` | DDL 逐字一致；`load` / `mutate`（CAS 重试 ≤3 + 抖动）/ `events(since)` |
| `learning/pending.py` | `domain.model.PendingQuestion`（静态工厂） | 旧式选项字符串解析 |
| `chat/store.py` | `storage.ChatStore` | 会话/消息表 |
| `settings/store.py` | `storage.SettingsStore` | `settings.json` 读写 |
| `knowledge/store.py` | `storage.KnowledgeStore` | 知识库元数据 + 目录布局 |

### 3.3 服务层 `service`

| Python | Java | 说明 |
| --- | --- | --- |
| `learning/service.py`（1105 行） | `service.LearningService` + 按领域拆分：`OutlineService` / `QuizService` / `ReviewService` / `RepairService` / `NavigationService` | 拆分仅为可读性，**事务边界与参考实现一致**：一次工具调用 = 一次 `mutate` |
| `learning/outline.py`（491 行） | `service.outline.OutlineBuilder` / `OutlineReviser` / `GapAnalyzer` / `CoverageCalculator` | 纯计算与校验放 `domain`，落库在 `service` |
| `learning/navigation.py` | `service.NavigationService` | path 绑定、scratch path、hand-off |
| `chat/service.py` | `service.ChatService` | 普通聊天回合 |
| `knowledge/service.py` | `service.KnowledgeService` | 索引、检索、进度 |
| `knowledge/loader.py` | `service.source.FolderScanner` | 文件夹扫描为来源 |
| `knowledge/indexer.py` | `service.knowledge.VectorIndexer` | 嵌入 + 检索（二期可换实现，接口不变） |

### 3.4 适配层 `adapter`

| Python | Java | 说明 |
| --- | --- | --- |
| `agents/llm.py`（357 行） | `adapter.llm.LlmClient` 接口 + `OpenAiCompatibleClient` + `FakeLlmClient` | `java.net.http.HttpClient`；SSE 解析；tool_call 拼装；错误映射 |
| `agents/loop.py` | `adapter.loop.AgentLoop` | 装配 schema、多轮工具调用、预算、`end_turn` |
| `agents/turn_runner.py` | `adapter.turn.TurnRunner` | 会话/模式/路径绑定、事件出口、持久化 |
| `agents/model_resolution.py` | `adapter.llm.ModelResolver` | 从设置解析模型 |
| `agents/task_model.py` | `adapter.llm.TaskModel` | 建议下一问 + 确定性回退 |
| `api/app.py`（559 行） | `adapter.web.LearningController` / `ChatController` / `KnowledgeController` / `SettingsController` / `WsTurnHandler` | 路径与字段名不变 |
| `api/chat_routes.py` | `adapter.web.ChatController` | SSE 流 |
| `api/knowledge_routes.py` | `adapter.web.KnowledgeController` | 上传/索引/进度 |
| `capabilities/mastery/tools.py`（1095 行） | `adapter.tool.MasteryTools`（按工具拆多个类）+ `ToolRegistry` | 每个工具一个类，schema 集中在 `ToolSchemas` |
| `capabilities/mastery/nav_tools.py` | `adapter.tool.NavTools` | 只读导航 + hand-off 元数据 |
| `eval/outline_eval.py` | `adapter.eval.OutlineEval` + `cli.OutlineEvalCli` | 命令行入口等价 `python -m masterypath.eval` |

---

## 4. 阶段与 Gate

Gate 定义沿用规范第 13 节，但按「后端重写」重新映射语义（**已获人类批准，见 §9 Q1**）：

| Gate | 语义（本项目） | 准入 | 通过标准 |
| --- | --- | --- | --- |
| **G0** | 工程骨架可用 | 计划批准 | `mvnw test` 能跑通冒烟用例；`verify.ps1` 能产出日志 |
| **G1** | 规范与纯逻辑冻结 | G0 | 纯函数层（domain）全部用例通过；追踪矩阵中 domain/storage/mode 三组无「未实现」 |
| **G2** | 核心业务闭环 | G1 | 建路径→建大纲→出题→判分→掌握→排期 全链路单测通过 |
| **G3** | 大纲与质量 | G2 | build/revise/缺口/覆盖报告用例通过；`coverage` 可读 |
| **G4** | 回合与工具 | G3 | Agent Loop + 工具层用例通过；工具预算生效；离线可跑 |
| **G5** | 协议兼容 | G4 | REST 全部端点契约测试通过；WS 事件序列与参考实现一致 |
| **G6** | 知识库与设置 | G5 | 来源扫描/检索/设置/建议下一问用例通过 |
| **G7** | 验收 | G6 | 全量用例通过；旧库双向可读；前端联调通过；全部 change 归档 |

---

## 5. Change 清单（Plan 产物，按依赖排序）

> 每个 change ≤ 8 个原子任务；编号单调递增；`skip_specs: true` 表示行为等价重写（不产生增量规范）。
> 「验收」列即该 change 的 DoD，逐条对应 `docs/traceability.md` 的行。

| # | change id | 内容 | 主要文件 | 验收 | 依赖 |
| --- | --- | --- | --- | --- | --- |
| 1 | `0001-java-skeleton` | Maven Wrapper + Spring Boot 3.5.16 骨架 + 包分层 + `verify.ps1` + 冒烟测试 | `pom.xml`、`mvnw*`、`src/main/java/com/masterypath/App.java`、`scripts/verify.ps1` | 冒烟用例 PASS；日志落 `artifacts/` | — |
| 2 | `0002-domain-model` | 全部枚举与实体 + JSON 映射 + 历史类型映射 | `domain/model/*` | TC-A1…A6 | 0001 |
| 3 | `0003-sqlite-store-cas` | DDL、`load`/`mutate`（CAS ≤3 + 抖动）、事件表、并发语义 | `storage/*` | TC-B1…B17 | 0002 |
| 4 | `0004-mastery-policy` | 门槛、状态派生、`nextObjective`、`mapSummary`、前置硬门控 | `domain/policy/*` | TC-C1…C17、TC-F1/F6–F8 | 0002 |
| 5 | `0005-mode-gating` | 静态分类表、fail-closed 守卫、准入错误、预算常量 | `domain/mode/*` | TC-D1…D9 | 0002 |
| 6 | `0006-service-core` | 建路径/改名/删除、来源附加、声明掌握、defer、单槽位交互、公共投影 | `service/LearningService`、`domain/projection` | TC-C6–C8、TC-C16/C17、TC-E1/E2/E4 | 0003、0004 |
| 7 | `0007-outline-build-revise` | build/revise、校验、缺口分类、覆盖报告 | `service/outline/*` | TC-J1…J6、TC-F2…F5、TC-N1…N4 | 0006 |
| 8 | `0008-quiz-grade` | 出题注册、choice/short/open 判分、幂等、提示降级 | `service/QuizService`、`domain/grading` | TC-K1…K6、TC-H1…H6、TC-E3 | 0007 |
| 9 | `0009-qualitative-assess` | 定性评估、证据累积、门槛清除 | `service/AssessService` | TC-M3、`test_assess` 对照 | 0008 |
| 10 | `0010-scheduler-sm2` | SM-2、播种、失败重学步、复习队列 | `domain/schedule`、`service/ReviewService` | TC-G1…G7 | 0009 |
| 11 | `0011-question-bank` | 判分入库、知识点自动关联、标签、筛选 | `service/QuestionBankService` | `test_question_bank` 对照 | 0010 |
| 12 | `0012-repair-profile` | 题目修复（void/correct）、学习者画像、`getState` 快照 | `service/LearningService` | `test_repair_profile` 对照 | 0011 |
| 13 | `0013-path-binding-scratch` | 路径概览、scratch 建档、会话绑定、删除级联 | `service/LearningService` | `test_path_binding` 对照 | 0012 |
| 14 | `0014-mastery-nav` | 只读导航（topics/sessions/module 解析）+ hand-off 卡片 | `service/NavigationService` | `test_mastery_nav` 对照 | 0013 |
| 15 | `0015-agent-loop-llm` | LLM 接口 + OpenAI 兼容客户端 + Fake + Loop + 预算 + **工具层**（`ToolContext` / `ToolResult` / `ToolRegistry` / 全部 `Mastery*Tool`，含 `MasteryRepairQuestionTool`、`MasteryProfileTool`） | `adapter/llm/*`、`adapter/loop/*`、`adapter/tool/*` | `test_llm_client`、`test_loop` 对照；TC-M6；`test_repair_profile` 的工具层 2 例；TC-M2 | 0014 |
| 16 | `0016-rest-api` | 全部 REST 端点、错误码、事件流端点 | `adapter/web/*` | TC-I1…I7、`test_api`/`test_f0_api` 对照 | 0015 |
| 17 | `0017-ws-turn` | WS 回合、事件顺序、`session_meta`、部分文本保留 | `adapter/turn/*`、`adapter/web/WsTurnHandler` | `test_turn_runner`、`test_ws_runtime`、`test_chat_nav` 对照 | 0016 |
| 18 | `0018-knowledge-base` | 文件夹扫描、知识库 CRUD、索引、检索、`rag`/`read_source` 条件挂载 | `service/source/*`、`service/knowledge/*`、`adapter/tool` | `test_knowledge`、`test_knowledge_chat`、`test_source_tools` 对照 | 0017 |
| 19 | `0019-settings-task-model` | 设置存储/提供方目录/探测、Task Model 建议 + 回退 | `storage/SettingsStore`、`adapter/llm/TaskModel` | `test_settings_probe`、`test_task_model`、`test_preferences` 对照 | 0018 |
| 20 | `0020-outline-eval-cli` | 大纲评测 CLI、确定性指标、基线对比 | `adapter/eval/*`、`cli/*` | `test_eval` 对照、TC-N5/N6 | 0019 |
| 21 | `0021-acceptance` | 旧库双向读取、前端联调、全量回归、全部归档 | 无新增业务代码 | 全量 PASS；G7 清单全绿 | 0020 |

### 5.1 依赖图

```
0001 → 0002 ─┬→ 0003 → 0006 → 0007 → 0008 → 0009 → 0010 → 0011 → 0012 → 0013 → 0014
             └→ 0004 ─┘                                                              ↓
             └→ 0005 ────────────────────────────────────────────────（0005 可与 0006 并行，但需在 0015 前完成）
                                                                                     ↓
     0015 → 0016 → 0017 → 0018 → 0019 → 0020 → 0021
```

- `0004`、`0005` 是纯函数层，可与 `0003` 并行推进（不同文件、无共享状态）。
- 其余严格串行：每一步都建立在前一步的事务边界与投影之上。

### 5.2 原 0012 的拆分记录（2026-11-04）

原计划把「题目修复 + 画像 + 路径绑定 + 导航」合成一个 `0012-repair-profile-nav`。
实测参考实现约 840 行、17 个原子任务，超过 `AGENTS.md` §3 的 8 个上限，故拆为三个：

| 新编号 | 内容 | 任务数 | 测试对照 |
| --- | --- | --- | --- |
| `0012-repair-profile` | 题目修复 + 画像 + `getState` | 7 | `test_repair_profile.py`（5 用例） |
| `0013-path-binding-scratch` | 路径概览 / scratch / 会话绑定 / 删除级联（写侧） | 5 | `test_path_binding.py`（5 用例） |
| `0014-mastery-nav` | 只读导航 + hand-off 卡片 | 4 | `test_mastery_nav.py`（4 用例） |

拆分理由：写侧绑定与只读导航是两种行为增量，混在一起违反「一个 change 只含一个行为增量」；
原 `0013`–`0019` 顺延为 `0015`–`0021`（仅改编号，无代码成本）。
`test_chat_nav.py` 依赖 `ChatService` + `AgentLoop` + 假 LLM，挂到 `0017-ws-turn`。

---

## 6. 每个 change 的固定交付物

严格按规范第 5 节五阶段，缺一不可：

| 阶段 | 产物 | 检查点 |
| --- | --- | --- |
| Plan | `proposal.md` / `design.md` / `tasks.md` / `.openspec.yaml` | `.openspec.yaml` 写 `skip_specs: true`（行为等价重写） |
| Check | — | `openspec validate <id> --strict` 通过 |
| Build | Java 源码 + JUnit 用例 + 勾选 `tasks.md` | 只改 `tasks.md` 列出的文件 |
| Verify | `artifacts/verify-<id>-<ts>.log` + `docs/verify/<id>.md` + 追加 `docs/regression-log.md` | 失败最多自愈 3 轮 |
| Merge | `openspec archive <id>` + 更新 `README.md` 看板 + `docs/traceability.md` 状态 | 需人类批准 |

---

## 7. 验证策略

### 7.1 三层测试

| 层 | 范围 | 特征 | 数量目标 |
| --- | --- | --- | --- |
| L1 纯函数 | `domain/*` | 无 Spring、无 I/O、无网络；毫秒级 | 覆盖 TC-A/C/D/F/G/H/J/N 的判定部分 |
| L2 存储/服务 | `storage/*`、`service/*` | 真实 SQLite（临时目录），多线程并发用例 | 覆盖 TC-B 全组、TC-E/K/M |
| L3 协议 | `adapter/web/*`、`adapter/turn/*` | `@SpringBootTest` + `WebTestClient` + 假 LLM | 覆盖 TC-I 全组、WS 序列 |

### 7.2 与参考实现对照

不追求「行数等价」，只追求「行为等价」。对照方法：

1. 每个 change 在 `tasks.md` 里列出它负责的 Python 测试文件（如 `tests/test_policy.py`）；
2. Build 阶段逐个读该文件的用例，翻译为 JUnit（方法名与 `traceability.md` 一致）；
3. Verify 阶段同时跑两侧：Java 侧 `mvnw test`，参考侧 `pytest -q <file>`，两侧结果都贴进 `docs/verify/`；
4. 若两侧结论不一致，**先假设 Java 错**，查清后在偏差表登记；确属参考实现缺陷则单独开 change。

### 7.3 唯一验证入口

`scripts/verify.ps1` 负责：编译 → 分层测试 → 落日志 → 打印「下一步：回填报告」。
接口与规范 8.1 一致：`--scope all|unit|storage|api`、`--change <id>`。

---

## 8. 风险与对策

| # | 风险 | 影响 | 对策 |
| --- | --- | --- | --- |
| R1 | Python 的「隐式契约」（如 Pydantic 默认值、`extra="ignore"`）在 Java 侧漏掉 | 数据兼容失败 | `0002` 用「读参考库 + 字段快照比对」做验收；Jackson 全局配置显式声明 |
| R2 | `service.py` 1105 行是「上帝对象」，直接翻译会变成巨型类 | 可维护性差 | 按领域拆 5 个服务类，但**事务边界不变**；拆分类在同一 change 内完成，不跨 change |
| R3 | 存储并发语义（WAL、CAS 重试、前置失败不重试）难以等价 | 数据损坏 | `0003` 必须用真实多线程 + 真实 SQLite；禁止 mock；TC-B9…B17 全部实跑 |
| R4 | WS 回合的「线程 + 队列」桥接在 Java 里形态不同 | 前端表现异常（心跳饿死） | `0015` 明确用 Spring 的异步/事件机制，并在验收中比对事件顺序与心跳 |
| R5 | 向量检索依赖（llama-index/faiss）在 Java 无等价物 | 检索质量下降 | 已定（§9 Q3）：`0016` 只做确定性检索 + 可插拔嵌入接口；向量检索另立 change，质量差异登记到验证债，不阻塞归档 |
| R6 | 前端字段漂移无人发现 | 联调失败 | `0014` 起每个端点写「字段快照测试」；`0019` 用真实前端跑通主要页面 |
| R7 | 范围蔓延（顺手改前端/改阈值） | 失控 | 红线 1；任何额外改动单独开 change |
| R8 | 参考实现存在**内部不一致**（同一概念不同措辞 / 同一字段不同事件是否携带） | 若在重写中「顺手统一」即为行为变化 | 一律**照抄并逐字锁定**；统一需求单独开 change（见 §11） |

---

## 11. 已知行为瑕疵登记（不改，只记录）

> 下列条目均为**参考实现原样行为**。本重写按「行为等价」原则照抄，并用测试逐字锁定；
> 任何统一/修正都属于行为变化，必须单独开 change 并同步修改 Python 参考实现。

| # | 位置 | 现象 | 影响 | 处置 |
| --- | --- | --- | --- | --- |
| Q1 | `learning/service.py:370` vs `:871`/`:881` | 同一个「找不到知识点」有两种文案：`override_mastery` 用 `unknown knowledge point: {id}`，`note_explained` / `defer_objective` 用 `unknown objective: {id}` | 无功能影响；按字符串匹配错误信息的调用方需注意两套 | 照抄。Java 侧由 `MasteryOverrideServiceTest#unknownPointUsesKnowledgePointWording` 与 `ObjectiveStateTest#deferUnknownUsesObjectiveWording` 锁定 |
| Q2 | `learning/service.py:885` vs `:873`/`:382` | `objective.deferred` 的 payload **不含** `note`，而 `objective.explained` 含 `summary`、`mastery.override` 含 `note` | 无功能影响；`note` 仅落在聚合的 `DeferredObjective.note`，事件回放拿不到 | 照抄。Java 侧由 `ObjectiveStateTest#deferEventOmitsNote` 与 `#noteExplainedEventIncludesSummary` 锁定 |
| Q3 | `learning/policy.py:137` | `require_type_unchanged` 在生产代码中**无调用者**（真正生效的是 `learning/outline.py:444`） | 无功能影响；纯函数被保留但未接入 | `0004` 已实现该函数并有单测；`0007` 在 revise 中按 `outline.py` 的行为实现拒绝 |
| Q4 | `capabilities/mastery/tools.py:626` | 文档 R4 要求 `mastery_grade` 在 review 下拒绝未掌握 KP，但代码只对 `quiz` 与 `assess` 做了该检查 | review 下可对未掌握 KP 判分（只要题已存在） | 照抄代码行为；`0008` 落地时登记 |
| Q5 | `learning/service.py:1000` | `tag_question` 不校验标签：空串、纯空白、甚至 `None`（转成字符串 `"None"`）都会写入 | 题库可积累看似脏的标签；`list_questions(tag='  ')` 会真的过滤 | 照抄。`0011` 用真实 Python 探针确认后锁定（`QuestionBankTest#blankTagsPreserved` / `#nullTagBecomesPythonNone` / `#blankTagStillFilters`） |

**统一建议**：以上五条均建议在 `0019-acceptance` 完成后评估。届时全链路稳定，
可新建 `0020-behavior-cleanup` 同时修改 Java 与 Python 两侧，并按增量规范走完整五阶段。

---

## 9. 人类决策记录（已批准）

| # | 问题 | 决定 | 批准日期 |
| --- | --- | --- | --- |
| Q1 | 是否接受 G0–G7 的重新映射（见 §4） | **接受**。规范原义的 G3–G6 是「采集/识别/持久化/管理」，与本项目能力不对应，重新映射为「大纲/回合/协议/知识库」 | 2026-11-04 |
| Q2 | 是否接受「前端不改」的验收方式 | **接受**。前端作为契约消费者反向验证后端；前端重写另立 change 且需先补行为级验收用例（§1.2/§1.3） | 2026-11-04 |
| Q3 | 知识库检索的 Java 实现深度 | **先确定性检索**（可测、可复现）+ 可插拔嵌入接口；向量检索作为独立 change | 2026-11-04 |
| Q4 | 是否允许 Java 类名/包名与 Python 模块一一对应 | **允许**。交叉审查成本最低，映射表见 §3 | 2026-11-04 |

> 人类对 Q1–Q4 的答复原文：「均接受建议」。

---

## 10. 建议的第一步

计划已于 2026-11-04 批准，立即执行 `0001-java-skeleton`（Plan → Check → Build → Verify），用最小的一个 change 验证流程本身可用（规范 14.13 的要求）。它的产物只有：可编译工程、一个冒烟用例、一份真实验证报告。

**在 `0001` 的验证报告出现之前，不应开始 `0002`。**
