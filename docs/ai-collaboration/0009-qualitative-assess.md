# AI 协作留痕：0009-qualitative-assess

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/service/LearningService.java`（补 `assess`
  与 `writtenBankEntry`）、`src/test/java/com/masterypath/service/QualitativeAssessTest.java`、
  `scripts/verify.ps1`（补 `*AssessTest`）、`openspec/changes/0009-qualitative-assess/**`、`docs/**`。
- 明确不做的事：不实现复习调度（`0010`）、不实现工具层 `mastery_assess` 与 review 校验（`0013`）。
- 出处：用户指令原文「git 提交后继续计划」；`docs/migration-plan.md` §5 第 9 行。

## 2. AI 产出

- 新增文件：`QualitativeAssessTest`（25 个用例）
- 修改文件：`LearningService`（补 `assess` + `writtenBankEntry` 私有辅助）；
  `scripts/verify.ps1`（`-Scope unit` 补 `*AssessTest`）
- 关键决策：
  - **不新建 `AssessService`**：参考实现就在 `LearningService`（80 行），拆类会引入无收益的
    间接层。`migration-plan.md` §5 的「`service/AssessService`」是预估，不是硬约束。
  - **类型校验在写入之前**：先校验 `concept`/`design`，再读 `wasMastered`、再写
    `qualitativeMastery`。顺序反了会在错误路径上产生写入意图。
  - **`gateCleared` 在写 `qualitativeMastery` 之后计算**：与 `0006` 的 override、
    `0008` 的 grade 同类陷阱。
  - **未通过记 `partial` 而非 `incorrect`**：定性未通过是「还没讲清楚」，不是「答错了」。
  - **`quality` 默认值**：通过固定 `1.0`（忽略传入值）；未通过显式值优先、缺省 `0.2`。
  - **只清同一知识点的待答题**：避免评估 A 时误清 B 上的当前题。
  - **题库题型固定 `written`**：`written` 不在 `QuizSpec.QUESTION_TYPES` 里，是 `assess` 专有。

## 3. 人工修正点

- 无人类介入（人类尚未审查本 change 产出）。
- AI 自我修正：本 change 首次即通过，无编译/测试失败。改动 `verify.ps1` 后主动检查了
  BOM 字节（`EF BB BF 23 21 2F`，单个 BOM），避免重演 `0008` 的双 BOM 问题。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 25 / 0；全量 378 / 0。
- 证据链接：[`docs/verify/0009-qualitative-assess.md`](../verify/0009-qualitative-assess.md)
- 日志：`artifacts/verify-0009-qualitative-assess-20261104-144641.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_assess.py -q` → **5 passed**。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否新建 `AssessService` | **不新建** | 参考实现就在 `LearningService`；拆类无收益，违反「不过度设计」 |
| 类型校验与写入的顺序 | **校验在前** | 错误路径上不应产生任何写入意图 |
| `gateCleared` 计算时机 | **写 `qualitativeMastery` 之后** | `isMastered` 读刚写入的标记；先算恒为 false |
| 未通过记 `partial` 还是 `incorrect` | **`partial`** | 「还没讲清楚」不是「答错了」；参考实现如此 |
| 通过时是否用传入 `quality` | **不用，固定 1.0** | 参考实现 `1.0 if passed else (...)` |
| 未通过且未传 `quality` | **0.2** | 参考实现默认值 |
| 是否清空所有待答题 | **只清同 KP 的** | 避免评估 A 时误清 B 上的当前题 |
| 评估是否写 `masteryLevels` | **从不写** | `INV-05`；分数只能由 `grade` 给定量类型 |
| 题库题型 | **`written`** | 参考实现原样；不在 `QuizSpec` 的合法题型里，属 `assess` 专有 |
| `review` 参数的作用 | **只透传** | 参考实现固定写 `QUALITATIVE` 证据；`review` 实际作用于 `_schedule_after`（属 `0010`） |
| 调度参数用 `correct`/`incorrect` 而证据用 `correct`/`partial` | **照抄不一致** | 参考实现原样；调度属 `0010`，届时按此实现 |
