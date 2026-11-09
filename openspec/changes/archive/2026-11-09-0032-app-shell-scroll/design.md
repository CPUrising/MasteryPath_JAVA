# Design: 修复工作区页面无法纵向滚动

## 1. 根因

```
AppShell
  <div class="flex h-dvh overflow-hidden">
    <main class="... overflow-hidden">
      <div class="min-h-0 flex-1 overflow-hidden">{children}</div>
```

`main` 与内容容器都把溢出裁掉；页面如果没有自己的滚动容器，超出视口的内容不可达。
`LearningShell` / `SpaceWorkspace` / `KnowledgeBaseList` 都是自然流布局，没有内部滚动。

## 2. 决策

把内容容器改为 `overflow-y-auto`：

```
<div class="min-h-0 flex-1 overflow-y-auto">{children}</div>
```

- 自然流页面：内容高度超过容器时，由这一层滚动。
- 自带滚动的页面（`ChatWorkspace`、掌握之路会话页、`SettingsShell`）：子节点是 `h-full`，
  正好占满容器高度，不会触发外层滚动；内部 `overflow-y-auto` 继续负责滚动。
- 不给每个页面单独加滚动容器，避免以后新增页面再次漏掉。

## 3. 兼容性

- 不改 `main` 的 `overflow-hidden`：侧栏与页面框架仍固定，只有内容区滚动。
- 横向溢出仍由页面自己处理（如表格的 `overflow-x-auto`）。
- 不引入 `100vh` 回退或 JS 计算，保持纯 CSS。

## 4. 验证

- `npm run typecheck`、`npm run build` 通过。
- 浏览器：在 `/learning/mastery` 创建足够多的主题使页面超过一屏，确认鼠标滚轮/触控板可滚动；
  在掌握之路会话页确认消息区仍只出现一个滚动条；在 `/space`、`/knowledge-bases` 确认页面可滚动。
- `.\scripts\verify.ps1 -Change 0032-app-shell-scroll` 全量通过（后端不受影响）。
