# 验证报告：0023-outline-eval-cli

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0023-outline-eval-cli` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-05 12:07:57（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0023-outline-eval-cli` |
| 日志 | `artifacts/verify-0023-outline-eval-cli-20261105-120757.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 离线指标（`OutlineEvalTest`，15 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 令牌化：中文整体成词、英文小写、保留下划线 | `#tokensAreUnicodeAware` | PASS | Python `[\w\u4e00-\u9fff]+` 的等价写法 |
| Jaccard 相似度 + 空集 0.0 | `#similarUsesJaccard` | PASS | `alpha beta` vs `alpha gamma` = 1/3 |
| 生成 = 参考 → 三项 1.0、在界内、无环 | `#identicalCaseScoresOne` | PASS | 对应 `test_eval_cli_passes_on_matching_case` 的指标部分 |
| `generated_modules` 缺省回落参考 | `#generatedFallsBackToReference` | PASS | 对应 `test_eval_cli_fails_on_cycle` 的回落分支 |
| 空用例：precision/recall 1.0，KP 数 0 越界 | `#emptyCaseDefaultsToOne` | PASS | 空集默认值是 1.0 而非 0.0 |
| `count_in_bounds` 闭区间 `[1, 60]` | `#countBoundsAreInclusive` | PASS | 1 / 60 通过，61 失败 |
| 部分匹配的 precision / recall / f1 | `#partialMatchComputesPrecisionAndRecall` | PASS | 1.0 / 0.5 / 2/3 |
| 完全不相关 → 全 0 | `#unrelatedNamesScoreZero` | PASS | |
| **贪心一对一匹配** | `#matchingIsGreedyOneToOne` | PASS | 参考 2 项同名、生成 1 项 → recall 0.5 |
| 前置成环 → `acyclic=false` | `#cycleDetected` | PASS | 对应 `test_tc_n4_cycle_detected` 的纯函数版 |
| 前置指向不存在节点 → 忽略该边 | `#missingPrerequisiteIgnored` | PASS | 参考实现 `if neighbour in graph` |
| **空列表汇总形状（4 键，无均值 2 键）** | `#evaluateCasesEmptyShape` | PASS | 逐字复刻不对称行为 |
| 非空汇总补齐三个均值 | `#evaluateCasesAverages` | PASS | mean_f1 = 0.5 |
| 确定性：两次结果一致 | `#evaluationIsDeterministic` | PASS | `TC-N6` |
| `loadCases` 跳过空行 | `#loadCasesSkipsBlankLines` | PASS | |

### 2.2 CLI（`OutlineEvalCliTest`，8 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 参考用例完全匹配 → 退出码 0 | `#passesOnMatchingCase` | PASS | 对应 `test_eval_cli_passes_on_matching_case` |
| 前置成环 → 退出码 1 | `#failsOnCycle` | PASS | 对应 `test_eval_cli_fails_on_cycle` |
| `mean_kp_f1` 低于 `--min-f1` → 1 | `#failsBelowMinF1` | PASS | `TC-N5` |
| KP 数越过 60 → 1 | `#failsOutOfBounds` | PASS | |
| 基线退化超 0.05 → 1 | `#failsOnBaselineRegression` | PASS | |
| 退化在容差内 → 0 | `#toleratesSmallRegression` | PASS | 1.0 vs 基线 1.02 − 0.05 |
| 缺 `--cases` → 2 | `#missingCasesIsUsageError` | PASS | |
| 未知子命令 → 2 | `#unknownCommandIsUsageError` | PASS | |

### 2.3 评测端点（`EvalApiTest`，6 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 来源被引用 → `coverage_ratio=1.0`、`passed=true` | `#evalPassesWhenCovered` | PASS | 对应 `test_tc_n5_eval_passes_when_covered` |
| 有来源未被引用 → 0.5、`passed=false`、`missing` 含它 | `#evalReportsUnusedSource` | PASS | `TC-N2` 的端点侧 |
| 无来源无引用 → `documents=0` 时 1.0，但 grounding 0 使 `passed=false` | `#evalWithoutSources` | PASS | `documents == 0 → 1.0` 的边界 |
| 含环 → `acyclic=false`、`passed=false` | `#evalDetectsCycle` | PASS | `TC-N4` 的端点侧 |
| 确定性：两次相等 | `#evalIsDeterministic` | PASS | 对应 `test_tc_n6_deterministic` |
| 未知路径 → 404 `unknown path` | `#evalUnknownPathIs404` | PASS | |

本 change 新增用例：**29 个**（`OutlineEvalTest` 15 + `OutlineEvalCliTest` 8 +
`EvalApiTest` 6），全部 PASS。
全量 **864 个，0 失败**（835 → 864）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| API 专项 | `verify.ps1 -Scope api -Change 0023-outline-eval-cli` | Tests run: 175, Failures: 0 |
| 全量 | `verify.ps1 -Change 0023-outline-eval-cli` | BUILD SUCCESS，Tests run: 864, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0022` 的 835 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_eval.py -q` | **8 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_tc_n1_build_returns_coverage` | `0007` 的 `CoverageReportTest` | 已在 `0007` 覆盖 |
| `test_tc_n2_unused_source_is_missing` | `EvalApiTest#evalReportsUnusedSource` + `CoverageReportTest` | 端点侧断言 `coverage_ratio` / `missing` |
| `test_tc_n3_unreported_does_not_expand_missing` | `CoverageReportTest`（`0007`） | `reported=false` 时不展开 |
| `test_tc_n4_cycle_detected` | `EvalApiTest#evalDetectsCycle` + `OutlineEvalTest#cycleDetected` | 端点侧与纯函数侧各一 |
| `test_tc_n5_eval_passes_when_covered` | `EvalApiTest#evalPassesWhenCovered` | 同断言（`coverage_ratio=1.0`、`passed=true`） |
| `test_tc_n6_deterministic` | `EvalApiTest#evalIsDeterministic` | 同断言（两次调用相等） |
| `test_eval_cli_passes_on_matching_case` | `OutlineEvalCliTest#passesOnMatchingCase` | 同断言（退出码 0） |
| `test_eval_cli_fails_on_cycle` | `OutlineEvalCliTest#failsOnCycle` | 同断言（退出码 1） |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `OutlineEvalTest#tokensAreUnicodeAware` / `#similarUsesJaccard` | 参考实现无令牌化与相似度的单元用例 |
| `OutlineEvalTest#countBoundsAreInclusive` / `#emptyCaseDefaultsToOne` / `#partialMatchComputesPrecisionAndRecall` / `#unrelatedNamesScoreZero` | 参考实现无边界值与部分匹配用例 |
| `OutlineEvalTest#matchingIsGreedyOneToOne` / `#missingPrerequisiteIgnored` | 参考实现无贪心匹配与悬空前置用例 |
| `OutlineEvalTest#evaluateCasesEmptyShape` / `#evaluateCasesAverages` | 参考实现无汇总形状用例 |
| `OutlineEvalCliTest#failsBelowMinF1` / `#failsOutOfBounds` / `#failsOnBaselineRegression` / `#toleratesSmallRegression` | 参考实现无三条阈值分支与容差边界用例 |
| `OutlineEvalCliTest#missingCasesIsUsageError` / `#unknownCommandIsUsageError` | 参考实现无参数错误用例 |
| `EvalApiTest#evalWithoutSources` / `#evalUnknownPathIs404` | 参考实现无 `documents=0` 与 404 用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 语义指标用**令牌重叠代理**，不是嵌入匹配 | 中 | 规范 `09` 自己标注为「后续替换项，不阻塞确定性指标」；用例格式不变 |
| D2 | 令牌正则用 `[\p{L}\p{N}_]+` 而非 `[\w\u4e00-\u9fff]+` | 无 | Java 的 `\w` 默认 ASCII；`\p{L}` 覆盖中文且含其它文字，与 Python `re.UNICODE` 语义一致 |
| D3 | CLI 用手写参数解析，不用第三方库 | 无 | 只有 1 个子命令 + 3 个选项；`run(String[])` 返回 int 便于单测 |
| D4 | `evaluateCases` 空列表只返回 4 个键 | 无 | 参考实现原样（不对称行为），已逐字锁定 |
| D5 | OK：`evalOutline` 复用 `OutlineBuilder.computeCoverage` | 无 | 参考实现也是调用同一个 `compute_coverage`；不写第二份算法 |
| D6 | 基线读不到时按 `0.0` | 低 | 参考实现 `baseline.get("mean_kp_f1", 0.0)`；文件本身读不了会抛 `UncheckedIOException`（参考实现抛 `FileNotFoundError`） |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
| --- | --- | --- |
| 嵌入匹配版语义指标 | 后续迭代 | 规范标注为非阻塞替换项 |
| 真实 golden set（`docs/` + 示例资料） | `0024` | 需产品方提供 2–3 个真实样例 |
| 旧库双向读取 | `0024` | 验收范围 |
| 前端联调 | `0024` | 需全部端点就绪 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 864, Failures: 0` 来自
`artifacts/verify-0023-outline-eval-cli-20261105-120757.log`；API 专项 175 项来自
`artifacts/verify-0023-outline-eval-cli-20261105-120555.log`；参考实现对照见
`pytest tests/test_eval.py -q` 的 `8 passed`。
