# Tasks

> 判据（开跑前约定，事后不调整）：
> 真实 provider —— 逐帧 `delta` 可拼接、`tool_calls[].index` 从 0 递增且后续分片只含
> `arguments`、`finish_reason=tool_calls` 后能完成第二轮、401 → `auth_error`；
> 索引性能 —— `1 MB 文本 < 60 s`、进度单调递增、无 OOM、失败文件数为 0。

## 1. 真实 provider（手工冒烟，不进 `src/test/**`）

- [x] 1.1 起后端 8080，确认 `data/settings.json` 生效（`GET /settings/providers` 返回 3 条）
- [x] 1.2 流式文本：一次普通提问，逐帧记录 `delta.content` 并拼成完整回复（2 帧 → 「收到。」）
- [x] 1.3 function calling：一次「必然触发工具」的提问，验证 `tool_calls` 增量归并 + 第二轮回传 + 最终文本
      （`mastery_topics` 真实调用，tool_call → tool_result → 32 帧 text_delta → turn_end）
- [x] 1.4 错误分支：显式错误 Key → 记录 HTTP 状态与映射结果（`{"status":"auth_error","http_status":401}`）
- [x] 1.5 结论写进 `docs/verify/0025-manual-verification.md` §1（含脱敏后的帧摘要）

## 2. 知识库索引性能

- [x] 2.1 生成合成数据集：200 个中文 markdown 共 5.19 MB + 1 个 1.02 MB 单文件（`artifacts/perf/`，gitignored）
- [x] 2.2 探活 Ollama `bge-m3`；用小文件预热一次（27 KB / 25 块 / 9.23 s）
- [x] 2.3 正式索引：单文件 29.42 s / 983 块 / 峰值 178.3 MB；多文件 143.42 s / 5000 块 / 峰值 986.2 MB
- [x] 2.4 对照判据给出结论：4 条判据全部 PASS，写进 `docs/verify/0025-manual-verification.md` §3

## 3. 证据与回填

- [x] 3.1 产出 `docs/verify/0025-manual-verification.md`
- [x] 3.2 回填 `docs/verification-debt.md`：第 66 行结案、第 72 行过期项关闭、新增性能行
- [x] 3.3 追加 `docs/regression-log.md` 与 `docs/ai-collaboration/0025-manual-verification.md`
- [x] 3.4 全量回归 `.\scripts\verify.ps1`：864 / 0 失败，无代码改动导致回归
