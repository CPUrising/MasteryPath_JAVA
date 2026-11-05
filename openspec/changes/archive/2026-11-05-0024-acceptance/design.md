# Design: 验收

## Context

| 维度 | 已有证据 | 缺口 |
| --- | --- | --- |
| 纯逻辑等价 | 864 项 JUnit | 无 |
| 数据等价 | `ReferenceWorkspaceCompatibilityTest`（fixture 库 → Java 读 + 续写 + DDL 比对） | 真实运行库；Java 建库 → Python 读 |
| 协议等价 | REST/WS 单测 + standalone `MockMvc` + `RANDOM_PORT` WS 测试 | 真实浏览器前端驱动 |

真实旧库现状（`../MasteryPath/data/learning.db`）：
5 条路径（`pmveodk71` v3 / `pmvf72enn` v2 / `pmvhhpxrl` v109 / `pmvhpxp9o` v6 / `pmvi0ub7m` v4）、
125 条事件、8 个会话、0 条题库、0 条交互。

## Goals / Non-Goals

**Goals**

1. 三个方向的读取都留下可复现证据：**旧库 → Java**、**Java 库 → Python**、**浏览器 → Java 后端**。
2. 验收过程**不污染**参考实现的 `data/`（只读复制到临时目录）。
3. 任何失败都按 `AGENTS.md` §5 处理：最多自愈 3 轮，禁止放宽断言。

**Non-Goals**

1. 不改前端代码，也不把前端纳入本仓库。
2. 不提交参考实现的 `data/`（含明文 Key）。
3. 不做真实付费模型的批量评测；只做一次单回合联调。

## Decisions

1. **旧库验证用「复制到临时目录再打开」**
   - 直接打开会让 Java 的初始化 DDL 与 `schema` 表写入污染参考数据。
   - 复制后无论是读还是续写都不影响原始库。

2. **对比项选「结构性事实」而非全量 JSON**
   - 路径数、路径 id 集合、`version`、`counts`、模块/KP 数量、事件条数。
   - 全量 JSON 对比会因字段顺序与浮点表示产生噪声，反而不稳。

3. **反向验证用一次性 Python 脚本，不落库**
   - 脚本放在临时目录，用完即删；结论写进 `docs/verify/0024-acceptance.md`。
   - 理由：让 JUnit 依赖外部 venv 会让 CI 不可用；`AGENTS.md` 要求证据而非测试形式。

4. **反向验证的内容是「结构 + 语义」两段**
   - 结构：`LearningStore` 能 `list_paths()` 且 `load()` 不抛错（Pydantic 校验通过）。
   - 语义：Java 写入的 `explained_objectives` / `modules` / `events` 在 Python 侧字段名与值一致。

5. **前端只通过 `NEXT_PUBLIC_API_BASE` 指向 Java**
   - 参考实现默认 `http://127.0.0.1:8001`；改为 `http://127.0.0.1:8080`。
   - 不改任何 `.tsx`；一旦需要改前端才能跑通，就说明协议不等价，必须记缺陷。

6. **联调的最小面覆盖四条主链路**
   - 路径列表 → 路径详情（`GET /learning/paths`、`GET /learning/paths/{id}`）
   - 设置中心（`GET /settings` 与 `GET /settings/providers/catalog`）
   - 知识库（`GET /knowledge-bases`）
   - 一次真实 WS/SSE 回合（`chat` 页），确认能收到 `turn_end`

7. **G7 清单以「项 → 证据」两列呈现**
   - 每项必须指向本报告的某一节或某条日志，禁止「已人工确认」这种不可核验的表述。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| Java 初始化 DDL 污染旧库 | 参考数据不可用 | 复制到临时目录；原库只读 |
| 前端默认端口 8001 | 连到参考后端，联调假通过 | 显式设置 `NEXT_PUBLIC_API_BASE=8080` 并在报告里记录 |
| `npm install` 失败影响结论 | 无法联调 | 先检查 `web/node_modules` 是否已在；不在则记录为阻塞而非通过 |
| 联调消耗真实模型额度 | 费用 | 只跑一次单回合 |
| 反向脚本依赖 venv 路径 | 换机器不可复现 | 报告里写明命令与 venv 路径 |

## Migration Plan

1. 复制旧库到临时目录 → 起 Java（`masterypath.data-dir` 指向临时目录）→ 对比结构事实。
2. Java 建库（独立临时目录）→ Python 读 → 对照字段。
3. 起 Java（真实 `data/`）+ 前端（`NEXT_PUBLIC_API_BASE=8080`）→ 浏览器逐页验证。
4. 产出报告 → 更新 README 的 G7 → 归档 `0024`。

## Open Questions

- 无。G7 清单沿用 `docs/migration-plan.md` §4。
