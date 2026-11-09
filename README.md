# MasteryPath-Java

把 [MasteryPath](../MasteryPath) 的**后端**从 Python 重写为 Java（Spring Boot 3.5 + Java 21 + SQLite/JDBC）。
行为合同不变：判定标准是参考实现的 237 个验收用例与 `docs/` 中的 60 条不变量。

> 本仓库使用 **OpenSpec + SDD**。开工前必读 [`openspec/AGENTS.md`](openspec/AGENTS.md)。
> 迁移计划：[`docs/migration-plan.md`](docs/migration-plan.md)（已批准 2026-11-04）。

## 阶段看板

| Gate | 语义 | 状态 | 证据 |
| --- | --- | --- | --- |
| G0 | 工程骨架可用 | **通过**（2026-11-04） | [verify/0001](docs/verify/0001-java-skeleton.md) · 3 用例 PASS |
| G1 | 规范与纯逻辑冻结 | **通过**（2026-11-04） | [verify/0005](docs/verify/0005-mode-gating.md) · 130 用例 PASS |
| G2 | 核心业务闭环 | **通过**（2026-11-04） | [verify/0014](docs/verify/0014-mastery-nav.md) · 504 用例 PASS |
| G3 | 大纲与质量 | **通过**（2026-11-05：build/revise/缺口/覆盖 + 离线评测 CLI） | [verify/0023](docs/verify/0023-outline-eval-cli.md) · 864 用例 PASS |
| G4 | 回合与工具 | **通过**（2026-11-04） | [verify/0017](docs/verify/0017-tool-layer.md) · 601 用例 PASS |
| G5 | 协议兼容 | **通过**（2026-11-05） | [verify/0019](docs/verify/0019-ws-turn.md) · 690 用例 PASS |
| G6 | 知识库与设置 | **通过**（2026-11-05：聊天 + 知识库 + 设置落盘 + 提供方探测 + Task Model） | [verify/0022](docs/verify/0022-settings-task-model.md) · 835 用例 PASS |
| G7 | 验收 | **通过**（2026-11-05：全量 864 · 旧库双向可读 · 前端联调 · 全部 change 归档） | [verify/0024](docs/verify/0024-acceptance.md) · 864 用例 PASS |

## 快速开始

### 0. 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21+（实测 Temurin 21.0.11） | 含 `javac`；**不需要预装 Maven**，仓库自带 wrapper |
| Node.js | 18.17+（实测 24.18.0） | 仅前端需要 |
| Ollama（可选） | — | 知识库检索需要嵌入模型，如 `ollama pull bge-m3` |

### 1. 启动后端（Java）

```powershell
# 项目根目录
.\mvnw.cmd spring-boot:run
```

启动后监听 `http://127.0.0.1:8080`，数据目录为 `data/`。

首次运行没有 JDK 环境变量时，`verify.ps1` 会自动回退到一个可用 JDK；手动启动请先确认：

```powershell
java -version                                   # 需要 21+
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'   # 若 JAVA_HOME 指向已删除目录（仅当前窗口）
```

若在 **cmd.exe** 里（提示符是 `D:\...>`），语法不同，且**值不要加引号**：

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
.\mvnw.cmd spring-boot:run
```

彻底修掉这个坑（用户级环境变量，新开的窗口都生效）：

```bat
setx JAVA_HOME "C:\Program Files\Java\jdk-21.0.11"
```

`setx` 只影响**新**进程，当前窗口仍需 `set` / `$env:` 一次；设置后可删掉指向 VS Code 扩展 JRE 的旧值。

换端口 / 换数据目录（用环境变量，避免 PowerShell 下 `-D` 参数被吞）：

```powershell
$env:SERVER_PORT='8081'
$env:MASTERYPATH_DATADIR='tmpdata'
.\mvnw.cmd spring-boot:run
```

打包成可执行 jar：

```powershell
.\mvnw.cmd -q -DskipTests package
java -jar target\masterypath-0.1.0-SNAPSHOT.jar
```

连通性自检（`200` 即后端可用）：

```powershell
curl.exe -s -o NUL -w "%{http_code}`n" http://127.0.0.1:8080/learning/paths
```

### 2. 配置模型

对话回合、大纲生成、建议下一问都需要一个可用的 LLM；**判分、门槛、门控、复习调度不调用 LLM**（`INV-32`），所以先跑通「出题 → 判分」不配模型也能用。

两种方式任选其一：

- **界面配置（推荐）**：启动前端后进入 `/settings/connections`，填 `base_url` + `api_key` → 「测试连接」→ 添加模型 → 在 `/settings/llm` 设为 active。
- **直接编辑** `data/settings.json`：`providers[]` 的 `base_url` / `api_key` / `protocol`，`llm.active_id` 指向 `llm.models[]` 里的某个 id；嵌入模型在 `embedding.models[]`（如 Ollama `bge-m3:latest`，1024 维）。

> `data/settings.json` 里的 Key 是明文，已在 `.gitignore` 中，不要提交。

#### 不配 Key：本地 Mock LLM（仅开发）

`scripts/mock-llm/` 是一个 OpenAI 兼容的假模型服务（Node 内置模块，零依赖），
用来不花钱、可重复地体验 agent：流式回答、建大纲、出题都能跑通。

```powershell
# 终端 A：假模型服务（默认 127.0.0.1:9099）
node scripts/mock-llm/mock-llm.mjs

# 终端 B：后端
.\mvnw.cmd spring-boot:run
```

然后在 `/settings/connections` 新增提供方：`base_url = http://127.0.0.1:9099/v1`、
`api_key = mock`（任意非空字符串）、协议 `openai`；测试连接后可拉到模型 `mock-tutor`，
在 `/settings/llm` 设为 active。

剧本是确定性的：

| 你说 | 结果 |
| --- | --- |
| `outline` 模式下「帮我规划大纲」 | 调用 `mastery_build`，生成 2 模块 4 知识点 |
| `study` 模式下「出题」 | 调用 `mastery_quiz`，弹出一道选择题卡片 |
| 「列出我的主题」 | 调用 `mastery_topics`（普通聊天链路） |
| 其他任何话 | 固定的 Markdown 流式回复 |

每次请求会在终端 A 打印一行 `[mock-llm] ... mode=... -> tool:mastery_build`，便于观察 agent 到底做了什么。
它只在 `tools` 里真实存在该工具时才调用，所以模式门控的拒绝行为仍然是真实的。

> 该服务只用于本地开发：自动化测试继续使用离线的 `FakeLlmClient`，不依赖它。

### 3. 启动前端（Next.js 14，位于本仓库 `web/`）

```powershell
cd web
npm ci
$env:NEXT_PUBLIC_API_BASE='http://127.0.0.1:8080'
npm run dev
```

若在 **cmd.exe**（提示符 `D:\...>`）里，同样的步骤换个写法，值不加引号：

```bat
cd /d web
npm ci
set NEXT_PUBLIC_API_BASE=http://127.0.0.1:8080
npm run dev
```

> 命令后不要跟 `# 注释`：cmd 不认 `#`，`npm install # 首次` 会把 `#` 当成包名报 `EINVALIDTAGNAME`。

`NEXT_PUBLIC_API_BASE` 默认是 `http://127.0.0.1:8001`（参考实现 Python 后端）；指向 Java 后端必须显式设置，且**设置后再启动 `npm run dev`**（Next 在启动时内联该变量，运行中修改无效）。WebSocket 地址由此变量自动派生（`http` → `ws`）。

主要入口：

| 页面 | 路径 | 依赖 |
| --- | --- | --- |
| 学习图集 / 仪表盘 | `/learning/mastery`、`/learning/mastery/{pathId}` | `GET /learning/paths` |
| 学习工作区（WS 回合） | `/learning/mastery/{pathId}/sessions` | `WS /ws` + 判分 REST |
| 普通聊天（SSE） | `/chat` | `POST /chat/sessions/{id}/turns` |
| 设置中心 | `/settings/connections`、`/settings/llm`、`/settings/embedding` | `GET/PUT /settings/*` |
| 知识库 | `/knowledge-bases` | `/knowledge-bases/**` |
| 学习空间 | `/space` | `GET /questions` |

前端已由 `0027-web-into-repo` 并入本仓库 `web/`（源码来自 `../MasteryPath/web`）。它仍作为「契约消费者」反向验证后端协议等价：一旦需要改前端才能跑通，就说明协议不等价，应记为缺陷。

### 4. 数据落在哪

| 路径 | 内容 | 说明 |
| --- | --- | --- |
| `data/learning.db` | 路径、事件、题库、会话 | 表结构与参考实现 DDL 逐字一致，可被 Python 版直接打开 |
| `data/chat.db` | 聊天会话与消息 | 与 `learning.db` 同目录但不共库 |
| `data/settings.json` | 提供方 / 模型 / 语言设置 | 含明文 Key |
| `data/knowledge_bases/` | 知识库文档与向量索引 | 删除知识库会一并清理 |

清空学习数据：停掉后端后删除对应文件即可（`settings.json` 在首次保存设置时会重新生成默认值）。

### 5. 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| `Cannot find java` / `JAVA_HOME is set to an invalid directory` | 用户级 `JAVA_HOME` 指向已删除的 VS Code 扩展 JRE。PowerShell 用 `$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.11'`，**cmd 用 `set JAVA_HOME=...`（不加引号）**，持久修复用 `setx` |
| 端口被占用 | `$env:SERVER_PORT='8081'` 后重启；前端同步改 `NEXT_PUBLIC_API_BASE` |
| 前端页面空白、请求打到 8001 | 未设 `NEXT_PUBLIC_API_BASE`，或设置后没重启 `npm run dev` |
| PowerShell 里中文变 `?` / 乱码 | PS 5.1 的 `Invoke-RestMethod` 会按系统代码页收发；改用 `curl.exe` 落文件，再用 `[System.IO.File]::ReadAllText($p, [Text.Encoding]::UTF8)` 读 |
| `verify.ps1 --change xxx` 报参数错误 | Windows PowerShell 不识别 `--change`，用 `-Change xxx` |
| 聊天回复为空 / 报模型错误 | `data/settings.json` 未配置 active 模型，或 Key 无效（可在 `/settings/connections` 测试连接） |
| 知识库状态 `rag_prerequisite_missing` | 未配置嵌入模型；本地起 Ollama 并 `ollama pull bge-m3`，再在设置里选中它 |

## 目录

```
openspec/                  规范与变更的单一真实源
  config.yaml              语言与 schema
  project.md               目标 / 边界 / 技术栈 / 工程标准
  AGENTS.md                AI 操作规程（人机契约）
  specs/                   基线规范（实现阶段只读）
  changes/                 增量变更沙箱（当前无活跃 change）
    archive/               已归档的 change（2026-11-05 归档 0001–0025）
docs/
  migration-plan.md        迁移计划（范围论证 / 架构映射 / Gate / change 清单）
  traceability.md          验收场景 ↔ 规范 ↔ Java 测试
  reference-env.md         参考环境锁定表
  regression-log.md        回归基线（只增不改）
  verification-debt.md     验证债台账
  verify/                  每个 change 的验证证据
  ai-collaboration/        AI 协作留痕
scripts/verify.ps1         唯一验证入口
scripts/mock-llm/          本地假模型服务（开发联调，零依赖）
src/main/java/com/masterypath/
  domain/                  模型 + 纯策略 + 判分 + 调度 + 门控（零 I/O）
  storage/                 SQLite + CAS + 事件
  service/                 业务编排
  adapter/                 LLM / Loop / TurnRunner / REST / WS / 工具
  cli/                     命令行入口（大纲评测）
```

## 命令

```powershell
.\mvnw.cmd -q -DskipTests compile          # 编译
.\mvnw.cmd -q test                          # 全量测试
.\mvnw.cmd spring-boot:run                  # 启动后端（见「快速开始」）
.\scripts\verify.ps1 -Change <change-id>    # 唯一验证入口（产出证据）
```

## 红线

1. 不扩大范围：顺手优化另开 change。
2. 不跳步：Plan → Check → Build → Verify → Merge。
3. 不伪造证据：`NOT RUN` 就是 `NOT RUN`，进 `docs/verification-debt.md`。
4. 不放宽断言：失败要么修实现，要么证明测试写错。
5. 不绕过变更改基线：行为变化走增量规范，归档时合并。
