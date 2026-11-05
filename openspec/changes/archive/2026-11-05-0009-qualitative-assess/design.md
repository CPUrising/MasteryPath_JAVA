# Design: 定性评估

## Context

参考实现 `service.assess`（`learning/service.py:766-851`）是 `concept` / `design`
**唯一**的门槛打开方式。它和 `grade` 的差异必须逐项对齐：

| 维度 | `grade` | `assess` |
| --- | --- | --- |
| 允许的类型 | 全部 | **仅** `concept` / `design` |
| 写入的分数 | 定量类型重算 `masteryLevels` | **从不**写 `masteryLevels` |
| 写入的定性标记 | 不写 | 写 `qualitativeMastery[kpId] = passed` |
| 证据类型 | `QUIZ` / `REVIEW` | **固定** `QUALITATIVE` |
| 证据结果 | `correct` / `incorrect` / `partial` | `correct`（通过）/ `partial`（未通过） |
| 证据 quality | 不写 | `1.0`（通过）/ `0.2` 或显式值（未通过） |
| 题库 `question_type` | `choice`/`short`/`open` | **`"written"`** |

## Goals / Non-Goals

**Goals**

1. 逐字段复刻 `assess`：证据、定性标记、门槛、事件、题库。
2. 类型校验的文案逐字：`mastery_assess is for concept/design objectives; use mastery_grade for memory/procedure`。
3. `quality` 的默认值规则：通过 `1.0`；未通过时显式值优先，缺省 `0.2`。
4. 多次评估允许累积证据（与 `grade` 的幂等不同）。

**Non-Goals**

1. 不做 LLM 判定（导师侧由模型给出 `passed`，服务层只落库）。
2. 不实现复习调度（`0010`）。
3. 不实现工具层与 mode 门控（`0013`）。

## Decisions

1. **`assess` 放在 `LearningService`，不新建 `AssessService`**
   - 参考实现就在 `LearningService` 里，且只有 80 行。
   - `migration-plan.md` §5 写的「`service/AssessService`」是**预估**，不是硬约束；
     按 `AGENTS.md` §3「一个 change 只含一个行为增量」，拆类会引入无收益的间接层。
   - 已在本 change 的 proposal 中说明，并在 `docs/ai-collaboration` 留痕。

2. **类型校验在事务内、`wasMastered` 之前**
   - 参考实现顺序：查 KP → 校验类型 → 读 `wasMastered` → 写 `qualitativeMastery`。
   - 顺序敏感：若先写 `qualitativeMastery` 再校验类型，非法类型会污染聚合（虽然事务会回滚，
     但错误路径上不应产生任何写入意图）。

3. **`quality` 的默认值用「通过 1.0 / 未通过 0.2」**
   - 参考实现：`1.0 if passed else (0.2 if quality is None else quality)`。
   - 注意：通过时**忽略**传入的 `quality`，固定 `1.0`。
   - Java 用 `Double quality`（可空）区分「未传」与「传了 null」。

4. **`gateCleared` 在写 `qualitativeMastery` 之后计算**
   - 与 `0006` 的 override、`0008` 的 grade 同类陷阱：`isMastered` 读的是刚写入的标记。

5. **证据结果用 `partial` 而非 `incorrect` 表示「未通过」**
   - 参考实现：`AssessmentResult.CORRECT if passed else AssessmentResult.PARTIAL`。
   - 理由：定性未通过是「还没讲清楚」，不是「答错了」。
   - 注意：`_schedule_after` 传的 `result` 是 `"correct" if passed else "incorrect"`
     （**与证据结果不同**），这是参考实现的不一致，照抄。

6. **清空待答题只在同一知识点时**
   - 参考实现：`if pending_question and pending_question.knowledge_point_id == kp_id`。
   - 理由：评估另一个知识点时不应误清当前待答题。

7. **题库条目的 `question` 字段填知识点名、`question_type="written"`**
   - 参考实现原样；`written` 不在 `QuizSpec.QUESTION_TYPES` 里，是 `assess` 专有。
   - `user_answer` 填 `evidence` 文本。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 未校验类型就写 `qualitativeMastery` | `memory` 被判为定性通过（`INV-05` 违规） | 校验在写入之前；用例断言 `memory` 评估抛错且聚合无变化 |
| `gateCleared` 先算后写 | 永远 false | 用例断言首次通过 → `gate_cleared=true` |
| 通过时仍使用传入的 `quality` | 与参考实现不一致 | 用例断言通过时 `quality == 1.0`（即使传了 0.5） |
| 证据结果写成 `incorrect` | 复习调度会把它当「答错」 | 用例断言未通过时证据为 `partial` |
| 误清其他知识点的待答题 | 学习者丢失当前题 | 用例断言不同 KP 的待答题不被清 |
| 把 `assess` 做成幂等 | 无法累积多次评估的证据 | 用例断言两次评估产生 2 条证据 |

## Migration Plan

1. `LearningService.assess`（单方法，依赖已有的模型与策略）。
2. 测试：`QualitativeAssessTest`。
3. `verify.ps1 -Scope unit` → 全量 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/service.py:766-851` 为唯一依据。
- 已知参考实现测试空白（本 change 会补上）：
  - `quality` 默认值的两条分支（通过 1.0 / 未通过 0.2）无专门用例；
  - 「通过时忽略传入 quality」无用例；
  - 「不同 KP 的待答题不被清」无用例；
  - 题库 `question_type="written"` 无断言。
