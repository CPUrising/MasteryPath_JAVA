# Design: 手工验证（真实 provider + 索引性能）

## Context

| 待验证项 | 已有证据 | 缺口 |
| --- | --- | --- |
| 流式解析 | `SseParserTest`（31 用例，含本地 `HttpServer`） | 真实供应商的分片边界与心跳行 |
| function calling | `AgentLoopTest` + `FakeLlmClient`（两轮工具回传） | 真实 `tool_calls` 增量归并 |
| HTTP 错误映射 | `OpenAiCompatibleClientTest` 6 分支 | 真实 401 响应体形状 |
| 知识库索引 | `VectorIndexerTest`（分块/进度/余弦） | 真实规模下的耗时与内存 |

真实配置（`data/settings.json`，用户提供）：

| provider | base_url | Key 长度 | 可用模型 |
| --- | --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | 35 | `deepseek-flash`、`deepseek-v4-pro` |
| OpenCode Zen | `https://opencode.ai/zen/go/v1` | 51 | 29 个（含嵌入） |
| Ollama | `http://127.0.0.1:11434/v1` | 空 | `bge-m3:latest`（1024 维） |

## Goals / Non-Goals

**Goals**

1. 用真实响应验证三条链路：流式文本、`tool_calls` 双轮、401 映射。
2. 用可复现的数据集给出索引耗时与内存的**原始数值**，并对照事先约定的判据。
3. 失败照实记 `执行失败`，不写「应该」「推测」。

**Non-Goals**

1. 不做批量评测、不做多供应商横向对比。
2. 不把冒烟固化成自动化用例（会违反 `AGENTS.md` §6，且引入费用与不确定性）。
3. 不根据性能数据改任何阈值或默认值。

## Decisions

1. **真实 provider 冒烟是手工验证，不进 `src/test/**`**
   - `AGENTS.md` §6 明确禁止用真实付费模型跑测试。本项是一次性冒烟，
     与 `0024` 的单回合联调同类；证据写进 `docs/verify/`，由人复核。
   - 调用预算：≤ 5 次请求、总 token < 10k，用最小的提示词。

2. **触发工具调用用「必然命中」的自然语言，而不是伪造 `tool_choice`**
   - 提示词选「列出我所有的学习主题」，`mastery_topics` 是唯一相关工具，命中率高。
   - 若模型选择不调工具，**记录为未命中并重试一次**，不修改产品代码强行触发。

3. **观测点在客户端逐帧打印，不依赖服务端日志**
   - 逐帧记录 `id/object/choices[].delta`，检查 `tool_calls[].index` 是否从 0 递增、
     后续分片是否只含 `arguments` 片段。
   - 理由：要验证的是「我们怎么解析真实响应」，服务端日志已经过解析。

4. **401 分支用「同一 provider + 故意改错一个字符的 Key」**
   - 不改 `settings.json`（会被后续步骤污染），在请求体里传显式 Key。
   - 预期：返回 `auth_error` 且不抛出未捕获异常。

5. **性能数据集用合成中文文本，固定随机种子**
   - 200 个文件 × 约 25 KB ≈ 5 MB，覆盖「多文件」路径；另加 1 个 1 MB 单文件覆盖单文件路径。
   - 合成数据避免版权与隐私问题，且可重跑。

6. **嵌入走本地 Ollama `bge-m3`，并先预热**
   - 首次加载模型是冷启动，混进计时会失真；先索引一个小文件预热，再正式计时。
   - 零成本，且与默认配置（`embedding.active_id` 指向 `bge-m3`）一致。

7. **判据在开跑前写进 `tasks.md`，事后不调整**
   - `1 MB 文本 < 60 s`、进度单调递增、无 OOM、失败文件数为 0。
   - 若超时，结论记「执行失败（超判据）」，不改判据迁就结果。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 模型不调用工具 | function calling 未被验证 | 换更明确的提示重试一次；仍失败则记「未命中」而非「通过」 |
| 供应商限流 / 余额不足 | 冒烟中断 | 记 `执行失败`，保留响应码与响应体（脱敏），不反复重试 |
| Ollama 未启动 | 性能项无法执行 | 先 `GET /api/tags` 探活；不通则记 `阻塞` 并说明 |
| 5 MB 数据把索引拖过久 | 验证耗时 | 记录真实耗时；若 > 10 min 主动中止并记「超时中止」 |
| 误把 Key 写进证据 | 泄露 | 证据里 Key 一律写成 `sk-****` + 长度 |

## Migration Plan

1. 起后端（`--server.port=8080`），确认 `data/settings.json` 被加载。
2. 真实 provider：流式文本 → 工具调用双轮 → 401 分支，逐项记录。
3. 造数据集 → 预热 → 索引计时 → 记录分块/嵌入/内存。
4. 产出 `docs/verify/0025-manual-verification.md` → 回填台账 → 全量回归。

## Open Questions

- 无。
