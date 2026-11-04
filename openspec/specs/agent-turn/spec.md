# Agent Turn Capability Spec

## Purpose

定义一轮导师对话的编排契约：WS 回合的请求与事件、REST 端点、领域事件命名、错误码，以及 LLM 适配边界。前端唯一依赖即这些契约，因此它们必须在实现前冻结。

## Requirements

### Requirement: Transport Split

系统 SHALL 让「一轮导师对话」只走 WebSocket，其余所有操作走 REST。WS 请求 MUST 携带会话、路径、模式与回合标识；事件 MUST 为 `text_delta` / `tool_call` / `tool_result` / `question_card` / `session_meta` / `turn_end` / `error`。

#### Scenario: TC-I1 REST mode gate

- **GIVEN** 当前会话模式不是 `outline`
- **WHEN** 通过 REST 写地图
- **THEN** 系统 MUST 返回模式冲突错误且地图 MUST 不变

#### Scenario: TC-I2 rebuild requires confirm over REST

- **GIVEN** 非空路径
- **WHEN** 调用重建端点且未带 `confirm`
- **THEN** 系统 MUST 拒绝

#### Scenario: TC-I3 projection hides answers

- **GIVEN** 一张待答题目卡
- **WHEN** 序列化为学习者可见投影
- **THEN** 投影 MUST NOT 含期望答案与解析

#### Scenario: TC-I7 override emits event without evidence

- **GIVEN** 一次学习者声明掌握
- **WHEN** 提交
- **THEN** 系统 MUST 发出声明事件且 `learning_evidence` MUST 无新增

### Requirement: Turn Orchestration

系统 SHALL 在回合开始前一次性装配工具 schema 与系统提示，并在调用期做模式校验。回合内 MUST 支持流式文本增量、工具调用与结果透传；工具结果中的卡片元数据 MUST 原样透出给前端。

#### Scenario: WS event order

- **GIVEN** 一轮会触发工具调用的导师对话
- **WHEN** 消费 WS 事件流
- **THEN** 顺序 MUST 为 `tool_call` → `tool_result` → `text_delta` → `turn_end`

#### Scenario: Partial text survives stream failure

- **GIVEN** 模型流在中途失败
- **WHEN** 回合结束
- **THEN** 已产出的文本 MUST 被保留，且 MUST 发出 `error` 事件

### Requirement: Provider Isolation

系统 SHALL 把供应商差异收敛在适配层：领域层 MUST NOT 依赖任何供应商类型，适配层 MUST 提供可离线运行的假客户端。

#### Scenario: Offline test client

- **GIVEN** 无网络环境
- **WHEN** 运行回合相关单元测试
- **THEN** 测试 MUST 通过，且 MUST NOT 发起真实网络请求
