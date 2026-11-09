# Proposal: 修复工作区页面无法纵向滚动

## Why

`AppShell` 把页面内容放在 `main > div.overflow-hidden` 里；`LearningShell`、`SpaceWorkspace`、
`KnowledgeBaseList` 等页面自己没有滚动容器，内容一高就被裁掉，窗口无法上下滑动。掌握之路首页、
学习空间、知识库、部分设置入口都会受影响。

## What Changes

- 把 `AppShell` 的内容容器从 `overflow-hidden` 改为 `overflow-y-auto`，让自然流页面可以滚动。
- 已有内部滚动的页面（`ChatWorkspace`、掌握之路会话页、`SettingsShell`）保持原样：
  它们的 `h-full` + `overflow-y-auto` 仍然工作，不会出现双滚动条。
- 不改任何 REST/WS/领域行为；不改页面布局与交互。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。`skip_specs: true`：这是纯 UI 容器修复，没有规范级行为变化。

## Impact

- 修改：`web/components/layout/AppShell.tsx`。
- 受影响页面：所有 `(workspace)` 路由（学习图集、学习空间、知识库等）。
- 不受影响：后端、数据库、`settings` 路由（自带滚动容器）。
