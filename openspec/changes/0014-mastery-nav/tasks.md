# Tasks

## 1. 课程卡片与单条查询

- [x] 1.1 `NavigationService` 骨架 + 常量（`TOPIC_LIMIT=20` / `SESSION_LIMIT=20` / `MODULE_LIMIT=40`）+ 辅助（`counts` / `modules` / `collapseWhitespace` / `truthy`）
- [x] 1.2 `topicCards(query)`：跳过空 map、`haystack` 只含名称/目标/模块名、`query` 键条件出现、`modules_omitted` / `topics_omitted` 条件键
- [x] 1.3 `findTopic(pathId)`：`strip()` 后查库、空 map 返回 `null`、`modules` 不截断

## 2. 模块解析与会话列表

- [x] 2.1 `resolveModule(topic, moduleRef)`：逐模块三级匹配（id → name → 1 基序号）、空 ref 返回 `null`
- [x] 2.2 `topicSessions(pathId)`：跳过 `archived`、`awaiting_answer` 复合条件、`last_message` 截 240、`total_sessions` 为截断前总数

## 3. hand-off 卡片

- [x] 3.1 `handoff(...)`：17 键、`session == null` / `module == null` 的默认值、`emoji` 恒空

## 4. 测试与验证

- [x] 4.1 `NavigationTest`：卡片筛选/截断/条件键、`findTopic` 空 map、`resolveModule` 三级匹配、`topicSessions` 过滤与截断、`handoff` 形状（26 用例）
- [x] 4.2 `.\scripts\verify.ps1 -Scope unit -Change 0014-mastery-nav`（417 通过）
- [x] 4.3 全量 `.\scripts\verify.ps1 -Change 0014-mastery-nav`（504 通过）
- [x] 4.4 与参考实现对照（`pytest tests/test_mastery_nav.py -q` → 4 passed）
- [x] 4.5 回填 `docs/verify/0014-mastery-nav.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0014-mastery-nav.md`、`README.md`
