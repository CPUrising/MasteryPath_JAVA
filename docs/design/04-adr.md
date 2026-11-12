# 04 · 架构决策记录（ADR）

> 对应作业工件 2 的"关键决策"部分。每条 ADR 都写清**背景 → 决策 → 备选与否决理由 → 后果**。
> 优先级标记：★★★ 为"改了就伤筋动骨"的决策，★★ 为"会影响后续很多选择"，★ 为局部决策。
> "证据"列给出仓库中可复核的位置（路径相对仓库根）。

## 索引

| # | 决策 | 优先级 | 一句话立场 |
| --- | --- | --- | --- |
| [ADR-01](#adr-01) | 教学与判定解耦，判定下沉为纯函数 | ★★★ | 模型可以教，但不能判 |
| [ADR-02](#adr-02) | 单聚合根 + JSON + version CAS | ★★★ | 一个真相源，一个写入口 |
| [ADR-03](#adr-03) | 模式门控在调用期、静态表、fail-closed | ★★★ | 权限不写在 UI 上 |
| [ADR-04](#adr-04) | 类型驱动的掌握定义（量化 0.9 / 定性） | ★★★ | 类型决定"什么叫会" |
| [ADR-05](#adr-05) | 确定性判分 + "未判分"语义 | ★★★ | 解析不了不判错 |
| [ADR-06](#adr-06) | 服务端存答案 + 投影白名单 | ★★★ | 泄题是产品级事故 |
| [ADR-07](#adr-07) | 间隔复习用 SM-2，不迁 FSRS | ★★ | 先有闭环，再谈最优 |
| [ADR-08](#adr-08) | 双轨进度并区别播种 | ★★ | "我觉得会"和"系统认为会"分开记 |
| [ADR-09](#adr-09) | 单槽位待答题 + 出题即结束回合 | ★★ | 提问-作答是一个原子单位 |
| [ADR-10](#adr-10) | 传输分流：WS / REST / SSE | ★★ | 按交互节拍选协议 |
| [ADR-11](#adr-11) | 本地优先单机部署 + SQLite | ★★★ | 零运维换可移植 |
| [ADR-12](#adr-12) | Java 21 + Spring Boot + Maven Wrapper | ★★ | 选"排错命中率最高"的一组 |
| [ADR-13](#adr-13) | 供应商无关的 LLM 适配层 | ★★ | 领域层不许出现供应商类型 |
| [ADR-14](#adr-14) | 回合预算与四类结束原因 | ★★ | 成本与死循环都要有硬上限 |
| [ADR-15](#adr-15) | 生成型练习卷独立成表、不写掌握分数 | ★★ | 练习归练习，证据归证据 |

---

## ADR-01

**教学与判定解耦，判定下沉为 `domain` 纯函数** ★★★

**背景。** 产品的差异化是"掌握可判定"。若让模型自评"你已掌握"，门槛就会被幻觉击穿；且这类缺陷无法通过测试发现（同一输入两次回答不同）。

**决策。**
- 判分（choice/short/open）、掌握门槛、下一路标、先修门控、复习调度、题目投影全部实现为 `domain` 层的纯函数：无 I/O、无网络、无 Spring，`now` 显式传入。
- `service` 只调用这些函数的结论并负责落库；模型只能提交"事实请求"（出题、作答、评估），不能提交"结论"。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 模型自评 + 阈值 | 不可复现、不可回归，且与"防止幻觉击穿"直接冲突 |
| 模型打分 + 人工抽检 | 单用户产品没有抽检能力；错误在学习者侧不可见 |
| 规则放在 `service` | 会被 I/O 与事务牵住，无法离线毫秒级断言 |

**后果。** 正面：判定可逐值断言，离线可跑，换模型不影响门槛。负面：规则变更必须走代码（不能靠提示词微调）；定性评估仍需模型，成为"半确定性"的边界（见 R-09）。

**证据。** `src/main/java/com/masterypath/domain/policy/MasteryPolicy.java`、`domain/grading/Grading.java`、`domain/schedule/Sm2Scheduler.java`、`domain/mode/ModeGate.java`；`openspec/project.md` §4"分层依赖方向"。

---

## ADR-02

**单聚合根 `LearningProgress` + JSON 存储 + `version` CAS** ★★★

**背景。** 一条学习路径的全部状态（地图、分数、证据、复习、缺口、待答题）必须**同时**演进：出题要在一次写入里同时占用题槽与写事件；判分要同时写 attempt、evidence、interaction 与掌握度。若拆成十几张表分多次写，任何一次失败都会留下"分数涨了但证据没写"的脏状态。

**决策。**
- 把整条路径作为一个聚合，序列化为 `paths.data` 的 JSON；`paths.version` 作为乐观锁。
- 所有写操作走 `LearningStore.mutate(pathId, fn)`：读 `(version, data)` → 反序列化 → 就地修改 → `UPDATE ... WHERE path_id=? AND version=?` → 失败抛 `CasConflictException` 并重试（≤3 次 + 抖动）。
- 领域事件在回调内入内存队列，**CAS 成功后才以本次新 revision 批量落库**，失败整体回滚。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 规范化多表 + 事务 | SQLite 单写者下可行，但会让"一次工具调用 = 一次事务"变成跨十几张表的隐式契约，难审计 |
| 事件溯源（事件即真相） | 需要快照、重放与版本迁移，复杂度远超 MVP；且事件含答案键，作为真相源会放大泄题面 |
| 悲观锁（`SELECT ... FOR UPDATE` 语义） | 单机单写者下收益低，且会引入锁等待与超时语义 |

**后果。** 正面：一次写入 = 一次事实变更，并发安全，事件与状态同版本可对账；备份就是复制文件。负面：聚合变大后有写放大（R-01）；"读-改-写"期间不能跨聚合事务（本系统只有一条路径被改，可接受）。

**证据。** `storage/LearningStore.java`（`mutate` / `retryCas` / 事件落库）、`storage/Transaction.java`、`storage/Schema.java`。

---

## ADR-03

**模式门控在调用期执行，静态分类表 + fail-closed 守卫** ★★★

**背景。** 三种模式（大纲 / 学习 / 复习）的能力差异必须真实生效。若只在 UI 禁用按钮，模型仍可能调用越权工具（它看不到按钮状态），用户也可以直接打 REST。

**决策。**
- `ToolClassification` 用**静态表**声明每个工具的模式归属（大纲专属 2 个、学习/复习共用 9 个、三模式共享 12 个）。
- 每次工具调用都过 `ModeGate.toolIsAllowed`；越权返回可操作错误并建议正确模式。
- 启动期 `assertClassificationComplete`：任何工具未归类、或共享集合与专属集合相交，直接抛异常，**服务起不来**。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 只在 UI 禁用 | 模型与 REST 都能绕过，等于没有约束 |
| 在回合开始时就裁剪工具清单 | 回合中途允许切模式，裁剪后会"切了但工具没更新"；调用期校验天然支持中途切换 |
| 用注释/约定 | 新工具漏归类会静默共享，风险不可见 |

**后果。** 正面：约束不可绕过、约束本身可测、加工具必须思考权限。负面：拒绝也会消耗一次工具预算（R-10）；分类表与 schema 需双写（QA-6 有意接受）。

**证据。** `domain/mode/ToolClassification.java`、`domain/mode/ModeGate.java`、`adapter/tool/MasteryTools.java`、`adapter/tool/StudyTools.java`（复习模式拒绝未掌握）、`adapter/web/SessionController.java`（REST 切换模式同样校验）。

---

## ADR-04

**类型驱动的掌握定义：量化 0.9 加权窗口 / 定性通过** ★★★

**背景。** "掌握"对记忆类与概念类不是一回事。用同一把尺子会导致两种失败：概念题靠选择题"蒙过"，或记忆类要求写长篇解释。

**决策。**
- `memory` / `procedure`：近期加权正确率 ≥ **0.9**（最近 5 次，权重 0.5 / 0.7 / 0.85 / 0.95 / 1.0，只计独立且未作废的作答）；有效样本只有 1 次时分数上限 0.5、2 次时 0.8，从机制上防止"两次满分就通关"。
- `concept` / `design`：不看分数，只认定性评估通过（`qualitativeMastery`）；选择题判对也不授予掌握。
- 额外证据要求（`required_evidence`）优先于上述两分支：缺证据即未掌握。
- 先修未掌握则目标不可选（fail-closed）。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 全类型统一用正确率 | 概念/设计可以被选择题糊弄，产品的核心承诺失效 |
| 按模块/按题型分档阈值 | 阈值维度爆炸，无法向学习者解释"为什么这题要 0.85" |
| 用模型置信度加权 | 引入不可复现因素 |

**后果。** 正面：判定可解释（"你最近 5 次里错了 2 次"）、可测。负面：0.9 是未标定的先验；定性评估依赖模型，成为唯一"非确定性门槛"（PoC 需验证一致率）。

**证据。** `domain/policy/MasteryPolicy.java`、`domain/policy/MasteryScore.java`、`service/LearningService.java`（`assess` 路径）、`adapter/tool/StudyTools.java`（定量目标拒绝 `open` 题）。

---

## ADR-05

**确定性判分 + "未判分"（ungraded）语义** ★★★

**背景。** 判分必须可复现，且不能因为"看不懂答案"就判错——那会误伤学习者，并污染掌握分数。

**决策。**
- 三套规则：选择题按标签或归一化正文解析（歧义视为解析失败）；短答先精确、再认可接受答案、再数字相对误差 `1e-6`、最后相似度（`1 - 编辑距离/较长长度`）≥ **0.90**（超过 30 字符只做精确匹配）；开放题要求必选关键词全中，可选关键词命中率 ≥ **0.60**。
- 解析失败一律 `ungraded`：**零状态变更**，题卡保留，要求学习者澄清；模型侧收到 `ok=false` 并被要求追问。
- 开放题即使判对也强制降为 `partial`，不得单独授予掌握。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 模型判主观题 | 不可复现；与 ADR-01 冲突 |
| 解析失败判错 | 会误伤（同义表达、错别字），且学习者无从申诉 |
| 模糊匹配阈值放宽到 0.8 | "宁严勿宽"：误判正确会直接推高掌握度 |
| 用嵌入相似度判短答 | 增加一次外部调用，且阈值更难解释 |

**后果。** 正面：判分零模型调用、可离线、错误可解释。负面：开放式短答的同义表达仍可能被判错（记录为 R-11，PoC 用真实作答抽样评估）。

**证据。** `domain/grading/Grading.java`、`domain/grading/Decision.java`、`service/LearningService.java`（`ungraded` 早退）、`adapter/tool/StudyTools.java`（转成 `ok=false`）。

---

## ADR-06

**参考答案只存服务端，所有对外投影走白名单** ★★★

**背景。** 卡片要发给前端，但卡片里如果带答案，学习者一看就知道；且投影一旦"顺手带上全字段"，泄题会随字段增长反复出现。

**决策。**
- 掌握题卡的公开投影固定 7 个键（题干、题型、选项、难度等），显式排除 `expected_answer` / `explanation` / `accepted_answers` / `required_keywords` / `optional_keywords`。
- 题卷（quiz set）提交前只发公开投影，**提交后**才释放答案与解析。
- 题库的管理视图可含答案，但刷题卡投影不含答案、解析、用户作答与正误。
- `question_card` 事件与 assistant 消息 `data.card` 使用**同一份投影**，避免两条路径漂移。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 前端隐藏答案 | 网络面板可见，等于公开 |
| 黑名单剔除字段 | 新增字段默认外泄；白名单才是安全默认 |
| 答题后再下发全部字段 | 尚无"答题后立即校验"的需求，先最小化暴露面 |

**后果。** 正面：泄题面收敛到三处投影，可一次性断言。负面：新增公开字段需要改投影（有意为之的摩擦）。

**证据。** `domain/projection/QuestionCardProjector.java`、`domain/projection/QuizSetProjector.java`、`domain/projection/QuestionBankProjector.java`、`adapter/web/QuizSetController.java`。

---

## ADR-07

**间隔复习用 SM-2，不迁 FSRS** ★★

**背景。** 复习调度只需"该复习时出现、失败后重学、成功则延长"。FSRS 更准，但需要大量复习日志训练参数；本项目冷启动没有数据。

**决策。** SM-2 起步：EF 初值 2.5、下限 1.3；间隔序列 1 天 → 6 天 → `round(prev × EF)`（银行家舍入）；失败（质量 <3）回到 10 分钟重学并标记"锚点由失败设置"；质量映射：无提示正确=5、有提示正确=4、部分正确=3、其余=0。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| FSRS（DSR 模型） | 需要参数拟合；与 SM-2 混用会出现"两套间隔逻辑漂移" |
| 固定间隔（1/3/7/30 天） | 无法反映易度差异，复习量分布极不均衡 |
| 只用 Ebbinghaus 曲线 | 缺少个体化与失败重学语义 |

**后果。** 正面：可手算、可解释、零依赖。负面：不是最优调度；`difficulty/stability/retrievability` 字段留而不用，属于"预留接口"而非现状能力（R-12）。

**证据。** `domain/schedule/Sm2Scheduler.java`、`service/LearningService.java`（三种调度分支：首次跨门槛播种 / 正式复习重排 / 非正式仅记录）。

---

## ADR-08

**双轨进度：系统评估与学习者声明分开记录，并区别播种** ★★

**背景。** 学习者常会说"这个我早就会了"。若直接写掌握分数，等于让用户自评；若完全不允许，路径会被"已知内容"卡住。

**决策。** 声明掌握产生 `LearnerMasteryOverride`（来源 `learner`）：推进地图与"下一路标"，但**不写掌握分数、不产证据**；同时按更短的初始间隔播种复习（系统轨 1 天 × 0.5 = 约半日），让"自称会"的内容更快被验真。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 直接写分数 | 门槛失效 |
| 不允许声明 | 无法处理已会内容，路径体验崩坏 |
| 声明后立刻出题验证 | 会阻塞学习者；且"刚声明就被考"体验差 |

**后果。** 正面：两条来源都可追溯，复习会替系统"复核"。负面：声明过多时复习负担前置；`provenance` 只在播种时决定，后续不重算（有意简化）。

**证据。** `service/LearningService.java`（override 路径传入 `"learner"`）、`domain/schedule/Sm2Scheduler.java`（`LEARNER_FIRST_FACTOR=0.5`）、`domain/policy/MasteryPolicy.java`（来源非空即视为掌握）。

---

## ADR-09

**单槽位待答题 + 出题即结束回合** ★★

**背景。** 若允许多题同时挂起，学习者可以"先看所有题再答"，且"哪道题对应哪次讲解"会失真；若答题不结束回合，就会出现"导师一边讲一边判"的混乱时序。

**决策。** 路径聚合里只有一个 `pending_question`；已有未答题时再次出题**重放同一张卡**；出题工具调用 `endTurn`，回合以 `turn_end(reason=card)` 收尾；学习者的下一条消息即为作答，服务端**先判分再让导师说话**。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 多题队列 | 时序不可追踪；判分与讲解的对应关系丢失 |
| 出题后继续讲解 | 学习者可能已从后续讲解中得到答案 |
| 作答走 REST | 判分与反馈被拆成两个请求，出现"判了但导师不知道"的窗口 |

**后果。** 正面：提问-作答成为可追踪、可幂等的最小单位。负面：想连续做多题只能"一题一回合"（题卷能力即为此而生，见 ADR-15）。

**证据。** `domain/model/LearningProgress.java`（`pendingQuestion`）、`adapter/tool/StudyTools.java`、`adapter/loop/AgentLoop.java`（`endTurn` → `turn_end(card)`）、`adapter/turn/TurnRunner.java`（答前判分）。

---

## ADR-10

**传输分流：把握回合走 WS、聊天走 SSE、其余走 REST** ★★

**背景。** 三种交互的节拍不同：掌握回合是"一次请求 → 有序多事件"；聊天是"单向流 + 可撤回"；其余是普通请求-响应。

**决策。** WS `/ws` 承载 `mastery_path` 与 `deep_question`；SSE `POST /chat/sessions/{id}/turns` 承载普通聊天；REST 承载其余全部操作。事件名与顺序作为契约冻结（`text_delta` / `tool_call` / `tool_result` / `question_card` / `session_meta` / `turn_end` / `error`；题卷另有 `quiz_plan` / `quiz_question` / `quiz_set`）。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 全部 WS | 聊天需要"按消息 id 撤回"，WS 上要自建请求关联；REST 天然带资源语义 |
| 全部 SSE | 掌握回合需要"每回合一请求"，SSE 仍要 POST；且事件顺序在 WS 上更自然 |
| 轮询 | 文本增量体验崩塌 |

**后果。** 正面：每通道都最贴合其节拍；契约测试可按通道分别断言。负面：前端维护两套流式客户端（`ws.ts` / `chat-api.ts`）；WS 每连接只处理一条请求，无断线续传（R-03）。

**证据。** `adapter/web/WsTurnHandler.java`、`adapter/turn/TurnRunner.java`、`adapter/web/ChatController.java`、`adapter/web/WebSocketConfig.java`、`web/lib/ws.ts`、`web/lib/chat-api.ts`。

---

## ADR-11

**本地优先的单机部署 + SQLite（而非服务端数据库）** ★★★

**背景。** 目标用户是单个自学者；数据含学习记录、画像与可能敏感的资料来源；没有运维预算。

**决策。** 单 Spring Boot 进程 + 单前端进程 + 一个 `data/` 目录；SQLite（WAL，`busy_timeout=5000`）+ 手写 JDBC；默认仅监听本机；外部依赖只有"可配置的模型提供方"，可整体离线。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| PostgreSQL / MySQL | 需要安装与运维；单用户场景无收益 |
| 云托管 + 账号体系 | 引入鉴权、多租户、隐私合规；超出 MVP |
| 嵌入式 KV（如 H2 文件） | SQL 生态与工具链不如 SQLite；备份与可读性差 |

**后果。** 正面：零安装、零运维、备份即复制、离线可用、隐私默认收敛在本地。负面：无法多用户/多设备同步；SQLite 单写者限制并发（R-02）；迁移到服务端数据库需要重写 `storage` 层（接缝已隔离）。

**证据。** `storage/LearningStore.java`（WAL / busy_timeout / 单连接）、`src/main/resources/application.properties`（`masterypath.data-dir`）、`adapter/web/CorsConfig.java`、`README.md`（数据落点）。

---

## ADR-12

**Java 21 + Spring Boot 3.5 + Maven Wrapper** ★★

**背景。** 后端需要 HTTP、WebSocket、JSON、校验、结构化日志与测试框架；团队熟悉 Java；开发机不预装构建工具。

**决策。** Java 21（record / sealed / pattern matching 用于领域枚举与规则）、Spring Boot 3.5（web / validation / actuator）、Maven Wrapper 固定构建版本、Jackson、JUnit 5 + AssertJ。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| Node/TypeScript 全栈 | 单一语言省心，但领域规则的"穷尽分支"表达力不如 sealed + switch；且 WS/REST 生态需自拼 |
| Go | 运行时更轻，但团队经验与生态（校验、JSON 映射）成本更高 |
| Quarkus / Micronaut | 冷启动与内存更优，但本项目不是 Serverless，收益不可见 |
| Gradle | 单模块无自定义构建逻辑，Gradle 的灵活性用不上 |

**后果。** 正面：文档与排错资源充足；wrapper 保证"有 JDK 即可构建"。负面：JVM 内存占用高于 Node/Go；启动约数秒（本地可接受）。

**证据。** `pom.xml`、`.mvn/wrapper/maven-wrapper.properties`、`openspec/project.md` §3。

---

## ADR-13

**供应商无关的 LLM 适配层，只实现 OpenAI 兼容协议 + 脚本化假客户端** ★★

**背景。** 模型提供方会变（价格、可用性、地区）。若领域或服务层出现供应商类型，换模型就是一次重构；若测试依赖真实模型，则测试不可重复、不可离线。

**决策。**
- `LlmClient` 接口只暴露 `chat` / `streamChat`（消息 + 工具定义 → 文本增量与工具调用）。
- 只实现 OpenAI 兼容协议（含 SSE 解析与 `tool_calls` 按 index 拼装）；`anthropic` 协议显式拒绝而不是"尽力兼容"。
- 错误分类固定为 `auth_error / model_not_found / rate_limited / bad_request / upstream_error`，`retryable` 只表达"重试可能成功"，**不自动重试**；错误消息不含上游响应体（可能含凭据）。
- 测试用 `FakeLlmClient`：脚本化响应 + 记录"下发的工具 schema"。
- 模型选择顺序固定：显式选择 → 设置里的 `active_id` → 第一个模型。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 官方/第三方 SDK | 会把供应商类型带进服务层；流式与错误语义被 SDK 抽象掉，难以断言 |
| Spring AI / LangChain4j | 抽象层过厚，本项目只需要三个能力；版本升级会牵动测试 |
| 多协议全支持（Anthropic 等） | 需求未出现；"半支持"比"明确不支持"更危险 |

**后果。** 正面：换提供方 = 改设置；测试离线；错误可枚举。负面：非 OpenAI 兼容的提供方需要写新适配器（有意留白）。

**证据。** `adapter/llm/LlmClient.java`、`OpenAiCompatibleClient.java`、`SseParser.java`、`LlmError.java`、`FakeLlmClient.java`、`ModelResolution.java`。

---

## ADR-14

**回合预算：单回合工具 ≤6、连续追问 ≤3、模型轮数 ≤6，并有四类结束原因** ★★

**背景。** 智能体的成本与失控都是"回合内"发生的：模型可能反复查状态、连续出题、或在工具失败后无限重试。

**决策。** 三类硬上限（工具 6 / 连续追问 3 / 同题提示 2）+ 轮数上限 6；结束原因显式化：`done` / `card` / `budget` / `max_rounds`；超预算的那次工具**不执行**。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 只靠提示词要求"别调太多" | 不可保证，且无法断言 |
| 按 token 计费截断 | 截断点不可预测，可能砍在半句话上；成本与行为脱钩 |
| 无轮数上限 | 工具失败 + 模型固执 = 死循环 |

**后果。** 正面：单回合成本有上界，行为可解释（`turn_end.reason`）。负面：复杂任务可能被 6 次卡住（PoC 需标定，OQ-02）；同题提示上限目前只有常量与测试，尚未在主流程消费（记录为 R-13）。

**证据。** `domain/mode/BehaviorBudget.java`、`adapter/loop/AgentLoop.java`、`adapter/loop/StreamEvent.java`、`service/LearningService.java`（连续追问拒绝）。

---

## ADR-15

**生成型练习卷独立成表、独立通道，且不写掌握分数** ★★

**背景。** 设计评审发现第一部分的缺口：只有"导师出一道题"，学习者无法主动做整卷练习；若把整卷做成"连续出题"，会破坏单槽位时序；若把整卷计入掌握，又会出现"刷卷刷出掌握"。

**决策。**
- 新增 `deep_question` 能力：一次结构化生成 N 题（1–10），事件 `quiz_plan → quiz_question×N → quiz_set → turn_end`。
- 题卷存 `quiz_sets` / `quiz_items` 两张独立表，状态只有 `open / submitted`，**不进入** `interactions`、待答题槽位与掌握度。
- 提交前只发公开投影；提交时逐题判分，任一题 `ungraded` 则整卷零状态变更并返回原因；已提交幂等。
- 生成侧硬校验：题数必须恰好等于请求数；题型必须在允许集合；`short` 必须有可接受答案。

**备选与否决。**

| 备选 | 否决理由 |
| --- | --- |
| 复用 `mastery_quiz` 连续出题 | 破坏单槽位与回合收口语义；一次失败会留下半套题 |
| 题卷也写掌握分数 | 绕开"逐点掌握"的证据模型，等于开后门 |
| 让模型自由决定题数 | 题数与 UI 进度、成本预估都不可控 |

**后果。** 正面：练习与证据物理隔离；一次生成、一次提交，失败不落半卷。负面：多了一套表与一套事件（复杂度换清晰度）；题卷不计入掌握，用户可能困惑"我做了卷为什么没涨"——需在 UI 文案中明说（见 [06](06-ui-ux.md)）。

**证据。** `adapter/turn/QuizSetTurn.java`、`adapter/turn/QuizSetGenerator.java`、`domain/quizset/QuizSetSpec.java`、`storage/Schema.java`（`quiz_sets` / `quiz_items`）、`adapter/web/QuizSetController.java`。

---

## 被否决的方案总表（便于答辩时回答"为什么不那样做"）

| 备选方案 | 一句话否决理由 | 对应 ADR |
| --- | --- | --- |
| 模型自评掌握 | 不可复现、门槛失效 | 01、04 |
| 事件溯源作为真相源 | 复杂度高，且事件含答案会放大泄题面 | 02 |
| 只在 UI 禁用越权操作 | 模型与 REST 可绕过 | 03 |
| 统一正确率门槛 | 概念/设计可被选择题糊弄 | 04 |
| 模糊阈值放宽到 0.8 | "宁严勿宽"，误判正确直接推高掌握 | 05 |
| 黑名单剔除敏感字段 | 新字段默认外泄 | 06 |
| 直接上 FSRS | 无数据可拟合，且会与 SM-2 双轨漂移 | 07 |
| 多题挂起队列 | 时序不可追踪 | 09 |
| 全 WS 或全 SSE | 两种节拍的代价都会转嫁到另一端 | 10 |
| PostgreSQL + 账号体系 | 单用户场景引入运维与合规成本 | 11 |
| 官方 SDK / 重框架 | 抽象过厚、类型泄漏到领域层 | 13 |
| 纯提示词控成本 | 无法断言、无法保证 | 14 |
| 题卷复用单题通道 | 破坏单槽位与回合收口 | 15 |
