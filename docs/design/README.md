# MasteryPath · 第二部分：系统设计（设计工件索引）

> 课程：系统分析与设计 · 2026–2027 学年第一学期 · 作业二（第二部分：设计，权重 30%）
> 团队：王君逸、程攀、卫梓豪、王沛璁
> 设计对象：本仓库实现的 MasteryPath（精通之路）——以知识地图为中心的掌握式学习智能体
> 基线：第一部分 SRS + 课堂反馈；设计细化后的需求见 [07-requirements-changelog.md](07-requirements-changelog.md)

## 阅读顺序

先读 [01-design-summary.md](01-design-summary.md)（约 500 词的进展与变更摘要），再按需下钻：

```
问题与目标 ──► 01 设计摘要
                 │
                 ├─► 02 架构（逻辑 / 部署 / 技术 + 质量属性）──► 04 关键决策（ADR）
                 │        │
                 │        └─► 03 智能体专项（角色 / 工具 / 记忆 / 编排 / 护栏）
                 │
                 ├─► 05 设计模型（类图 / 序列图 / 状态机 / 活动图）
                 ├─► 06 UI 与 UX（信息架构 / 用户流程 / 界面原型）
                 ├─► 07 需求细化与变更日志
                 └─► 08 未决问题与风险 ──► 第三部分 PoC 验证计划
```

## 工件 ↔ 作业要求对照

| 作业要求（§3） | 本目录文件 | 主要形式 |
| --- | --- | --- |
| 1. 设计摘要（约 500 词） | [01-design-summary.md](01-design-summary.md) | 叙述 |
| 2. 架构与关键决策 | [02-architecture.md](02-architecture.md)、[04-adr.md](04-adr.md) | C4（L1/L2/L3）、UML 部署图、质量属性场景、ADR |
| 3. 设计模型 | [05-design-models.md](05-design-models.md) | UML 类图 / 序列图 / 状态机 / 活动图、ER 图 |
| 4. 更新后的需求 | [07-requirements-changelog.md](07-requirements-changelog.md) | 需求条目 + 变更日志（改了什么、为什么） |
| 5. UI 与 UX 设计 | [06-ui-ux.md](06-ui-ux.md) | 信息架构、用户流程图、低保真线框、状态与文案 |
| 6. 未决问题与风险 | [08-risks-open-questions.md](08-risks-open-questions.md) | 风险登记册、未决决策、PoC 验证项 |
| 7. AI 工具反思 | [09-ai-reflection.md](09-ai-reflection.md) | 用法、贡献、局限与批判性评估 |
| 8. 带注释的参考文献 | [10-references.md](10-references.md) | 9 篇，每篇 200–300 字注释 |
| 9. 团队成员贡献 | [11-team-contributions.md](11-team-contributions.md) | 按分析 / 建模 / 设计 / 决策维度 |

> 要求中的"智能体系统专项关注点"（智能体角色、工具接口、记忆与状态、编排、防护栏）集中在
> [03-agentic-design.md](03-agentic-design.md)，不分散到各文件里。

## 约定

- 图表全部使用 **Mermaid**，可在 GitHub / VS Code / Typora 中直接渲染；导出 PDF 时随 Markdown 一起打印。
- 代码证据统一写成 `相对仓库根的路径:行号`，便于逐条复核。设计与实现同源：**凡是本文档断言的行为，都能在仓库里找到对应实现或验收用例**；反之，未经验证的设计写在 [08-risks-open-questions.md](08-risks-open-questions.md)。
- 本文档只描述当前仓库（Java + Next.js）这一套实现，不涉及任何其他语言版本。

## 一页速览

| 维度 | 结论 |
| --- | --- |
| 核心设计原则 | 教学决策交给模型，**能否推进交给确定性引擎**（判分 / 门槛 / 门控 / 调度零模型调用） |
| 逻辑架构 | 四层：`adapter`（传输与外部系统）→ `service`（编排与事务）→ `domain`（纯规则，零 I/O）→ `storage`（SQLite + CAS）；另有 `knowledge`（来源与检索）横切 |
| 部署架构 | 单机、本地优先：1 个 Spring Boot 进程（8080）+ 1 个 Next.js 前端（3000）+ 文件系统数据目录；外部依赖仅"可配置的模型提供方"，可整体离线 |
| 技术架构 | Java 21 / Spring Boot 3.5 / SQLite(JDBC, WAL) / Jackson / JUnit 5；Next.js 14 + React 18 + Tailwind；LLM 走 OpenAI 兼容 SSE，测试用脚本化假客户端 |
| 智能体 | 单导师智能体（Agent Loop，6 轮 / 6 次工具上限）+ 23 个工具 + 静态模式门控 + 确定性校验；记忆分四层（聚合状态 / 会话消息 / 领域事件 / 题库与知识库） |
| 最关键的三条护栏 | ①答案只存服务端、投影不含答案；②模式在**调用期**校验且分类表 fail-closed；③解析不了就"未判分"，零状态变更 |
| 关键取舍 | SM-2 而非 FSRS；SQLite 单写 + CAS 而非 PostgreSQL；WS（掌握回合）/ SSE（普通聊天）双通道而非统一协议；生成型练习卷独立成表、不写掌握分数 |
