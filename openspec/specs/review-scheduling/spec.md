# Review Scheduling Capability Spec

## Purpose

定义掌握之后的间隔复习调度：算法选定 SM-2，掌握即播种复习状态，到期进入复习队列，失败进入重学步并保留失败痕迹。

## Requirements

### Requirement: SM2 Only

系统 SHALL 只使用 SM-2 的字段参与计算（间隔序号、连续正确/错误、易度因子），FSRS 字段 MUST 仅用于展示，MUST NOT 与 SM-2 混算。

#### Scenario: TC-G1 interval sequence

- **GIVEN** 连续以最高评分答对
- **WHEN** 依次排期
- **THEN** 间隔 MUST 依次为 1 / 6 / 15 / 38 / 95 天

### Requirement: Failure Enters Relearning Step

系统 SHALL 在失败时把下一次复习排在重学步（不超过 1 天），递增遗忘次数，并标记该锚点由失败设置。

#### Scenario: TC-G2 failure scheduling

- **GIVEN** 一次失败作答
- **WHEN** 排期
- **THEN** `next_review_at` MUST 落在 1 天内，`lapse_count` MUST 递增，失败标志 MUST 为真

#### Scenario: TC-G3 recovery clears flag

- **GIVEN** 失败后下一次正式复习成功
- **WHEN** 排期
- **THEN** 失败标志 MUST 置为假且间隔 MUST 回到正常序列

#### Scenario: TC-G4 in-session practice only touches last review

- **GIVEN** 同一会话内再次练习并答对
- **WHEN** 记录
- **THEN** `last_review_at` MUST 更新，`next_review_at` 与失败标志 MUST 不变

### Requirement: Provenance Seeding

系统 SHALL 用单一调度器按来源播种：系统评估通过按正常序列，学习者声明按约一半的首次间隔，两者首次到期时间之比 MUST 落在 0.3 到 0.7 之间。学习者 MUST NOT 能直接读写调度内部参数。

#### Scenario: TC-G5 learner track is more conservative

- **GIVEN** 同一知识点的系统轨与声明轨
- **WHEN** 比较首次到期时间
- **THEN** 比值 MUST 落在 [0.3, 0.7]

#### Scenario: TC-G6 internals hidden

- **GIVEN** 学习者调用接口
- **WHEN** 尝试读写调度内部参数
- **THEN** 系统 MUST 拒绝

#### Scenario: TC-G7 scenario change does not retro-apply

- **GIVEN** 已排期（已有锚点）的条目
- **WHEN** 修改场景设置
- **THEN** 其 `next_review_at` MUST 不变
