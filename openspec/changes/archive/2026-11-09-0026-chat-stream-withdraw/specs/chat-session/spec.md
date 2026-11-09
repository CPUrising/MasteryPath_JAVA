# Spec Delta: chat-session

## Purpose

定义普通聊天会话的对外契约：回合以 SSE 增量传输、回合结束后正文落库，以及按消息撤回对话，使前端能在不刷新页面的前提下恢复发送前的上下文。

## ADDED Requirements

### Requirement: Chat Turn Streaming

`POST /chat/sessions/{sessionId}/turns` SHALL 以 `text/event-stream` 逐帧返回 `{type, data}`。当上游模型产出文本增量时，系统 MUST 立即写出对应的 `text_delta` 帧，MUST NOT 等整段回答完成后才发送。回合正常结束 MUST 发出 `turn_end`。

#### Scenario: Incremental delta delivery

- **GIVEN** 上游模型把一段回答分成多片返回
- **WHEN** 消费方保持 SSE 连接并逐帧读取
- **THEN** 每一片到达后 MUST 能立即读到对应的 `text_delta`，无需等待 `turn_end`

#### Scenario: Turn persists transcript and ends

- **GIVEN** 一次成功的聊天回合
- **WHEN** 回合结束
- **THEN** 用户消息与助手正文 MUST 落库，且最后一帧 MUST 为 `turn_end`

#### Scenario: Partial text survives upstream failure

- **GIVEN** 上游模型在产出部分文本后失败
- **WHEN** 回合结束
- **THEN** 已产出的文本 MUST 被保留，且 MUST 发出 `error` 帧

### Requirement: Message Withdrawal

`DELETE /chat/sessions/{sessionId}/messages/{messageId}` SHALL 删除指定消息及其之后的所有消息，使会话回到该消息发送前。成功 MUST 返回 `204`；未知会话 MUST 返回 `404`；`messageId` 不存在时 MUST 保持幂等并返回 `204`。

#### Scenario: Withdraw truncates tail

- **GIVEN** 一个含四条消息（user/assistant/user/assistant）的会话
- **WHEN** 用第一条消息的 id 调用撤回端点
- **THEN** 响应 MUST 为 `204`，且会话详情 MUST 不再包含任何消息

#### Scenario: Withdraw keeps earlier history

- **GIVEN** 一个含四条消息的会话
- **WHEN** 用第三条消息的 id 调用撤回端点
- **THEN** 前两条消息 MUST 保留，第三条及其之后 MUST 被删除

#### Scenario: Unknown session

- **GIVEN** 一个不存在的会话 id
- **WHEN** 调用撤回端点
- **THEN** 响应 MUST 为 `404`

#### Scenario: Repeated withdrawal is idempotent

- **GIVEN** 一条已被撤回的消息 id
- **WHEN** 再次调用撤回端点
- **THEN** 响应 MUST 为 `204`，且 MUST NOT 产生错误或恢复已删除消息
