# Tasks

## 1. 渲染能力

- [x] 1.1 `MarkdownMessage` 增加 `className` 与 `inline` 可选参数，默认行为不变；验证：`npm run typecheck`。
- [x] 1.2 `QuestionCard` 题干改用 Markdown，选项 `body` 改用 inline Markdown；验证：题干中的 `**`、`- ` 按格式渲染。

## 2. 作答反馈

- [x] 2.1 `lib/api.ts` 补全 `grade` 返回类型（`is_correct`、`explanation`、`mastery_after`、`gate_cleared`）；验证：`npm run typecheck`。
- [x] 2.2 会话页把判分结果组合为助手消息追加到 `items`，失败时显示错误文案；验证：作答后出现结论与解析气泡，且可复制。
- [x] 2.3 补齐中英文 i18n：结论（correct / partial / incorrect）、已掌握提示、判分失败；验证：切换语言无缺失键。

## 3. 输入区样式

- [x] 3.1 作答区改为圆角容器 + textarea + 圆形发送按钮，保留 Enter 提交、Shift+Enter 换行、空内容禁用；验证：与底部输入框观感一致。

## 4. 验证

- [x] 4.1 `web/` 下 `npm run typecheck` + `npm run build` 通过。
- [x] 4.2 `.\scripts\verify.ps1 -Change 0028-quiz-answer-parity` 通过，产出 `docs/verify/0028-quiz-answer-parity.md`。

## Workflow follow-up

- 追加 `docs/regression-log.md`。
- 新增 `docs/ai-collaboration/0028-quiz-answer-parity.md` 留痕。
- 人类审查证据后执行 `openspec archive 0028-quiz-answer-parity`。
