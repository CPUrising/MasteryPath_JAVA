# Design: 工具层（19 个工具 + schema 表）

## Context

参考实现三处：

| 文件 | 内容 | 行数 |
| --- | --- | --- |
| `capabilities/mastery/tools.py` | 19 个工具 + `ToolContext` / `ToolResult` / `TOOL_PARAMETERS` / `mounted_tools` | 1095 |
| `capabilities/mastery/nav_tools.py` | 4 个导航工具 + `_handoff` | 191 |
| `learning/service.py` | 工具调用的服务层方法 | — |

`0016` 已提供 `Tool` / `ToolContext` / `ToolResult` / `ToolDefinitions`；
`0014` 已提供 `NavigationService`（`topicCards` / `findTopic` / `resolveModule` /
`topicSessions` / `handoff`）。本 change 只写工具本体与 schema。

## Goals / Non-Goals

**Goals**

1. 19 个工具的行为、文案、`ok` 语义逐字复刻。
2. 门控在调用期：模式专属工具先查 `ModeGate.toolIsAllowed`，被拒返回纠正文案。
3. 服务层异常 → `ToolResult(ok=false)`，回合永不崩。
4. `mountedTools(ctx)` 的条件挂载：无知识库不挂 `rag`，无来源不挂 `read_source`。
5. `ToolSchemas` 的 19 份 schema 与参考实现 `TOOL_PARAMETERS` 逐字一致。

**Non-Goals**

1. 不做 `TurnRunner` / WS（`0019`）。
2. 不做知识库服务本体（`0020`）。
3. 不做 REST（`0018`）。

## Decisions

1. **补齐服务层 4 个缺口，而不是在工具里直接操作 store**
   - `setSessionMode`：参考实现 `ensure_session` + 改 `mode` + `upsert_session`。
   - `proposeObjective` / `noteGap`：参考实现是 `LearningService` 的方法，不是工具内联。
   - `getOpenInteraction`：参考实现 `store.get_open_interaction(path_id)`；
     Java 侧只有 `Transaction.getOpenInteraction()`（事务内），需补存储层方法。

2. **`_wrong_mode_result` 的判定用原始模式值**
   - 参考实现：`mode.tool_is_allowed(tool_name, ctx.session_mode)`，
     内部用 `enforced_mode`（未记录 → `None` → 不门控，`INV-09`）。
   - Java：`ModeGate.toolIsAllowed(name, ctx.sessionMode())`。

3. **`mastery_status` 的 `status` 重写为 `empty`**
   - 参考实现：`get_state` 对无目标路径返回 `next=complete`，但导师视角不是「学完了」，
     而是「还没设计」。因此 `_state_payload` 把 `status` 改成 `empty` 并附 `message`。
   - 这是**唯一**会改写服务层返回值的地方，必须照抄。

4. **`mastery_mode` 的 `mode_changed` 判定**
   ```
   mode_changed = mode_changed or previous != requested or raw_previous is None
   ```
   - `raw_previous is None` 表示「之前没记录模式」，即使 `previous == requested` 也算变更。
   - 持久化失败**不**让工具失败：`persisted=false` + `persistence_error`，回合继续。

5. **`mastery_quiz` 的单槽位重放**
   - 已有未终态题目时**不新建**，而是重放同一张卡（`status=already_pending`），
     并调用 `endTurn()`。
   - 注册竞态（`registerQuestion` 返回 `null`）时同样重放。

6. **`mastery_quiz` 的 review 门控**
   - `review` 模式下只能考**已掌握**的目标；未掌握时返回
     `"{name!r} is not mastered yet; review re-tests proven objectives. Switch to study to teach it."`
   - 注意 `{name!r}` 是 Python repr（单引号），Java 需手写 `'name'`。

7. **`mastery_grade` 的 `ungraded` 是 `ok=false`**
   - 参考实现：`status == "ungraded"` → `ok=False` + `"Not graded: {reason}. Ask the learner to clarify."`
   - 其余状态（`graded` / `already_graded`）都是 `ok=true`。

8. **`mastery_repair_question` 的文案按 action 分支**
   - `void` → `"The question was voided; continue from mastery_status.next."`
   - `correct` → `"The answer key was corrected and the stored reply re-graded."`

9. **`mastery_profile` 的字段过滤在工具层**
   - 参考实现：只取 6 个白名单键且值非 `null`；全空时返回 `ok=false` + 字段清单。
   - 服务层 `recordLearnerProfile` 也会过滤，但工具层先过滤是为了给出**精确文案**。

10. **`mastery_assess` 的 review 门控文案与服务层不同**
    - 工具层：`"{name!r} is not mastered yet; review re-tests proven objectives."`（无后半句）
    - 服务层：`"mastery_assess is for concept/design objectives; ..."`
    - 两者是不同检查，不可合并。

11. **`mastery_propose_objective` 的名称长度在工具层校验**
    - `len(name) < 2` → `ok=false` + `"a candidate objective needs a name of 2+ characters"`。
    - 服务层不校验长度（参考实现也不校验）。

12. **`mastery_note_gap` 的 `kind` 在工具层校验**
    - 只接受 `goal_gap` / `source_omission`，否则 `ok=false`。

13. **`rag` 的三级校验**
    ```
    query 为空                    → "rag needs a query"
    无知识库                      → "No knowledge base is attached to this path."
    多个知识库但未指定 kb_id       → "rag needs kb_id when several knowledge bases are attached: ..."
    kb_id 不在已挂载列表          → "Knowledge base 'x' is not attached. Attached: ..."
    ```
    - 检索异常也转 `ok=false`（`"retrieval failed: ..."`），不杀死回合。

14. **`read_source` 的 20000 字符截断**
    - 参考实现：`text = str(text)[:20_000]`。
    - `metadata.char_count` 是**截断后**的长度。

15. **`mountedTools` 返回新 map，不改原表**
    - 参考实现：`out = dict(TOOLS if tools is None else tools)`。
    - 因此每次调用都得到独立副本，条件挂载不会污染全局注册表。

16. **导航 4 个工具的失败文案逐字复刻**
    - `path_id is required; call mastery_topics for the ids.`
    - `No mastery topic 'x' exists (or it has no map yet). Call mastery_topics for the topics you can send the learner to.`
    - `session_id is required; call mastery_sessions for this topic's conversations, or mastery_new_session to start one.`
    - `{topic} has no lesson matching 'x'.`
    - `{topic} has no conversation 'x'. Call mastery_sessions for its conversations.`
    - `This conversation is already the study session for that topic, so a card would only start a second one. Teach the lesson here instead.`

17. **`mastery_new_session` 的「同一课程」守卫**
    - `topic["path_id"] == ctx.path_id` → `ok=false`。
    - 这是**唯一**依赖 `ctx.pathId` 的导航校验。

18. **`ToolSchemas` 用静态 map，不反射**
    - 参考实现手写 `TOOL_PARAMETERS`，理由写在注释里：执行体用 `params.get(...)`，
      签名不可靠；显式 schema 可直接断言。
    - Java 同样手写，`ToolSchemas.of(name)` 返回对应 schema。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 门控用 `normalize` 而非 `enforced` | 未记录模式时误拒（`INV-09` 违反） | 用例断言「未记录模式放行全部工具」 |
| `mastery_status` 不重写 `status` | 模型对空路径说「恭喜学完」 | 用例断言空路径 `status=empty` + `message` |
| `mastery_quiz` 不重放而新建 | 单槽位被破坏（`INV-02`） | 用例断言第二次注册返回 `already_pending` |
| `ungraded` 返回 `ok=true` | 模型以为判分成功 | 用例断言 `ok=false` + `Not graded:` |
| `rag` 未指定 kb_id 时默认取第一个 | 多知识库时静默选错 | 用例断言多库未指定时 `ok=false` |
| `read_source` 不截断 | 20 万字符塞进上下文 | 用例断言 `char_count <= 20000` |
| `mountedTools` 直接改全局表 | 后续回合丢失工具 | 用例断言原表不变 |
| 导航文案用 Java 默认 `toString` | 与参考实现 `repr` 不一致 | 用例逐字断言文案 |

## Migration Plan

1. 服务层 4 个缺口（`setSessionMode` / `proposeObjective` / `noteGap` / `getOpenInteraction`）。
2. `ToolSchemas`（19 份 schema）。
3. 共享 5 个工具 + 大纲 2 个工具。
4. 学习 5 个工具 + 修复/画像/评估 3 个工具。
5. 捕获 2 个工具 + 来源 2 个工具 + 导航 4 个工具。
6. `MasteryTools` 注册表 + `mountedTools`。
7. 测试：`MasteryToolsTest`（门控 / 异常转换 / 条件挂载）+ `ToolSchemasTest` + 各工具专项。
8. `verify.ps1 -Scope unit` → 全量 → `pytest` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/capabilities/mastery/tools.py` 与
  `nav_tools.py` 为唯一依据。
- 参考实现测试空白（本 change 会补）：
  - `mastery_status` 的 `empty` 重写（`test_mastery_status.py` 已覆盖，Java 需对齐）；
  - `mastery_mode` 的 `mode_changed` 与持久化失败；
  - `mastery_quiz` 的单槽位重放与 review 门控；
  - `mastery_grade` 的 `ungraded` → `ok=false`；
  - `rag` / `read_source` 的全部拒绝分支；
  - `mountedTools` 不改原表。
