# Proposal: 只读导航与 hand-off 卡片

## Why

`0013` 让会话能**绑定**到某条路径，但学习者与导师还缺少「看」的能力：
导师需要先列出学习者有哪些课程、某条课程下有哪些会话、某个模块叫什么，
才能把学习者「送」过去。参考实现用 `ADR-022` 定义了一层**只读**导航：

1. `topic_cards` —— 列出可返回的课程（含模块大纲、进度、到期复习）；
2. `find_topic` —— 按 `path_id` 取单条课程；
3. `resolve_module` —— 把「第 1 课」「Statistics basics」「m1」解析成模块；
4. `topic_sessions` —— 列出某课程下的会话（标题、消息数、是否在等作答）；
5. `_handoff` —— 生成一张「交接卡」payload，由前端决定学习者是否接受。

这一层**从不写库**：路径与会话的生命周期不同，导航只读。

## What Changes

- 新增 `service/NavigationService`：
  - `topicCards(query)`：课程卡片列表（`TOPIC_LIMIT=20`、`MODULE_LIMIT=40`）
  - `findTopic(pathId)`：单条课程，空 map 或不存在返回 `null`
  - `resolveModule(topic, moduleRef)`：按 id / 名称 / 序号（1 基）解析
  - `topicSessions(pathId)`：会话列表（`SESSION_LIMIT=20`，跳过 `archived`）
  - `handoff(kind, topic, module, openingMessage, reason, session)`：17 键交接卡

## Capabilities

### New Capabilities

无新增能力；实现 `specs/learning-domain` 的只读导航扩展（该规范未单列导航章节，
行为以参考实现 `learning/navigation.py` 与 `capabilities/mastery/nav_tools.py` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0013` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/service/NavigationService.java`（新增）
  - `src/test/java/com/masterypath/service/NavigationTest.java`（新增）
  - `scripts/verify.ps1`（`-Scope unit` 补 `*NavigationTest`）
- 影响测试：既有 478 个用例**不修改**。
- 明确不做（Out of scope）：
  - 工具层 `MasteryTopicsTool` / `MasterySessionsTool` / `MasteryOpenSessionTool` /
    `MasteryNewSessionTool` 及其**失败文案**（`path_id is required; ...` 等）→ 属 `0015`。
    本 change 只实现这些工具背后的**数据构造**（`topicCards` / `topicSessions` / `handoff`）。
  - REST 端点（`GET /learning/topics` 等）→ 属 `0016`。
  - 前端卡片渲染 → 不在后端范围。
