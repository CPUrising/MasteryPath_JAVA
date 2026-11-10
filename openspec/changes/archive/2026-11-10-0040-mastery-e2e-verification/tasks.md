# Tasks

## 1. L1 判分矩阵

- [x] 1.1 新增 `src/test/resources/fixtures/real-answers-13.json`（13 条）。
- [x] 1.2 新增 `testsupport/AnswerFixtures`。
- [x] 1.3 `GradingTest` / `MasteryGradingTest` 各加一个 fixture 驱动用例。

## 2. L2 REST 契约

- [x] 2.1 新增 `MasteryGradeContractTest`（5 个用例）。

## 3. L3 真实模型 E2E

- [x] 3.1 新增 `MasteryJourneyE2ETest`（`e2e` 标签、真实模型、transcript 留痕）。
- [x] 3.2 `scripts/verify.ps1` 默认排除 `e2e`，新增 `-Scope e2e`。
- [x] 3.3 新增 `scripts/e2e-mastery.ps1`。

## 4. 验证

- [x] 4.1 默认全量 `.\scripts\verify.ps1 -Change 0040-mastery-e2e-verification` 通过（941）。
- [x] 4.2 `.\scripts\e2e-mastery.ps1` 真实模型跑通，产出 transcript。
- [x] 4.3 回填 `docs/verify/0040-mastery-e2e-verification.md`，追加回归日志与追踪矩阵。

## Workflow follow-up

- [x] 人类审查证据后归档。
