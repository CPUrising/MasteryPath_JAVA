# Tasks

## 1. 离线指标

- [x] 1.1 `adapter/eval/OutlineEval`：令牌化 / Jaccard / `MATCH_THRESHOLD` / `COUNT_BOUNDS` / `acyclic`
- [x] 1.2 `adapter/eval/OutlineEval`：`evaluateCase`（贪心一对一 + 空集默认 1.0 + generated 回落）+ `evaluateCases`（空列表形状）+ `loadCases`

## 2. CLI

- [x] 2.1 `cli/OutlineEvalCli`：`outline --cases [--baseline] [--min-f1]`，报告进 stdout、失败进 stderr
- [x] 2.2 `cli/OutlineEvalCli`：四条失败判定与退出码（阈值 / 有环 / 越界 / 基线退化 0.05）

## 3. 服务与传输

- [x] 3.1 `service/LearningService.evalOutline`：复用 `OutlineBuilder.computeCoverage` + 三阈值 + `passed`
- [x] 3.2 `adapter/web/LearningController` 补 `POST /paths/{id}/eval`（未知路径 404）

## 4. 测试与验证

- [x] 4.1 `OutlineEvalTest`（15）+ `OutlineEvalCliTest`（8）+ `EvalApiTest`（6）；`verify.ps1` 补 `*OutlineEval*Test`、`*EvalApiTest`
- [x] 4.2 `.\scripts\verify.ps1 -Scope api -Change 0023-outline-eval-cli`（175 项）→ 全量（864 项）→ `pytest tests/test_eval.py -q`（8 passed）对照
- [x] 4.3 回填 `docs/verify/0023-outline-eval-cli.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0023-outline-eval-cli.md`、`README.md`
