# Proposal: 题卡事件权威化与作答反馈落库

## Why

后端已经发出 `question_card` 事件，并把题卡写进 assistant 消息的 `data.card`；但前端只消费
`GET /learning/paths/{id}` 的 `pending_question`，导致题卡不在消息流里、作答反馈只存在本地，
刷新即丢（`0028` 已登记该限制）。同时 `ungraded` 被 REST 映射为 422，学习者看到的是“失败”
而不是“我没听懂”；short/open 缺少可接受答案时，等价表述容易被判错。

`0032/0033` 的生成型题卷需要一套稳定的“题卡/题卷事件 + 会话消息落库”契约，本 change 先把它冻结。

## What Changes

- **题卡事件**：`question_card` 增量字段 `card_kind`、`turn_id`、`objective {id,name}`；
  学习者可见投影仍是 7 键白名单，`expected_answer` / `explanation` 永不下发。
- **判分反馈落库**：`POST /learning/paths/{id}/grade` 绑定 `session_id` / `turn_id`；判分结果
  （含 `ungraded`）写入 `messages`（user 答案 + assistant 判分数据），刷新后仍可见。
- **short 答案容错**：`mastery_quiz` 注册 short 时要求提供 `accepted_answers`；纯数值/单 token
  的 short 可豁免。缺失时返回可读拒绝，不静默降级。open 继续使用既有关键词契约。
- **前端**：题卡内联消息流、历史 `data.card` 恢复、作答反馈持久化、`ungraded` 可重答。
- **BREAKING（请求字段）**：`grade` 请求新增可选 `session_id` / `turn_id`；不传仍能判分，
  但不会把反馈写入会话消息。事件新增字段是向后兼容的。
- **明确不做**：不改判分算法、掌握门槛、单槽位、SM-2、现有表 DDL、chat SSE。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `agent-turn`: `question_card` 事件的字段契约与会话消息中的题卡恢复。
- `learning-tools`: `mastery_grade` 的 REST 会话绑定、判分反馈落库、`ungraded` 行为；
  short 题的可接受答案要求。

## Impact

- Java：`adapter/tool/StudyTools`、`adapter/web/StudyController`、`service/LearningService`、
  `storage/LearningStore`（新增消息落库入口）、相关 JUnit。
- Web：`app/(workspace)/learning/mastery/[pathId]/sessions/page.tsx`、
  `components/learning/QuestionCard.tsx`、`lib/api.ts`、`lib/types.ts`、i18n。
- 数据：不新增表；`messages.data` 复用现有 `card` 键，新增 `grade` 键。
- 文档：`docs/traceability.md`、`docs/verify/0031-quiz-card-event-authority.md`、
  `docs/regression-log.md`。
- 不影响：`0032/0033` 尚未实现的题卷表与 UI；现有 REST/WS 路径与事件名。
