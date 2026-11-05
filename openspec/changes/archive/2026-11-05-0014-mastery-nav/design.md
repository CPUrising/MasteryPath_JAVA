# Design: 只读导航与 hand-off 卡片

## Context

参考实现两处：

| 文件 | 内容 | 行数 |
| --- | --- | --- |
| `learning/navigation.py` | `topic_cards` / `find_topic` / `resolve_module` / `topic_sessions` + `_counts` / `_modules` | 170 |
| `capabilities/mastery/nav_tools.py` | 4 个工具类 + `_handoff` / `_topic` / `_result` / `_failure` | 221 |

本 change 只实现**数据构造**（`navigation.py` 全部 + `nav_tools.py` 的 `_handoff`），
工具类的参数解析与失败文案属 `0015`。

Java 侧 `LearningStore` 已有 `load` / `listPaths` / `listSessions`，
`MasteryPolicy` 已有 `mapSummary` / `dueReviews`，`LearningProgress` 已有
`getPendingQuestion` / `getModules` / `getOwnerSessionId`。本 change 不碰存储层。

## Goals / Non-Goals

**Goals**

1. `topicCards` 的筛选、截断、`modules_omitted` / `topics_omitted` 条件键逐字复刻。
2. `findTopic` 的「空 map 视为不存在」与 `path_id` 的 `strip()`。
3. `resolveModule` 的三级匹配（id → name → 1 基序号）与**逐模块**顺序。
4. `topicSessions` 的 `archived` 跳过、`awaiting_answer` 复合条件、`last_message` 截 240。
5. `handoff` 的 17 键与 `or` 默认值语义。

**Non-Goals**

1. 不做工具层（`0015`）：`MasteryTopicsTool` 等的 `ok=false` 文案不在本 change。
2. 不做 REST（`0016`）。
3. 不改 `LearningStore` / `MasteryPolicy`。

## Decisions

1. **`topicCards` 跳过 `total <= 0` 的路径**
   - 参考实现：`if counts["total"] <= 0: continue`。
   - 与 `0013` 的 `listPathOverviews` 同一机制（空 map 隐藏），但这里用 `counts["total"]`
     而非 `summary.isEmpty()`——两者等价，因为 `total = len(summary)`。

2. **`topicCards` 的 `haystack` 只含名称、目标、模块名**
   - 参考实现：`" ".join([name or path_id, goal, *(m["name"] for m in modules)]).casefold()`。
   - **不含** `path_id`（当 `name` 非空时）、不含模块 `objective`、不含知识点名。
   - 后果：搜 `"p1"` 找不到名为 `Statistics` 的路径。照抄。

3. **`needle` 与 `haystack` 都做「折叠空白 + 小写」**
   - `needle = " ".join(str(query or "").split()).casefold()`。
   - Java：`collapseWhitespace(query).toLowerCase(Locale.ROOT)`。
   - 已知差异：Python `casefold()` 与 `toLowerCase(Locale.ROOT)` 对 `ß` 等少数场景不同，
     与 `Grading.normalize` 同一处已登记差异（`migration-plan.md` §11）。

4. **`query` 键只在原始 query 为真值时出现**
   - 参考实现：`**({"query": query} if query else {})`。
   - 注意用的是**原始** `query`（未折叠、未小写），不是 `needle`。
   - 因此 `query="  "` 时：`needle=""`（不过滤），但 `query` 键**出现**且值为 `"  "`。

5. **`modules_omitted` / `topics_omitted` 是条件键**
   - 只在超出上限时出现，值为超出数量。不出现时**不是** `0`，而是**没有这个键**。
   - `total_topics = len(cards) + clipped`，即截断前的总数。

6. **`findTopic` 的 `path_id` 先 `strip()` 再 `load`**
   - 参考实现：`store.load(str(path_id or "").strip()) if path_id else None`。
   - 空 `path_id` 直接返回 `null`，**不查库**。
   - `findTopic` 的 `modules` **不截断**（与 `topicCards` 的 40 上限不同）。

7. **`resolveModule` 逐模块检查三个条件，命中即返回**
   - 顺序：`module_id`（casefold 相等）→ `name`（casefold 相等）→ `int(ref.rstrip(".")) == order + 1`。
   - **不是**「先扫所有 id，再扫所有 name」——是每个模块依次试三种。
   - 序号是 **1 基**（`order + 1`），因为学习者说「第 1 课」。
   - `ref` 先折叠空白；空 `ref` 返回 `null`。

8. **`topicSessions` 跳过 `preferences.archived` 为真值的会话**
   - 参考实现：`if preferences.get("archived"): continue`。
   - Python 真值语义：`True` / 非空字符串 / 非零数字 / 非空容器都为真。
     Java 需 `truthy(Object)` 辅助，不能只判 `Boolean.TRUE`。

9. **`awaiting_answer` 是复合条件**
   ```
   pending != null AND pending.question_id 非空 AND session.has_pending_question 为真
   ```
   - 三者缺一为 `false`。注意 `pending` 来自**路径聚合**（全局单槽位），
     而 `has_pending_question` 来自**会话行**——两者都要满足。

10. **`topicSessions` 的 `total_sessions` 是截断前的总数**
    - `total = len(rows)` 在 `rows[:20]` **之前**计算。
    - `sessions_omitted` 只在 `total > 20` 时出现。

11. **`handoff` 的 17 键与 `or` 默认值**
    ```
    kind, path_id, path_name, emoji(""), session_id, session_title,
    session_messages, session_updated_at, session_awaiting, session_running,
    module_id, module_name, opening_message, reason,
    due_reviews, mastered, objectives
    ```
    - `session_*` 五键在 `session == null` 时取默认：`""` / `""` / `0` / `0.0` / `false` / `false`。
    - `module_*` 两键在 `module == null` 时取 `""`。
    - `due_reviews` / `mastered` / `objectives` 来自 `topic`，`or 0` 兜底。
    - `emoji` 恒为 `""`（参考实现留空，前端自行决定图标）。

12. **`handoff` 不写库、不发事件**
    - 参考实现 `_handoff` 是纯函数，只返回 dict。
    - 本 change 的 `NavigationService` 全部方法**只读**：不调用 `mutate` / `create` / `delete`。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `haystack` 误加 `path_id` | 搜 `"p1"` 会命中名为 `Statistics` 的路径，与参考实现不一致 | 用例断言「按 path_id 搜不到」 |
| `modules_omitted` 恒出现（值为 0） | 前端会渲染「省略 0 个」 | 用例断言未超限时**无此键** |
| `resolveModule` 先扫全部 id 再扫 name | 模块 A 的 name 与模块 B 的 id 同名时结果不同 | 用例构造该冲突 |
| 序号用 0 基 | 「第 1 课」解析失败 | 用例断言 `"1"` → 第一个模块 |
| `archived` 只判 `Boolean.TRUE` | 库里存 `"true"` 字符串时不过滤 | 用例用字符串 `"true"` 断言被跳过 |
| `awaiting_answer` 只看 `pending` | 别的会话在等作答时误报 | 用例断言 `has_pending_question=false` 时为 `false` |
| `total_sessions` 用截断后长度 | 前端显示「共 20 个」而非真实总数 | 用例构造 21 个会话 |
| `casefold` 与 `toLowerCase` 差异 | `ß` 等少数场景 | 已登记 `migration-plan.md` §11 |
| `int(ref.rstrip("."))` 的 Python 宽松解析 | `"1_0"` 在 Python 是 10，Java `parseInt` 失败 | 已知差异，登记到本 change 的 verify 报告；实际输入为「第 N 课」不会出现下划线 |

## Migration Plan

1. `NavigationService` 骨架 + `counts` / `modules` / `collapseWhitespace` / `truthy` 辅助。
2. `topicCards` + `findTopic`。
3. `resolveModule` + `topicSessions`。
4. `handoff`。
5. 测试：`NavigationTest`。
6. `verify.ps1 -Scope unit` → 全量 → `pytest tests/test_mastery_nav.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/navigation.py` 与
  `capabilities/mastery/nav_tools.py:43-70` 为唯一依据。
- 参考实现测试空白（本 change 会补）：
  - `topicCards` 的 `modules_omitted` / `topics_omitted` 条件键；
  - `topicCards` 的 `query` 键只在真值时出现；
  - `findTopic` 的空 map 视为不存在；
  - `resolveModule` 的三级匹配与 1 基序号；
  - `topicSessions` 的 `archived` 跳过与 `awaiting_answer` 复合条件；
  - `handoff` 的 17 键形状与 `session == null` 默认值。
