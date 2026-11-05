# Proposal: 模式门控

## Why

`0002`–`0004` 让系统知道「什么叫掌握」与「下一个该学什么」，但还不知道
**「当前这个会话允许做什么」**。参考实现把这件事收敛成一个纯函数层
`capabilities/mastery/mode.py`（187 行）+ 一组预算常量 `behavior.py`（11 行）。

模式（`outline` / `study` / `review`）决定：
- 哪些工具可以调用（`mastery_build` 只能在 outline，`mastery_quiz` 只能在 study/review）；
- 拒绝时模型看到的文案（必须能自我纠正，指明切哪个模式）；
- 进入 `study` 前必须已有大纲；
- 单回合能调几次工具、能连问几次、同一题能给几次提示。

若这层不等价，`0013` 的回合循环会放行越权调用，`0007` 的 build 会在 study 模式下改地图。

## What Changes

- 新增 `SessionMode` 枚举：`outline` / `study` / `review` + 每个模式的提示词块
- 新增 `ToolClassification`：静态分类表（8 共享 / 2 outline 专属 / 9 study+review 共用）
  与 fail-closed 完整性守卫
- 新增 `ModeGate`：`toolIsAllowed` / `owningModes` / `wrongModeMessage` / `admissionError`
- 新增 `BehaviorBudget`：三个预算常量（6 / 3 / 2）

## Capabilities

### New Capabilities

无新增能力；本变更实现 `specs/mode-gating` 中的
「Static Fail Closed Classification」「Ungated When Mode Unknown」
「Rejection Must Point The Way」「Study Admission Requires Outline」
「Behavior Budget」五条需求中**纯函数部分**。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/domain/mode/**`（新增 4 个类）、
  `src/test/java/com/masterypath/domain/mode/**`（新增 4 个测试类）、
  `scripts/verify.ps1`（`-Scope unit` 补 3 个测试类名）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope，均已登记到 `docs/verification-debt.md`）：
  - `TC-L1`/`TC-L2`/`TC-L4`（会话模式持久化与 `session_meta`）→ 依赖 `setSessionMode`，属 `0006`；REST 端点属 `0015`。
  - `TC-L3`（被拒切换不持久）→ 同上。
  - `TC-M2`（study 下 `mastery_revise` 被拒且地图不变）→ 需要 `revise` 工具本体，属 `0007`。
  - `TC-M6`（单回合工具预算触发 `turn_end`）→ 需要回合循环，属 `0013`。
  - `TC-D7`/`TC-D8` 的**知识点级** review 拒绝（`review` 下拒绝对未掌握 KP 出题）→ 需要工具执行上下文，属 `0008`；本变更只覆盖**模式许可级**判定（与参考实现 `tests/test_mode.py` 一致）。
  - `rag` / `read_source` 的条件挂载 → 属 `0016`。
