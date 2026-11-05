# Proposal: 大纲评测与 CLI

## Why

`0007` 已把在线覆盖报告（`CoverageReport`）落库，但 `09` 规范里还有两块缺口：

1. **离线评测指标**：`eval/outline_eval.py` 的确定性指标（`kp_precision` / `kp_recall` /
   `kp_f1` / `count_in_bounds` / `acyclic`）完全没有 Java 实现——换提示词、换模型、
   改分块时没有任何可对比的基线。
2. **命令行入口**：`python -m masterypath.eval outline --cases ... [--baseline ...]`
   没有等价物；「低于阈值 / 基线退化就非零退出」这条 CI 契约缺失。
3. **在线 `eval` 端点**：`POST /learning/paths/{id}/eval` 未实现（`0022` 的 proposal
   已把它显式排除）。

参考实现：`masterypath/eval/outline_eval.py`（110 行）、`masterypath/eval/__main__.py`
（59 行）、`learning/service.py:964-987`、`api/app.py:382-387`。

## What Changes

- 新增 `adapter/eval/OutlineEval`：`evaluateCase` / `evaluateCases` / `loadCases`
  （令牌重叠代理，阈值 0.8，无嵌入、无网络）
- 新增 `cli/OutlineEvalCli`：`outline --cases <jsonl> [--baseline <json>] [--min-f1 <float>]`，
  达标返 0、不达标返 1
- 新增 `LearningService.evalOutline(String pathId)`：重算覆盖 + 三项阈值 + `passed`
- `LearningController` 补 `POST /paths/{id}/eval`（未知路径 404 `unknown path`）
- `scripts/verify.ps1` 的 `-Scope unit` 补 `*OutlineEvalCliTest`、`*OutlineEvalTest`，
  `-Scope api` 补 `*EvalApiTest`

## Capabilities

### New Capabilities

无新增能力；落地 `../MasteryPath/docs/09-outline-eval.md`（ADR-018 / `INV-57` / `TC-N5` /
`TC-N6`）与 `specs/learning-tools` 的 `Coverage Report On Generation` 的离线评测部分。

### Modified Capabilities

无。`skip_specs: true`。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/adapter/eval/OutlineEval.java`（新增）
  - `src/main/java/com/masterypath/cli/OutlineEvalCli.java`（新增）
  - `src/main/java/com/masterypath/service/LearningService.java`（+`evalOutline`）
  - `src/main/java/com/masterypath/adapter/web/LearningController.java`（+1 端点）
  - `src/test/java/com/masterypath/**/{OutlineEvalTest,OutlineEvalCliTest,EvalApiTest}.java`（新增）
  - `scripts/verify.ps1`
- 影响测试：既有 835 个用例**不修改**。
- 明确不做（Out of scope）：
  - 嵌入匹配版语义指标（规范自己标注为「后续替换项，不阻塞确定性指标」）
  - 旧库双向读取、前端联调、全量归档 → 属 `0024-acceptance`
