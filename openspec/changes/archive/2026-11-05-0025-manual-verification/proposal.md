# Proposal: 手工验证（真实 provider + 索引性能）

## Why

`0024` 关闭了 G7 的自动化门槛，但 `docs/verification-debt.md` 里仍有两项
「写了但没在真实环境跑过」的验证：

1. **真实 provider 的流式与 function calling**（台账状态：`阻塞`）。
   `OpenAiCompatibleClientTest` 用 JDK `HttpServer` 覆盖了 SSE 解析与 6 个 HTTP 错误分支，
   但那是**我们按自己的理解伪造的响应**。真实供应商的 `tool_calls` 增量分片、
   `finish_reason=tool_calls` 之后的多轮回传，从未被真实响应验证过。
2. **知识库在真实大文档上的索引耗时**（台账未登记）。
   `VectorIndexerTest` 只证明了「分块正确、检索正确」，没有任何耗时与内存数据，
   无法回答「5 MB 文档能不能用」。

这两项都不影响「行为等价」结论，但属于必须显式记账的验证债，不能以「应该没问题」结案。

## What Changes

- 真实 provider 冒烟（手工，非 CI）：
  - 流式增量拼接（`delta.content` 逐帧累积）
  - `tool_calls` 增量按 `index` 归并 → 工具执行 → 第二轮回传 → 最终文本
  - 反向分支：错误 Key → 401 映射到 `auth_error`
- 知识库索引性能实测：
  - 合成数据集（200 个中文 markdown，约 5 MB）+ 1 个 1 MB 单文件
  - 记录文件数、总字节、分块数、嵌入调用次数、总耗时、峰值堆内存
  - 判据事先约定：`1 MB 文本 < 60 s`、进度单调递增、无 OOM
- 产出 `docs/verify/0025-manual-verification.md`（全部原始观测值 + 结论）
- 回填 `docs/verification-debt.md`：第 66 行结案、第 72 行过期项关闭、新增性能行

## Capabilities

### New Capabilities

无。本 change 不新增业务代码。

### Modified Capabilities

无。`skip_specs: true`。

## Impact

- 影响文件：
  - `docs/verify/0025-manual-verification.md`（新增）
  - `docs/ai-collaboration/0025-manual-verification.md`（新增）
  - `docs/verification-debt.md`、`docs/regression-log.md`
  - 不修改 `src/main/**` 与 `src/test/**`（除非实测暴露缺陷，届时另开 change 修复）
- 明确不做：
  - 不把真实 provider 冒烟写成 JUnit 用例（`AGENTS.md` §6 禁止用真实付费模型跑测试）
  - 不提交任何 Key、真实响应全文或 `artifacts/` 下的性能数据集
  - 不因性能数据调阈值、改默认值（那是行为变化）
