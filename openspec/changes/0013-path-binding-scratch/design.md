# Design: 路径绑定与 scratch

## Context

参考实现四处（`learning/service.py`）：

| 方法 | 行号 | 行数 |
| --- | --- | --- |
| `list_path_overviews` | 73–97 | 25 |
| `ensure_scratch_path` | 99–130 | 32 |
| `bind_session_path` | 132–136 | 5 |
| `delete_session` | 1014–1029 | 16 |
| `list_sessions` / `get_session` | 1005–1012 | 8 |

Java 侧 `LearningStore` 已具备 `listPaths` / `getSession` / `listSessions` /
`setSessionPath` / `deleteSession` / `deletePath`，`LearningProgress.ownerSessionId`
字段也已存在。本 change 只写服务层编排。

## Goals / Non-Goals

**Goals**

1. `listPathOverviews` 的 9 个键与排序逐字复刻，**空 map 的路径必须跳过**。
2. `ensureScratchPath` 的 id 派生规则、owner 认领、建档事件逐字复刻。
3. `deleteSession` 的三重条件（owner 匹配 + 无知识点 + 无其他会话）逐字复刻。
4. `bindSessionPath` 的空 session 短路。

**Non-Goals**

1. 不做工具层（`0015`）。
2. 不做只读导航与 hand-off（`0014`）。
3. 不改 `LearningStore`（已有方法够用）。

## Decisions

1. **`listPathOverviews` 跳过空 map 的路径**
   - 参考实现：`if not summary: continue`。
   - 这是 scratch 路径「隐藏」的**唯一机制**——scratch 没有知识点，`mapSummary` 返回空 map，
     因此不出现在列表里。不需要额外的 `is_scratch` 标志。
   - 后果：一条**建过大纲但被清空**的普通路径也会被隐藏。这是参考实现原样行为，照抄。

2. **`listPathOverviews` 的 `name` 回退到 `path_id`**
   - 参考实现：`progress.name or progress.path_id`。
   - Java 侧 `LearningProgress.name` 默认是 `""`，所以空名时用 `pathId`。

3. **`listPathOverviews` 按 `path_id` 升序排序**
   - 参考实现：`rows.sort(key=lambda item: item["path_id"])`。
   - 不是按创建时间，也不是按名称。照抄。

4. **`ensureScratchPath` 的 id 派生规则**
   ```
   safe = re.sub(r"[^A-Za-z0-9_-]", "_", session_id).strip("_")
   path_id = "scratch-" + (safe or "default")
   ```
   - 注意 `strip("_")` 在**替换之后**：`"a b"` → `"a_b"`（保留），`"!!!"` → `"___"` → `""` → `"default"`。
   - Java 用 `replaceAll("[^A-Za-z0-9_-]", "_")` + 手写 `strip('_')`（Java 的 `String.strip()`
     只去空白，不去下划线）。

5. **`ensureScratchPath` 的「认领」分支不发事件**
   - 参考实现：已存在但 `owner_session_id` 为空时，单独 `mutate` 只写 owner，**不 emit**。
   - 理由：路径已存在，`path.created` 已经发过了；再发一次会让事件流出现重复的创建事件。
   - 注意：认领分支**不检查** `session_id` 是否为空——但外层 `if not existing.owner_session_id and session_id`
     有检查，所以空 session 不会触发认领。

6. **`ensureScratchPath` 建档时 `name="Scratch"`、`goal=""`**
   - 参考实现：`tx.progress.name = "Scratch"`，`goal` 保持默认 `""`。
   - 事件 payload 是 `{goal: "", name: "Scratch", scratch: true}`，带 `session_id`（不带 `turn_id`）。

7. **`bindSessionPath` 空 session 返回 `null`，不写库**
   - 参考实现：`if not session_id: return None`。
   - 理由：没有会话就没有「绑定」的对象；写库会产生孤儿记录。

8. **`deleteSession` 的三重条件必须同时满足才删路径**
   ```
   progress.owner_session_id == session_id
   AND 没有任何模块含知识点
   AND 该 path 已无其他会话
   ```
   - 顺序：先读 session → 读其 path → **删 session** → 再判断是否删 path。
   - 注意「删 session 之后」再查 `listSessions(path_id)`，所以「无其他会话」自然成立
     （刚删的那个已不在）。这个顺序是参考实现原样，照抄。
   - 若 session 不存在，返回 `false`（参考实现 `if session is None: return False`）。

9. **`deleteSession` 返回 `true` 即使没删路径**
   - 参考实现：删了 session 就 `return True`，路径是否删除不影响返回值。
   - 语义：返回值表示「会话删除成功」，不是「路径也删了」。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 用 `is_scratch` 标志而非「空 map」隐藏 | 与参考实现不一致；建过大纲的 scratch 会显示 | 用例断言「空 scratch 不出现」「建过大纲的 scratch 出现」 |
| `strip("_")` 用 Java 的 `strip()` | `"___"` 不会被清空，得到 `scratch-___` 而非 `scratch-default` | 用例断言 `"!!!"` → `scratch-default` |
| 认领分支发事件 | 事件流出现重复 `path.created` | 用例断言认领时事件数不变 |
| `deleteSession` 先删路径再删会话 | 外键/级联顺序错乱 | 用例断言顺序：会话先消失 |
| 删路径条件少判一条 | 误删有知识点的路径 | 用例断言「有知识点时不删路径」 |
| `bindSessionPath` 空 session 写库 | 孤儿会话记录 | 用例断言返回 `null` 且库无变化 |

## Migration Plan

1. `listPathOverviews` + `ensureScratchPath`。
2. `bindSessionPath` + `deleteSession` + `listSessions` / `getSession`。
3. 测试：`PathBindingTest`。
4. `verify.ps1 -Scope unit` → 全量 → `pytest tests/test_path_binding.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/learning/service.py:73-136, 1005-1029` 为唯一依据。
- 参考实现测试空白（本 change 会补）：
  - `list_path_overviews` 的 `name` 回退到 `path_id`；
  - `ensure_scratch_path` 的 owner 认领分支（不发事件）；
  - `ensure_scratch_path` 的 id 派生边界（`"!!!"` → `default`）；
  - `delete_session` 的「有知识点时不删路径」；
  - `bind_session_path` 的空 session 短路。
