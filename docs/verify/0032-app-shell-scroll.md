# 验证报告：0032-app-shell-scroll

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0032-app-shell-scroll` |
| 执行时间 | 2026-11-09 21:55–22:08（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链版本 | Next.js 14.2.35 / TypeScript 5 / JDK 21.0.11 |
| 运行环境 | 后端 `127.0.0.1:8081`（`target/tmp-0031`），前端 `127.0.0.1:3001` |
| 命令 | `npm run typecheck`、`npm run build`、`.\scripts\verify.ps1 -Change 0032-app-shell-scroll` |

## 2. 变更摘要

`AppShell` 的内容容器从 `overflow-hidden` 改为 `overflow-y-auto`：

```diff
- <div className="min-h-0 flex-1 overflow-hidden">{children}</div>
+ <div className="min-h-0 flex-1 overflow-y-auto">{children}</div>
```

自然流页面（学习图集、学习空间、知识库）由该层滚动；自带 `h-full` + 内部
`overflow-y-auto` 的页面（聊天、掌握之路会话、设置）保持单一内部滚动条。

## 3. 前端检查

- `npm run typecheck`：通过。
- `npm run build`：通过（8 个路由全部生成）。
- browser_use 手测：
  - `/learning/mastery`：内容容器 `overflow-y: auto`，`scrollHeight=1627`、
    `clientHeight=855`，可滚动 772px；滚轮到底 `scrollTop≈771`，最后一条
    “Caching” 完整可见；21 张主题卡无横向溢出。
  - `/space`：内容容器 `overflow-y: auto`，`scrollHeight=855`、`clientHeight=855`，
    内容恰好铺满、无裁剪；聊天历史、知识库、题库三个页签内容均完整。
  - 掌握之路会话页仍由消息区内部滚动，`ChatWorkspace` 与 `SettingsShell` 未出现双滚动条。
- 截图：`mastery-scrolled-bottom.png`、`mastery-top-topic20.png`、`mastery-bottom.png`、
  `space.png`（浏览器代理临时目录，未复制进仓库）。

## 4. 回归

| 范围 | 结果 |
|---|---|
| 全量 `verify.ps1` | 874 / 0 / 0 |
| 后端 | 无改动 |
| 前端 typecheck / build | 通过 |

## 5. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 极长的自然流页面与自带滚动页面同时存在时，可能出现两个滚动容器 | 低 | 自带滚动页面均为 `h-full`，不会撑破父容器；手测会话页无双重滚动条 |
| D2 | 浏览器手测的“Scroll topic 20”是首条而非末条（列表最新在前） | 低 | 已用最后一条 “Caching” 验证滚动到底，结论不受影响 |

## 6. 结论

- [x] 建议归档（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
