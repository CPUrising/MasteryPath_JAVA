# Design: 题卡作答与对话框回答效果对齐

## 1. 反馈消息的来源

`mastery_grade` 已经返回完整判定，只是前端丢弃了：

| 字段 | 含义 | 本次用途 |
| --- | --- | --- |
| `status` | `graded` / `already_graded` | 判断是否可直接展示 |
| `result` | `correct` / `partial` / `incorrect` | 结论行 |
| `is_correct` | 是否完全正确 | 与 `result` 冗余，不单独展示 |
| `explanation` | 题目解析（可能为空） | 解析段落 |
| `gate_cleared` | 本次是否新达成掌握 | 追加一行提示 |
| `mastery_before` / `mastery_after` | 掌握度前后值 | 本次不展示，避免制造噪声 |

`ungraded` 在 `StudyTools.Grade` 里转成 `ToolResult.failure`，`StudyController` 抛 422；
前端在 `catch` 中把错误文案作为反馈消息展示。

## 2. 反馈消息的渲染路径

```
QuestionCard.onAnswer
  → api.grade(...)                     // 422 会抛
  → setItems(prev => [...prev, { role: "assistant", text: feedback }])
  → load()                             // 刷新掌握度与下一题
```

- 反馈进入 `items` 后由 `MessageBubble` 渲染，因此自动获得 Markdown、复制与统一气泡样式，
  与底部对话框的回答视觉一致。
- `load()` 只更新 `state`，不会覆盖 `items`，所以反馈不会被刷掉。
- 反馈不写入后端消息表，因此刷新页面后消失；`interactions` 中仍有判分记录。
  这是本次接受的限制，已在 proposal 声明。

反馈文本结构：

```
<结论行>
(空行)
<explanation>
(空行)
<已掌握提示，仅 gate_cleared 时>
```

## 3. Markdown 的两种形态

`MarkdownMessage` 增加两个可选属性：

| 属性 | 默认 | 用途 |
| --- | --- | --- |
| `className` | `text-[14px] leading-relaxed` | 调用方覆盖字号/字重 |
| `inline` | `false` | 用 `<span>` 作容器并移除 `<p>` 包裹，供按钮内联使用 |

`inline` 只影响容器与段落包裹，GFM 表格/代码块等块级语法在选项里本就罕见，不做特殊处理。

## 4. 输入区样式对齐

`QuestionCard` 的作答区改为：

```
rounded-2xl border border-border bg-card p-2 shadow-sm
  focus-within:border-primary/40
  ├─ textarea（无边框、透明背景、可换行、Enter 提交）
  └─ flex justify-end → 圆形 h-8 w-8 bg-primary 按钮（ArrowUp）
```

与 `MessageComposer` 的圆角容器 + `focus-within` + 圆形发送按钮保持一致；
不在题卡里引入模型/知识库/附件工具栏（题目作答不需要这些控制）。

## 5. 兼容性

- 选择题仍点击选项即提交，行为不变。
- 短答/开放题仍支持 Enter 提交；新增 Shift+Enter 换行。
- 后端零改动，`verify.ps1` 结果应保持 868 用例全通过。
