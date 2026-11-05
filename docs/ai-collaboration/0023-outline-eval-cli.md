# AI 协作留痕：0023-outline-eval-cli

## 1. prompt 范围

- 授权修改的文件：
  - `src/main/java/com/masterypath/adapter/eval/OutlineEval.java`（新增）
  - `src/main/java/com/masterypath/cli/OutlineEvalCli.java`（新增）
  - `src/main/java/com/masterypath/service/LearningService.java`（+`evalOutline`）
  - `src/main/java/com/masterypath/adapter/web/LearningController.java`（+`POST /paths/{id}/eval`）
  - `src/test/java/com/masterypath/adapter/eval/OutlineEvalTest.java`（新增）
  - `src/test/java/com/masterypath/cli/OutlineEvalCliTest.java`（新增）
  - `src/test/java/com/masterypath/adapter/web/EvalApiTest.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*OutlineEvalTest`、`*OutlineEvalCliTest`）
  - `openspec/changes/0023-outline-eval-cli/**`、`docs/**`
- 明确不做的事：嵌入匹配版语义指标（规范标注为非阻塞）、旧库双向读取与前端联调（`0024`）。
- 出处：用户指令原文「继续」；`docs/migration-plan.md` §5 第 23 行。

## 2. AI 产出

- 指标：`OutlineEval`（`tokens` / `similar` / `kpNames` / `acyclic` / `evaluateCase` /
  `evaluateCases` / `loadCases`，阈值 0.8、边界 `[1, 60]`）
- CLI：`OutlineEvalCli`（`main` / `run(String[])` / `run(String[], out, err)` /
  `CliArgs`，四条失败判定 + 退出码）
- 服务：`LearningService.evalOutline`（复用 `OutlineBuilder.computeCoverage`，三阈值 + `passed`）
- 传输：`LearningController.evalOutline`（`POST /paths/{id}/eval`，404 `unknown path`）
- 新增测试：`OutlineEvalTest`（15）、`OutlineEvalCliTest`（8）、`EvalApiTest`（6）

## 3. 人工修正点

- AI 自我修正 1 轮：
  1. **`OutlineEvalTest#matchingIsGreedyOneToOne` 的 `testCase(id, reference, generated)`
     参数顺序写反**——把只有 1 项的列表当成参考、2 项的当成生成，于是断言 `recall=0.5`
     实际得到 1.0。交换参数后通过。**测试基线写错，非实现缺陷**（实现与参考实现一致）。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 29 / 0；全量 864 / 0（835 → 864）。
- 证据链接：[`docs/verify/0023-outline-eval-cli.md`](../verify/0023-outline-eval-cli.md)
- 日志：`artifacts/verify-0023-outline-eval-cli-20261105-120757.log`（全量）、
  `artifacts/verify-0023-outline-eval-cli-20261105-120555.log`（API 专项 175 项）
- 参考实现对照：`pytest tests/test_eval.py -q` → **8 passed**

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 语义指标用嵌入还是令牌重叠 | **令牌重叠**（Jaccard，阈值 0.8） | 规范 `09` 明示当前实现是代理、嵌入是后续替换项；不引入网络依赖 |
| 令牌正则怎么写 | **`[\p{L}\p{N}_]+`** | Java `\w` 默认 ASCII；`\p{L}` 覆盖中文，与 Python `re.UNICODE` 等价 |
| 空集合的 precision/recall | **1.0** | 参考实现 `if generated else 1.0`；改成 0 会让「没生成」看起来更差，属行为变化 |
| 匹配是否求最优 | **贪心一对一，不回头** | 逐字复刻参考实现的 `for` + `break`；最优匹配会抬高分数 |
| `evaluateCases` 空列表形状 | **只 4 个键** | 参考实现原样；"补齐"会改变面向 CI 的 JSON 合同 |
| CLI 如何避免测试进程退出 | **`run(String[])` 返回 int，`main` 才 `System.exit`** | Python `main(argv)` 同样返回 int |
| `evalOutline` 读存库报告还是重算 | **重算** | 参考实现 `compute_coverage(progress, selected)`；且复用 `OutlineBuilder` 的唯一实现 |
| `documents == 0` 的 coverage_ratio | **1.0** | 参考实现 `if report.documents else 1.0` |
| 基线缺少 `mean_kp_f1` | **按 0.0** | 参考实现 `baseline.get("mean_kp_f1", 0.0)` |
| CLI 的用法错误退出码 | **2** | Python `argparse` 的约定（`SystemExit(2)`）；参考实现未显式测但行为一致 |
