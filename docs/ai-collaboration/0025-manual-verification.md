# AI 协作留痕：0025-manual-verification

## 1. prompt 范围

- 授权修改的文件：
  - `docs/verify/0025-manual-verification.md`（新增）
  - `docs/ai-collaboration/0025-manual-verification.md`（新增）
  - `docs/verification-debt.md`、`docs/regression-log.md`
  - `openspec/changes/0025-manual-verification/**`
- 明确不做的事：
  - 不改 `src/main/**`、`src/test/**`（本 change 只产出证据）
  - 不把真实 provider 冒烟写成 JUnit 用例（`AGENTS.md` §6 禁止真实付费模型跑测试）
  - 不提交 Key、真实响应全文、性能数据集
- 出处：用户指令「如何去做？」→ 选择「开 0025 + 做真实 provider」「同时做知识库索引耗时」。

## 2. AI 产出

- 真实 provider（3 次请求）：
  - 流式文本：2 帧 `text_delta` + `turn_end`
  - function calling：`mastery_topics` 被真实调用 → 结果回传 → 32 帧增量文本 → `turn_end`
  - 401：错误 Key → `{"status":"auth_error","http_status":401}`
- 索引性能（3 次索引，本地 Ollama）：
  - 预热 27 KB / 25 块 / 9.23 s
  - 1.02 MB / 983 块 / 29.42 s / 峰值堆 178.3 MB
  - 5.19 MB / 200 文件 / 5000 块 / 143.42 s / 峰值堆 986.2 MB（写盘阶段）
- 辅助脚本（`artifacts/`，gitignored）：
  - `perf/gen_dataset.ps1`（合成中文语料，固定内容）
  - `perf/measure_kb.ps1`（建库 → 导入 → 轮询进度 → 采样 `jcmd GC.heap_info` → 落 JSON）

## 3. 人工修正点

AI 自我修正 2 轮，均为**脚本缺陷**，不涉及实现：

1. `measure_kb.ps1` 首版用 `used\s+(\d+)M` 解析 `jcmd`，实际输出单位是 `K`（`used 34223K`），
   导致 `heap_used_mb` 全为 `null`。改为先匹配 `garbage-first heap` 行再兼容 K/M 两种单位。
2. 预热数据集复制路径写成 `doc-001.md`，实际生成名是 `doc001.md`，导致预热库 `empty`。
   修正后重跑；第一次的错误库已删除（204）。

未出现「放宽判据」的情况：1 MB < 60 s 的阈值写在 `tasks.md` 里，实测 29.42 s 通过，
多文件场景即使 143 s 也未改判据（该场景本就不在阈值约束内，只作规模参考）。

## 4. 回归结果

- 用例数 / 失败数：全量 **864 / 0**（无新增用例，无代码改动）
- 证据链接：[`docs/verify/0025-manual-verification.md`](../verify/0025-manual-verification.md)
- 日志：`artifacts/verify-0025-manual-verification-20261105-160348.log`

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 真实付费模型能否用来验证 | **能，但只能手工一次性** | `AGENTS.md` §6 约束的是「测试」；本项不进 `src/test/**`、不参与 CI 判定，且与 `0024` 单回合联调同类 |
| 是否用 `tool_choice` 强制调工具 | **不用** | 强制会掩盖「模型自主选择」这一真实路径；改为「必然命中」的自然语言，未命中则重试一次 |
| 401 分支是否改 `settings.json` | **不改** | 会污染后续步骤；probe 端点支持显式 Key，足以覆盖同一映射分支 |
| 性能判据 60 s 是否事后调整 | **不调整** | 判据先写入 `tasks.md`；实测通过，无调整动机 |
| 986 MB 峰值是否算缺陷 | **不算** | 未 OOM、未失败，只是写盘阶段的一次分配尖峰，记 O1 观察项 |
| 合成数据是否可提交 | **不提交** | 5 MB 数据无证据价值；脚本可复现，原始 JSON 在 `artifacts/` |
| 测量后是否保留测试库 | **删除** | 避免把合成数据留在用户的 `data/knowledge-bases/` |
