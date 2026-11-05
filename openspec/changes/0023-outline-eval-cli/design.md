# Design: 大纲评测与 CLI

## Context

| 块 | 行号 | 内容 |
| --- | --- | --- |
| `eval/outline_eval.py` | 1–110 | 令牌重叠指标、无环检测、JSONL 加载 |
| `eval/__main__.py` | 1–59 | `outline` 子命令、阈值、基线对比、退出码 |
| `learning/service.py` | 964–987 | `eval_outline`：重算覆盖 + 三阈值 |
| `api/app.py` | 382–387 | `POST /learning/paths/{id}/eval` |
| `tests/test_eval.py` | 70–136 | `TC-N5` / `TC-N6` / 两条 CLI 用例 |

Java 侧 `0007` 已有 `OutlineBuilder.computeCoverage`（与参考实现逐字对齐），
本 change 直接复用它，不再写第二份覆盖算法。

## Goals / Non-Goals

**Goals**

1. 离线指标**确定性**：同一输入两次运行结果完全一致（`TC-N6`）。
2. CLI 退出码是 CI 契约：低于阈值 / 有环 / 数量越界 / 基线退化 → 退出码 1。
3. `eval` 端点只重算、只读，不写库、不调模型。

**Non-Goals**

1. 不做嵌入匹配版语义指标（规范标注为后续替换项）。
2. 不做旧库双向读取与前端联调（`0024`）。

## Decisions

1. **`OutlineEval` 是纯静态工具类，零 I/O 依赖（加载器除外）**
   - `evaluateCase` / `evaluateCases` 只吃 `Map`，不吃文件；
   - `loadCases(Path)` 单独负责 JSONL 读取（跳过空行）。

2. **令牌化：`[\p{L}\p{N}_]+`，等价 Python 的 `[\w\u4e00-\u9fff]+`**
   - Python `\w` 在 `re.UNICODE` 下含字母、数字、下划线；中文已被 `\u4e00-\u9fff` 覆盖。
   - Java 用 `\p{L}\p{N}_` 得到同一集合；`casefold()` 用 `toLowerCase(Locale.ROOT)`。
   - 中文不分词：`理解线程` 是一个 token，与参考实现一致。

3. **相似度是 Jaccard，阈值 0.8**
   - `len(交集) / len(并集)`；任一为空 → `0.0`。

4. **匹配是「贪心一对一」**
   - 按 `generated` 顺序，每条在 `reference` 里找第一个未被占用的、相似度 ≥ 0.8 的项；
   - 命中计数，不回头重排。`precision` 分母是 `generated`，`recall` 分母是 `reference`。

5. **空集合的默认值是 1.0，不是 0.0**
   - `generated` 为空 → `precision = 1.0`；`reference` 为空 → `recall = 1.0`；
   - `f1` 在 `precision + recall == 0` 时为 `0.0`。

6. **`generated_modules` 缺省回落到 `reference_modules`**
   - `case.get("generated_modules") or case.get("reference_modules")`——
     「只给参考大纲」时按「生成得完全正确」打分。

7. **`count_in_bounds` 的边界是闭区间 `[1, 60]`**
   - 计算对象是 `generated`（含回落）的 KP 总数；`0` 个 KP 判为越界。

8. **`_acyclic` 只看图里存在的节点**
   - 前置指向不存在的节点时忽略该边（`if neighbour in graph`）；
   - DFS 三色标记，状态 `1` 表示在当前栈上 → 有环。

9. **`evaluateCases` 空列表的形状不对称（逐字复刻）**
   - 空列表返回 `{cases: [], mean_kp_f1: 0.0, all_acyclic: true, all_in_bounds: true}`，
     **不含** `mean_kp_precision` / `mean_kp_recall`；
   - 非空才补齐三个 `mean_*`。这个不对称是参考实现原样，不"修正"。

10. **CLI 入口是 `OutlineEvalCli.run(String[])` 返回 int**
    - `main` 只做 `System.exit(run(args))`；测试直接调 `run`，避免测试进程被退出。
    - 这就是 Python `main(argv)` 返回 int 的等价物。

11. **报告先打印到 stdout，失败信息打印到 stderr**
    - `json.dumps(report, ensure_ascii=False, indent=2)` → Jackson 的
      `writerWithDefaultPrettyPrinter()`（不转义非 ASCII）；
    - 四条失败判定顺序：`mean_kp_f1` → `all_acyclic` → `all_in_bounds` → baseline 退化。

12. **基线容差 0.05，默认 `min-f1 = 0.8`**
    - 退化判定是严格小于 `baseline - 0.05`；`baseline` 缺 `mean_kp_f1` 键时按 `0.0`。

13. **`evalOutline` 重算而不是读存库报告**
    - 参考实现 `compute_coverage(progress, selected)` 在调用时重算；
    - 复用 `OutlineBuilder.computeCoverage`，`selected` = 全部来源 id（不过滤 `available`）。

14. **`evalOutline` 的三项阈值逐字锁定**
    - `coverage: 0.9` / `grounding: 1.0` / `acyclic: true`；
    - `documents == 0` 时 `coverage_ratio = 1.0`（不是 0）；
    - `passed` 是三项的**与**。

15. **`POST /paths/{id}/eval` 的 404**
    - 未知路径 → 404 `"unknown path"`（与 `coverage` 端点一致）；
    - 返回 8 个键，顺序与参考实现一致（`coverage_ratio` 打头）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 令牌化与 Python 不一致 | `kp_f1` 数值漂移 | 用 `\p{L}\p{N}_` 覆盖 Unicode；用例含中文与空串 |
| 贪心匹配实现成「最优匹配」 | 分数偏高 | 用例锁定「一对一、不回头」的顺序语义 |
| 空列表多返回两个键 | 前端/CI 解析差异 | `evaluateCases` 空列表用例断言键集合 |
| CLI 直接 `System.exit` | 测试进程被杀 | `run` 返回 int，`main` 才退出 |
| 基线比较方向写反 | 分数下降不报警 | 用例构造「低于基线 - 容差」触发 1 |
| 重算覆盖与存库报告不一致 | 两处数字打架 | 直接复用 `OutlineBuilder.computeCoverage`，不写第二份 |

## Migration Plan

1. `adapter/eval/OutlineEval`（令牌化 / 相似度 / 无环 / 单例 / 汇总 / 加载）。
2. `cli/OutlineEvalCli`（`outline` 子命令 + 退出码）。
3. `LearningService.evalOutline` + `LearningController` 的 `POST /paths/{id}/eval`。
4. 测试：`OutlineEvalTest` / `OutlineEvalCliTest` / `EvalApiTest`。
5. `verify.ps1 -Scope api` → 全量 → `pytest tests/test_eval.py -q` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/eval/` 与 `learning/service.py:964-987` 为唯一依据。
