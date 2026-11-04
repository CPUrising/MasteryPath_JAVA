# AI 协作留痕：0014-mastery-nav

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/service/NavigationService.java`（新增）、
  `src/test/java/com/masterypath/service/NavigationTest.java`（新增）、
  `scripts/verify.ps1`（`-Scope unit` 补 `*NavigationTest`）、
  `openspec/changes/0014-mastery-nav/**`、`docs/**`。
- 明确不做的事：工具层 4 个 `Mastery*Tool` 及其失败文案（`0015`）、REST 端点（`0016`）、
  前端卡片渲染（不在后端范围）。
- 出处：用户指令原文「好，提交后继续实现计划」；`docs/migration-plan.md` §5 第 14 行（拆分后）。

## 2. AI 产出

- 新增文件：`NavigationService` —— `topicCards` / `findTopic` / `resolveModule` /
  `topicSessions` / `handoff` + 辅助（`counts` / `modules` / `collapseWhitespace` /
  `stripTrailingDots` / `truthy` / `truncate`）
- 新增测试：`NavigationTest`（26）
- 关键决策：
  - **`haystack` 只含名称、目标、模块名**：不含 `path_id`（当 `name` 非空时）、
    不含模块 `objective`、不含知识点名。搜 `"p1"` 找不到名为 `Statistics` 的路径。
  - **`query` 键只在原始 query 为真值时出现**，值为**原始** query（未折叠、未小写）。
    因此 `query="  "` 时：`needle=""`（不过滤），但 `query` 键出现且值为 `"  "`。
  - **`modules_omitted` / `topics_omitted` 是条件键**：不出现时**没有这个键**，不是 `0`。
  - **`resolveModule` 逐模块试三种匹配**，不是「先扫全部 id 再扫全部 name」。
    序号是 **1 基**（`order + 1`），因为学习者说「第 1 课」。
  - **`truthy` 必须实现 Python 真值语义**：库里可能存字符串 `"true"` 或数字 `1`，
    只判 `Boolean.TRUE` 会漏掉 `archived` 过滤。
  - **`awaiting_answer` 是复合条件**：路径有 pending + pending 有 `question_id` +
    该会话 `has_pending_question`，三者缺一为 `false`。
  - **`total_sessions` / `total_topics` 是截断前总数**，不是返回列表长度。
  - **本层从不写库**：`NavigationService` 不调用 `mutate` / `create` / `delete`。

## 3. 人工修正点

- 无人工修正。本 change 一次通过（unit 417 / 全量 504，0 失败）。
- 主动澄清一处编号冲突：`docs/traceability.md` 里已有的 `TC-N1`–`TC-N6` 是**大纲覆盖报告**
  的场景；参考实现 `test_mastery_nav.py` 首行只声明 `TC-N*` 家族（未细分）。
  为避免两套 `TC-N*` 混淆，本 change 的导航分组改用 `TC-NAV1`–`TC-NAV5`，
  并在 verify 报告 §2 显式说明。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 26 / 0；全量 504 / 0（478 → 504）。
- 证据链接：[`docs/verify/0014-mastery-nav.md`](../verify/0014-mastery-nav.md)
- 日志：`artifacts/verify-0014-mastery-nav-20261104-194559.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_mastery_nav.py -q` → **4 passed**。
- 跨语言数据等价：真实 Python 探针输出与 Java 断言逐条一致（见 verify 报告 §3）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `haystack` 是否含 `path_id` | **不含** | 参考实现 `[name or path_id, goal, *(m["name"] ...)]` |
| `query` 键用原始值还是折叠值 | **原始值** | 参考实现 `**({"query": query} if query else {})` |
| 条件键不出现 vs 值为 0 | **不出现** | 参考实现 `**({...} if clipped else {})` |
| `resolveModule` 的匹配顺序 | **逐模块** id → name → 序号 | 参考实现 for 循环内三个 if |
| 序号是 0 基还是 1 基 | **1 基** | 参考实现 `int(module.get("order", -1)) + 1` |
| `archived` 只判 `Boolean.TRUE` | **不行，要 Python 真值** | 参考实现 `if preferences.get("archived")` |
| `awaiting_answer` 只看 `pending` | **不行，三条件相与** | 参考实现 `pending is not None and pending.question_id and session.get("has_pending_question")` |
| `total_sessions` 用截断前还是后 | **截断前** | 参考实现 `total = len(rows)` 在切片之前 |
| `findTopic` 的 `modules` 是否截断 | **不截断** | 参考实现 `find_topic` 直接 `_modules(progress)` |
| 导航层是否写库 | **不写** | 参考实现 `navigation.py` 无任何写操作 |
| 工具层 4 个 `Tool` 类是否本 change 实现 | **不实现** | 属 `0015`；数据构造已全部覆盖 |
