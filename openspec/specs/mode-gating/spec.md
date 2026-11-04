# Mode Gating Capability Spec

## Purpose

定义三种模式（`outline` / `study` / `review`）对工具调用的权限边界，以及门控必须在调用期生效的原因与规则：模式专属工具在装配期全部挂载，执行时校验，越权即拒绝且零状态变更。

## Requirements

### Requirement: Static Fail Closed Classification

系统 SHALL 用代码内的静态表声明每个工具的归属模式，MUST 对未声明的工具默认拒绝。共享工具 MUST 显式列名，且 MUST NOT 与模式专属集合相交。

#### Scenario: TC-D1 TC-D2 TC-D3 mode permission matrix

- **GIVEN** 当前模式为 `outline` / `study`
- **WHEN** 校验 `mastery_quiz` / `mastery_build` / `mastery_status`
- **THEN** 系统 MUST 分别拒绝、拒绝、放行

#### Scenario: TC-D9 classification guard

- **GIVEN** 全部 `mastery_*` 工具
- **WHEN** 运行完整性守卫
- **THEN** 每个工具 MUST 被显式归类，且两集合 MUST 不相交

### Requirement: Ungated When Mode Unknown

系统 SHALL 在会话模式缺失或未识别时不做门控，放行全部工具。

#### Scenario: TC-D4 unknown mode allows all

- **GIVEN** 模式为 `null` 或未知值
- **WHEN** 调用任意工具
- **THEN** 系统 MUST 全部放行

### Requirement: Rejection Must Point The Way

系统 SHALL 在拒绝时返回可操作错误，MUST 指明该工具所属模式、当前模式，以及应调用的模式切换动作。

#### Scenario: TC-M2 structure change blocked in study

- **GIVEN** 当前模式为 `study`
- **WHEN** 调用 `mastery_revise`
- **THEN** 系统 MUST 拒绝且 MUST NOT 改变地图

### Requirement: Study Admission Requires Outline

系统 SHALL 在进入 `study` 前校验路径已有大纲；无大纲时 MUST 拒绝并提示先在 `outline` 模式设计大纲。该规则 MUST 在工具层与 REST 模式切换端点各生效一次，且被拒绝的切换 MUST NOT 持久化。

#### Scenario: TC-D5 TC-D6 admission error

- **GIVEN** 目标模式为 `study` 且路径无大纲 / 已有大纲
- **WHEN** 校验准入
- **THEN** 系统 MUST 返回非空拒绝提示 / MUST 放行

#### Scenario: TC-L1 TC-L2 TC-L4 successful switch persists

- **GIVEN** 一次成功的模式切换
- **WHEN** 提交
- **THEN** 会话的 `mode` MUST 记录新模式，并通过 `session_meta` 告知前端
- **AND** 重开会话时 MUST 能恢复该模式

#### Scenario: TC-L3 rejected switch not persisted

- **GIVEN** 无大纲路径尝试切到 `study`
- **WHEN** 切换被拒绝
- **THEN** 系统 MUST NOT 产生持久变更，路径与会话存储 MUST 不变

### Requirement: Review Only Retests Mastered

系统 SHALL 在 `review` 模式下只允许对已掌握知识点出题、判分与定性重评；对未掌握知识点的这些调用 MUST 被拒绝，且 MUST NOT 改变状态。

#### Scenario: TC-K6 TC-D7 TC-D8 review scope

- **GIVEN** 当前模式为 `review`
- **WHEN** 对未掌握 / 已掌握知识点出题
- **THEN** 系统 MUST 拒绝前者 / 放行后者

### Requirement: Behavior Budget

系统 SHALL 对单回合工具调用数、连续追问次数、同一题提示次数设置上限，超限 MUST 拒绝后续调用并给出可操作提示。

#### Scenario: TC-M6 tool budget

- **GIVEN** 单回合工具调用已达上限
- **WHEN** 模型继续请求调用
- **THEN** 系统 MUST 拒绝并结束回合
