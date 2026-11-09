# Tasks

## 1. 题卡事件

- [x] 1.1 `StudyTools.questionCard` 增加 `card_kind=mastery`、`turn_id`、`objective{id,name}`；7 键投影不变。
- [x] 1.2 `TurnRunnerTest` 断言 `question_card` 新字段，且不含答案/解析/关键词。

## 2. 判分反馈落库

- [x] 2.1 `LearningService.recordGradeInteraction` 写入 user 答案 + assistant `data.grade`，`message_count + 2`。
- [x] 2.2 `StudyController.grade` 在成功/`ungraded` 时落库，`already_graded` 跳过；仍保持 422 契约。
- [x] 2.3 `StudyApiTest` 覆盖：成功反馈持久化、`ungraded` 零掌握变更、重复判分不重复写消息。

## 3. short 题校验

- [x] 3.1 `StudyTools.Quiz` 对 short 要求 `accepted_answers`，纯数值/单 token 豁免；错误文案可读。
- [x] 3.2 工具层测试覆盖：自然语言 short 缺答案被拒、数值 short 放行、带答案放行。

## 4. 前端接入

- [x] 4.1 `Item` / `SessionMessage` 支持 `card` 与 `grade`；历史消息保留空正文但有数据的消息。
- [x] 4.2 内联题卡按 `question_id` 去重；已判分卡显示已作答；`pending_question` 仅兜底。
- [x] 4.3 `answerQuestion` 带 `session_id` / `turn_id`，追加用户答案与反馈气泡；`ungraded` 可重答。
- [x] 4.4 `QuestionCard` 增加 `answered`；i18n 补“未判定/请重答”文案。

## 5. 验证

- [x] 5.1 `.\mvnw.cmd -q -DskipTests test-compile` 通过。
- [x] 5.2 `.\scripts\verify.ps1 -Change 0031-quiz-card-event-authority` 通过（874 / 0 / 0）。
- [x] 5.3 前端手测：出题 → 选项作答 → 反馈出现 → 刷新后题卡与反馈仍在且不重复；`ungraded` 由 `StudyApiTest` 覆盖。

## Workflow follow-up

- [x] 回填 `docs/verify/0031-quiz-card-event-authority.md`。
- [x] 追加 `docs/regression-log.md`。
- [x] 更新 `docs/traceability.md` 的新场景行。
- [x] 人类审查证据后归档（2026-11-09 确认）。
