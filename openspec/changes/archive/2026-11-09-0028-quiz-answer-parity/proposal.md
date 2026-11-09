# Proposal: 题卡作答与对话框回答效果对齐

## Why

掌握之路的题目卡片与底部对话框是两个不同的作答入口，但表现不一致：

1. **作答后没有反馈**：`sessions/page.tsx` 调用了 `POST /learning/paths/{pathId}/grade`，
   却丢弃了响应里的 `result` / `is_correct` / `explanation` / `gate_cleared`，卡片直接消失，
   学习者不知道对错，也看不到解析。而在底部对话框提问会得到流式回答。
2. **题目文本不渲染 Markdown**：`QuestionCard` 用 `<p>` 直接输出 `question.prompt` 与选项 `body`，
   模型给出的 `**加粗**`、`- 列表`、行内代码会以原始符号显示；对话框消息已走 `MarkdownMessage`。
3. **作答输入区样式不同**：题卡是单行 `input` + 方形「提交」按钮；底部是圆角容器 + 多行 `textarea`
   + 圆形发送按钮 + 工具栏，两者观感割裂。

## What Changes

- **作答反馈**（复用既有后端返回值，不新增端点）
  - 判分成功后，把 `status` / `result` / `explanation` / `gate_cleared` 组合成一条助手消息，
    追加到当前会话的消息流，复用 `MessageBubble`（Markdown + 复制），与对话框回答一致。
  - 判分失败（422 / 网络错误）同样以助手消息显示失败原因，不再静默吞掉。
- **题目文本**
  - `MarkdownMessage` 增加 `className` 与 `inline` 两个可选参数；`inline` 用 `<span>` 容器并去掉 `<p>` 包裹，
    便于放进选项按钮。
  - `QuestionCard` 的题干改用 Markdown 渲染；选项 `body` 用 inline Markdown 渲染。
- **作答输入区**
  - 改用与 `MessageComposer` 一致的视觉：圆角边框容器、`focus-within` 高亮、无边框 `textarea`、
    圆形 `primary` 发送按钮（`ArrowUp` 图标）。
  - 保留 Enter 提交、Shift+Enter 换行、空内容禁用。
- **i18n**：新增判分结论与失败文案（中/英）。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。`skip_specs: true`：REST 路径、JSON 字段与判分规则均不变，本次只是把已有响应正确显示出来。

## Impact

- 影响代码（均在本仓库 `web/`）：
  - `web/components/common/MarkdownMessage.tsx`
  - `web/components/learning/QuestionCard.tsx`
  - `web/app/(workspace)/learning/mastery/[pathId]/sessions/page.tsx`
  - `web/lib/api.ts`（`grade` 返回类型补全）
  - `web/i18n/{zh,en}.json`
- 不受影响：Java 后端、数据库、`openspec/specs/` 基线。
- 范围说明：`openspec/project.md` 的 Out of scope 为「不重写前端、不新增产品功能」。
  本次不重写前端、不新增后端能力，只修复已有数据的展示，属于 UI 缺陷修复。
- 已知限制：反馈消息为本地追加，刷新页面后不保留（后端 `interactions` 已记录判分结果，
  但会话消息表未写入）。本次不扩展会话消息契约。
- 明确不做：
  - 不改变判分算法、提示预算与掌握门槛。
  - 不为掌握之路启用消息撤回（导师会话无对应撤回端点）。
