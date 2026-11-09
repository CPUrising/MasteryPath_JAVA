# AI 协作留痕：0028-quiz-answer-parity

## 1. prompt 范围

- 授权修改的文件（均在本仓库 `web/`）：
  - `web/components/common/MarkdownMessage.tsx`
  - `web/components/learning/QuestionCard.tsx`
  - `web/app/(workspace)/learning/mastery/[pathId]/sessions/page.tsx`
  - `web/lib/api.ts`
  - `web/i18n/zh.json`、`web/i18n/en.json`
- 明确不做的事：
  - 不改 Java 源码、REST/WS 契约、判分算法与 `interactions` 存储。
  - 不把判分反馈写入会话消息（刷新后不保留，人类已确认）。
  - 不在题卡中加入模型/知识库/附件工具栏。
  - 不为导师会话启用消息撤回。
- 出处：用户提问「出的题目的框内回答与对话框回答效果似乎不同？」，
  并在结构化选择中同时勾选「作答后无反馈」「题目文本不渲染 Markdown」「作答输入框样式不同」。

## 2. AI 产出

- `MarkdownMessage`：
  - 新增 `className`（覆盖字号/字重）与 `inline`（`<span>` 容器，`p` 映射为 Fragment）两个可选参数；
  - 默认路径不变，聊天与导师气泡输出保持原样。
- `QuestionCard`：
  - 题干改用 Markdown；选项 `body` 用 inline Markdown；
  - 短答区改为圆角容器 + 无边框 `textarea` + 圆形 `ArrowUp` 发送按钮；
  - Enter 提交、Shift+Enter 换行、空内容禁用。
- 会话页：
  - `describeGrade` 把 `result` / `explanation` / `gate_cleared` 组合为文本；
  - `answerQuestion` 把结果作为 `assistant` 消息追加到 `items`，复用 `MessageBubble`；
  - 失败分支追加 `判分失败 (…)`，不再静默。
- `api.ts`：新增 `GradeOutcome` 并让 `grade` 返回该类型。
- i18n：中英各新增 6 个键（结论 3 + 未知 1 + 已掌握 1 + 失败 1）。

## 3. 人工修正点

1. **插入位置错误（自查发现）**：首次替换把 `useEffect(() => { void load(); }, [load])` 一并删掉，
   导致首屏不再拉取学习状态。已在新增回调之后补回该 `useEffect`，并用 `npm run build` 复核。
2. **范围收窄（主动）**：计划阶段曾考虑在 `api.grade` 请求体追加 `session_id`，
   但会改变 `LearningEvidence.sessionId` 的落库值，超出「只修展示」范围，最终保持原请求体。
3. **持久化取舍（人类决策）**：反馈仅前端显示；未扩展会话消息契约。

未出现「放宽断言」「删除用例」「伪造验证结果」的情况。

## 4. 回归结果

- 用例数 / 失败数：全量 **868 / 0**（Java 零改动，与上一基线相同）
- 证据链接：[`docs/verify/0028-quiz-answer-parity.md`](../verify/0028-quiz-answer-parity.md)
- 日志：`artifacts/verify-0028-quiz-answer-parity-20261109-165700.log`
- 前端：`npm run typecheck`、`npm run build` 均通过（手工命令，不计入 Java 用例数）

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 是否改后端以持久化反馈 | **否** | 人类选择「仅前端显示」；避免新增 spec 增量 |
| 反馈是否复用聊天气泡 | **是** | 需求是「与对话框回答效果一致」，复用 `MessageBubble` 才能保证样式/Markdown/复制一致 |
| `inline` Markdown 是否影响聊天 | **否** | 默认 `inline=false`，聊天与导师消息走原 `<div>` 路径 |
| 选项内 `<p>` 是否合法 | **已消除** | `inline` 模式把 `p` 映射为 Fragment，只输出行内节点 |
| `ungraded` 是否算错误答案 | **否** | 工具层转 422，前端仅展示「判分失败 + 原因」，不写判定结论 |
| 刷新后反馈消失是否可接受 | **是** | 人类确认；`interactions` 表仍有判分记录 |
