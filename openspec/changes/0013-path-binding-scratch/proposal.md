# Proposal: 路径绑定与 scratch

## Why

一个会话（conversation）需要知道「它现在跟着哪条学习路径」。参考实现用
`ADR-021` 定义了三件事：

1. **列出可切换的路径**（`list_path_overviews`）——只列**已建过大纲**的路径，
   空 scratch 不出现；
2. **scratch 路径**（`ensure_scratch_path`）——每个会话有一个私有草稿路径，
   学习者「离开」某条路径后落到这里，可以重新开始；
3. **会话绑定**（`bind_session_path`）——把会话指向另一条路径，从下一回合生效。

没有这一层，`mastery_switch` / `mastery_leave` 两个工具无法工作，
学习者也无法在多条路径之间切换。

## What Changes

- 新增 `LearningService.listPathOverviews()`：可切换路径列表（含 `owner_session_id`）
- 新增 `LearningService.ensureScratchPath(sessionId)`：按会话 id 派生 `scratch-<safe>`，
  已存在则认领 owner，不存在则建档并发 `path.created{scratch:true}`
- 新增 `LearningService.bindSessionPath(sessionId, pathId)`：空 session 返回 `null`
- 新增 `LearningService.deleteSession(sessionId)`：删会话 + 清理「自己拥有的空 scratch」
- 新增 `LearningService.listSessions(pathId)` / `getSession(sessionId)`：存储层转发

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-domain` 的路径生命周期扩展（该规范未单列
scratch 章节，行为以参考实现 `service.py:73-136, 1005-1029` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0012` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/service/LearningService.java`（+6 个公开方法）
  - `src/test/java/com/masterypath/service/PathBindingTest.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*PathBindingTest`）
- 影响测试：既有 456 个用例**不修改**。
- 明确不做（Out of scope）：
  - 工具层 `MasteryPathsTool` / `MasterySwitchTool` / `MasteryLeaveTool` → 属 `0015`。
    参考实现这三个类共 91 行，只做「参数解析 + 服务层调用 + `ToolResult` 包装」。
    本 change 用服务层用例覆盖同样的语义（与 `0012` 同理由）。
  - 只读导航（`topics` / `sessions` / `module` 解析）+ hand-off 卡片 → 属 `0014`。
  - `test_turn_path_switch.py`（TC-S5）→ 依赖 WS，属 `0017`。
