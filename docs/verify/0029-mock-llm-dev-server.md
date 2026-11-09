# 验证报告：0029-mock-llm-dev-server

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0029-mock-llm-dev-server` |
| 执行时间 | 2026-11-09 19:20–19:27（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | JDK 21.0.11 / Maven Wrapper 3.9.16 / Spring Boot 3.5.16 / Node 24.18.0 |
| 运行环境 | 本地（后端 `--server.port=8099`，数据目录 `artifacts/verify29/workspace`，gitignored） |
| 回归命令 | `.\scripts\verify.ps1 -Change 0029-mock-llm-dev-server` |
| 回归日志 | `artifacts/verify-0029-mock-llm-dev-server-*.log` |

> 本 change 不改生产代码；证据由 `scripts/mock-llm/mock-llm.mjs` + 本地后端实测产出，
> 原始文件在 `artifacts/verify29/`（gitignored）。

## 2. 供应商端点契约

`GET http://127.0.0.1:9099/v1/models`（`models.json`）：

```json
{"object":"list","data":[{"id":"mock-tutor","object":"model","owned_by":"mock-llm"}]}
```

判定：**PASS** —— 设置页「测试连接 / 获取模型」可直接使用。

## 3. 端到端（真实后端 → Mock LLM）

`data/settings.json` 指向 `http://127.0.0.1:9099/v1`，模型 `mock-tutor`。

### 3.1 普通聊天（SSE，工具回合）

`POST /chat/sessions` → `session_id=ca2fb363c7936`；
`POST /chat/sessions/{id}/turns`，消息 `list my topics`（`turn_topics.sse`）。

帧序列：

| 顺序 | 类型 | 观测 |
|---|---|---|
| 1–2 | `text_delta` | 「好的，马上处理。」分两帧 |
| 3 | `tool_call` | `name=mastery_topics`，`arguments={}` |
| 4 | `tool_result` | `ok=true`，`total_topics=0` |
| 5–40 | `text_delta` | 基于工具结果的 Markdown 收尾，逐帧到达 |
| 41 | `turn_end` | `reason=done` |

判定：**PASS** —— 假模型数据经 `OpenAiCompatibleClient` + `SseParser` 完整走通。

### 3.2 掌握式导师（WS，建大纲）

路径 `mockdemo`，会话 `s-mock`，模式 `outline`；`WS /ws` 发送「帮我规划大纲」
（`turn_outline_ws.jsonl`，共 **86 帧**）。

| 状态 | 观测 |
|---|---|
| `tool_call` | `name=mastery_build`，2 模块 / 4 知识点（memory、concept、procedure、design 各一） |
| `tool_result` | `ok=true`；`Built outline with 2 module(s)`；状态快照含 `k1..k4` |
| 结尾 | 多帧 `text_delta` + `turn_end reason=done` |

判定：**PASS** —— 地图真的落库（`GET /learning/paths/mockdemo` 返回 4 个知识点）。

### 3.3 掌握式导师（WS，出题）

`PUT .../mode` 切到 `study` 后发送「出题」（`turn_quiz_ws.jsonl`，共 6 帧）。

| 顺序 | 类型 | 观测 |
|---|---|---|
| 1–2 | `text_delta` | 过渡语 |
| 3 | `tool_call` | `mastery_quiz`，`question_type=choice`，4 个选项 |
| 4 | `tool_result` | `ok=true`，`question_id=f6c37b5c…` |
| 5 | `question_card` | 题卡投影（题干、选项、难度） |
| 6 | `turn_end` | `reason=card` |

判定：**PASS** —— 与 `0028` 的题卡渲染入口完全一致。

### 3.4 服务端可观测性

Mock 服务终端输出（节选）：

```text
[mock-llm] ... mode=study   last=user            -> tool:mastery_topics user="list my topics"
[mock-llm] ... mode=outline last=user            -> tool:mastery_build  user="帮我规划大纲"
[mock-llm] ... mode=study   last=user            -> tool:mastery_quiz   user="出题"
```

判定：**PASS** —— 每次请求一行，能直接看出「命中哪个剧本」。

## 4. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量单元测试 | `.\scripts\verify.ps1 -Change 0029-mock-llm-dev-server` | **868 / 0 失败 / 0 跳过** |
| 生产代码改动 | `git status --porcelain -- src/main` | 无输出 |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 剧本关键词以中文为主（`outline` / `plan` / `quiz` 等少量英文兜底） | 一般 | 已在 README 与 design 中写明 |
| D2 | 题目固定绑定 `k1`，不解析真实知识点 id | 一般 | 演示用途；在 mock 自己建出的路径上有效 |
| D3 | 端到端用的 `settings.json` 位于 `artifacts/`（gitignored），未入库 | 一般 | 生产中按 README 在设置页配置 |
| D4 | 未验证前端页面（只到 WS/SSE 协议层） | 一般 | 前端消费的事件名与字段与既有契约一致；页面级验证留给后续 change |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策
