# 验证报告：0025-manual-verification

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0025-manual-verification` |
| 执行时间 | 2026-11-05 15:55–16:05（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | JDK 21.0.11 / Maven Wrapper 3.9.16 / Spring Boot 3.5.16 |
| 运行环境 | 本地（后端 `--server.port=8080`，`masterypath.data-dir=data`） |
| 命令 | `.\mvnw.cmd spring-boot:run` + `curl.exe`（逐项见 §2、§3） |
| 回归命令 | `.\scripts\verify.ps1 -Change 0025-manual-verification` |
| 回归日志 | `artifacts/verify-0025-manual-verification-20261105-160348.log` |

> 本 change 只产出证据，**不改任何生产代码与测试代码**。原始观测文件在 `artifacts/verify25/`（gitignored）。

## 2. 真实 provider：流式与 function calling

**配置**：`data/settings.json` 的 `llm.active_id = m-mvenwsrt-0`
→ provider `https://opencode.ai/zen/go/v1`，model `deepseek-v4.1-flash`（Key 不记录）。

**会话**：`POST /chat/sessions` → `session_id = c2df92cbc8f4a`（标题 `smoke-0025`）。

### 2.1 流式文本

请求：`POST /chat/sessions/c2df92cbc8f4a/turns`，`{"message":"请只回复四个字：收到。"}`

原始帧（`artifacts/verify25/turn_text.sse`，2 帧增量 + 1 帧结束）：

```text
data: {"type":"text_delta","data":{"text":"收到"}}
data: {"type":"text_delta","data":{"text":"。"}}
data: {"type":"turn_end","data":{"reason":"done","session_id":"c2df92cbc8f4a","title":"smoke-0025"}}
```

判定：**PASS** —— 内容分两帧到达（说明服务端未攒批），拼接结果为「收到。」。

### 2.2 function calling（两轮）

请求：`{"message":"请调用工具列出我所有的学习主题，然后告诉我结果。"}`

帧序列（`artifacts/verify25/turn_tool.sse`，共 32 帧 `text_delta`）：

| 顺序 | 帧类型 | 观测 |
|---|---|---|
| 1 | `tool_call` | `name=mastery_topics`，`arguments={}` |
| 2 | `tool_result` | `ok=true`，`content` 含 1 个主题 `p1 / Java 并发` |
| 3–34 | `text_delta` | 基于工具结果的中文回复，逐帧到达 |
| 35 | `turn_end` | `reason=done` |

判定：**PASS** —— 真实响应里的 `tool_calls` 增量分片被正确归并（工具名与参数无残缺），
`finish_reason=tool_calls` 后完成了「执行工具 → 回传结果 → 第二轮生成」的完整链路。

落库校验：`GET /chat/sessions/c2df92cbc8f4a` 返回 4 条消息（2 轮 user/assistant），
中文与换行完整，`created_at` 递增。

### 2.3 错误分支（401）

请求：`POST /settings/providers/probe`，body 显式传错误 Key（`sk-invalid-0025`，不改 `settings.json`）。

响应：`{"status":"auth_error","models":[],"http_status":401}`

判定：**PASS** —— 401 映射到 `auth_error`，无未捕获异常、无 500。

## 3. 知识库索引性能

**数据集**（合成中文文本，固定内容，脚本 `artifacts/perf/gen_dataset.ps1`）：
`bulk/` 200 个 md 共 5,441,692 B（5.19 MB）；`single/` 1 个 md 共 1,069,526 B（1.02 MB）。

**嵌入**：本地 Ollama `bge-m3:latest`（1024 维），`chunk_size=512`、`chunk_overlap=64`（默认）。

**判据（开跑前约定，未调整）**：`1 MB 文本 < 60 s`、进度单调递增、无 OOM、失败文件数为 0。

| 场景 | 文件 | 总字节 | 分块 | 耗时 | 峰值堆 | 结果 |
|---|---|---|---|---|---|---|
| 预热（吸收模型冷启动） | 1 | 27,207 | 25 | 9.23 s | 47.8 MB | ready |
| 单文件 1 MB | 1 | 1,069,526 | 983 | **29.42 s** | 178.3 MB | ready |
| 多文件 5 MB | 200 | 5,441,692 | 5,000 | 143.42 s | 360.8 MB→986.2 MB | ready |

补充观测：

- 吞吐稳定：单文件 33.4 块/s、多文件 34.9 块/s（两者一致，说明分文件摊薄开销可忽略）。
- `indexed_files == total_files`（1/1、200/200），`error` 为空，失败文件数 **0**。
- 三次的 `progress_monotonic` 均为 `true`；多文件场景 `percent` 从 0 逐级升到 1.0。
- 预热耗时 9.23 s（25 块），明显高于稳态（约 0.03 s/块 → 0.75 s），差额即 bge-m3 首次加载。

判定：

| 判据 | 结论 |
|---|---|
| 1 MB 文本 < 60 s | **PASS**（29.42 s） |
| 进度单调递增 | **PASS**（3/3） |
| 无 OOM | **PASS**（最大采样 986.2 MB，进程未退出） |
| 失败文件数为 0 | **PASS** |

## 4. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量单元测试 | `.\scripts\verify.ps1 -Change 0025-manual-verification` | **864 / 0 失败 / 0 跳过**（BUILD SUCCESS） |
| 代码改动 | — | 生产代码与测试代码均未改动（0 行 diff） |

测试用的三个知识库（`perf-warm` / `perf-single-1mb` / `perf-bulk-5mb`）在测量后已删除（HTTP 204），
`data/knowledge-bases/` 未残留合成数据。

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| O1 | 5 MB 场景最后一次采样堆用量跳到 986.2 MB（索引写盘阶段），此前稳态峰值为 360.8 MB | 一般 | 未 OOM，未调参；记入观察项，若后续要支持更大库再评估流式落盘 |
| O2 | 进度只在「文件级」更新：单文件场景从 0 直接跳到 100% | 一般 | 与参考实现一致（`indexed_files/total_files`），不视为缺陷 |
| O3 | 真实 provider 冒烟消耗 3 次请求（约 1k token） | 一般 | 一次性手工验证，未固化为测试（`AGENTS.md` §6） |

无阻断级与严重级偏差；未发现需要修改生产代码的缺陷。

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

证据文件：`artifacts/verify25/turn_text.sse`、`turn_tool.sse`、`session_detail.json`、
`probe_badkey.json`、`perf_warm.json`、`perf_single.json`、`perf_bulk.json`（均 gitignored）。
