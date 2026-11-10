# Tasks

## 1. 证据通道

- [x] 1.1 `TurnRunner.historyMessages` 恢复为只回放 role + content（不再拼题卡/判分文本）。
- [x] 1.2 新增 `recapContext()`：最近 6 张卡片 + 判分配对，标注为运行时数据、不得复述。
- [x] 1.3 `initialize` 把回顾并入 `context`（与 `mastery_status` 快照同通道）。

## 2. 前端答题入口

- [x] 2.1 `QuestionCard` 去掉自由文本输入框，无选项时显示「在下方输入框作答」。
- [x] 2.2 会话页 `send` 在存在 `pending_question` 时附加 `mastery_answer`。
- [x] 2.3 `i18n/zh.json`、`i18n/en.json` 增加提示文案。

## 3. 会话连续性

- [x] 3.1 无 `session` 参数时拉取会话列表，取最新一条并 `router.replace` 写回地址。
- [x] 3.2 无任何会话时生成新 id 并立即写回地址。
- [x] 3.3 `SessionCamp` 的「打开」链接带上各自的 `session_id`。

## 4. 历史渲染

- [x] 4.1 加载时按「正文 → 题卡/判分 → 题卷」拆分消息，正文不再被卡片/判分覆盖。
- [x] 4.2 渲染不做条数截断。

## 5. 验证

- [x] 5.1 `TurnRunnerTest#historyKeepsRuntimeEvidenceOutOfAssistantText`（新增，替换 0041 的反向用例）。
- [x] 5.2 `.\scripts\verify.ps1 -Scope all -Change 0042-mastery-session-continuity` 通过。
- [x] 5.3 `npm run typecheck` 与 `npm run build` 通过。
- [x] 5.4 回填 `docs/verify/0042-mastery-session-continuity.md`，追加 `docs/regression-log.md` 与 `docs/traceability.md`。

## Workflow follow-up

- [x] 人类审查证据后归档。
