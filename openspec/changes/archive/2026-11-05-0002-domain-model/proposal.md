# Proposal: 领域模型与 JSON 映射

## Why

`0001` 只搭了空骨架。参考实现的所有判定（掌握门槛、判分、调度、门控）都建立在一组领域实体之上，
这些实体的**字段名、默认值、类型约束、历史兼容行为**直接决定了数据能否与旧库互通。
在写任何策略或存储之前，必须先把这层定死，否则后续每个 change 都要返工。

## What Changes

- 新增 7 个枚举：`KnowledgeType` / `ErrorType` / `InteractionStatus` / `AssessmentType` /
  `AssessmentResult` / `GapKind` / `GapResolution`。
- 新增 20 个实体（对应 `learning/models.py` 全部类）。
- 实现三条与 Python 对齐的兼容行为：
  1. 未知枚举值**拒绝**，历史中文类型值**映射**（`INV-16`）。
  2. 反序列化时**忽略未知字段**（对齐 Pydantic `extra="ignore"`）。
  3. 旧式选项字符串 `["A: x", "B: y"]` 解析为 `[{label, body}]`。
- 提供 `JsonSupport`（`ObjectMapper` 工厂）与 `@DomainModel` 元注解，统一 snake_case 命名与容错策略。

## Capabilities

### New Capabilities

无。本变更只实现 `specs/learning-domain` 中「Typed Knowledge Point」需求的数据结构部分。

### Modified Capabilities

无。

## Impact

- 影响文件：`src/main/java/com/masterypath/domain/model/**`（新增 30 个文件）、
  `src/test/java/com/masterypath/domain/model/**`（新增 4 个测试）。
- 影响测试：无既有测试被修改。
- 明确不做（Out of scope）：
  - 不实现掌握门槛、`next_objective`、判分、调度（属 `0004`/`0008`/`0010`）。
  - 不实现存储（属 `0003`）。
  - 不实现学习者可见投影 `public_pending_question`（属 `0006`，`TC-E4`/`TC-I3`）。
  - 不引入 Bean Validation 注解（校验写在 setter 内，保证反序列化路径也生效）。
