# Design: 模式门控

## Context

参考实现 `capabilities/mastery/mode.py` 是**无状态纯函数模块**。它的核心不是「查表」，
而是**两个语义不同的归一函数**：

| 函数 | 未知值 / 缺失 | 用途 |
| --- | --- | --- |
| `enforced_mode` | `None`（**不门控**） | 门控判定（`INV-09`） |
| `normalize_mode` | `study`（**默认模式**） | 展示、提示词框架、准入判定 |

这个区分是 `TC-D4` 的根因：`tool_is_allowed("mastery_build", "bogus")` 返回 `true`，
而 `admission_error("bogus", has_outline=false)` 返回拒绝文案（因为它按 `study` 判）。

`behavior.py` 是三个纯常量。

## Goals / Non-Goals

**Goals**

1. 逐函数复刻语义，**包括两个归一函数的差异**（这是本 change 最容易写错的地方）。
2. 错误文案逐字一致：包含目标模式名、当前模式名、`mastery_mode` 指令与破折号 `—`。
3. 分类表 fail-closed：未归类即拒绝，且启动期可断言。
4. 纯函数层：零 I/O、零 Spring、零网络。

**Non-Goals**

1. 不做工具装配（挂载期），门控只在调用期生效。
2. 不实现 `mastery_mode` 工具本体（切换动作 + 持久化）→ `0006`。
3. 不实现回合循环的预算消费（`turn_end` 事件）→ `0013`。
4. 不实现 `review` 模式下的 KP 级掌握校验 → `0008`。

## Decisions

1. **`SessionMode` 用枚举，不用 String**
   - 选择：`enum SessionMode { OUTLINE("outline"), STUDY("study"), REVIEW("review") }` + `value()`。
   - 理由：编译期约束；`MODES` 顺序（outline → study → review）在枚举声明顺序里天然保留，
     而 `owningModes` 的返回顺序依赖 `MODES` 顺序（`wrongModeMessage` 取 `targets[0]`）。
   - 放弃的方案：`String` 常量（无法在 `owningModes` 保证顺序，且易拼错）。

2. **两个归一函数分别建模为 `enforced()` 与 `normalize()`**
   - `static SessionMode enforced(Object raw)`：返回 `null` 表示不门控。
   - `static SessionMode normalize(Object raw)`：永不返回 `null`，未知值回退 `STUDY`。
   - 理由：合并成一个函数必然丢掉 `TC-D4` 或 `admission_error` 的语义。
   - 实现细节：输入按参考实现 `str(value or "").strip().lower()` 归一 —— `null`/空串 → 不门控；
     **大小写与前后空白**必须容忍（`" STUDY "` 视为 `study`）。

3. **分类表用 `Map<String, Set<SessionMode>>`，共享工具靠「不在表里」表达**
   - 参考实现 `TOOL_MODES` 不含 `SHARED_TOOLS`；`tool_is_allowed` 对 `get()==null` 直接返回 `true`。
   - 选择：照抄。**共享性 = 不在 `TOOL_MODES` 里**，另用 `SHARED_TOOLS` 集合供守卫使用。
   - 理由：若把共享工具也写进 `TOOL_MODES`（三模式全放行），`TC-D9` 的「两集合不相交」断言
     就会失去意义，且「未声明工具」与「显式共享工具」将无法区分。

4. **`assertClassificationComplete` 抛 `IllegalStateException`，不抛 `AssertionError`**
   - 参考实现抛 `AssertionError`（Python 的 `raise AssertionError`）。
   - 选择：Java 用 `IllegalStateException`。
   - 理由：Java 的 `assert` 默认关闭，`AssertionError` 语义上属于「断言失败」；这里的守卫是
     **运行期契约校验**（参考实现也是显式 `raise`，不是 `assert` 语句），`IllegalStateException` 更准确。
   - 影响：测试用 `assertThatThrownBy(...).isInstanceOf(IllegalStateException.class)`，
     并断言消息含 `fail-closed` / `both shared and mode-exclusive`。

5. **错误文案逐字复刻，含 Python `repr` 的单引号**
   - 参考实现用 `{target!r}` 生成 `'study'`（单引号）。Java 手写 `"'" + value + "'"`。
   - 理由：`tests/test_mode.py:69-72` 断言 `"'study'" in message`；`test_loop.py:61` 断言含 `mastery_mode`。
   - 文案（`mastery_quiz` + `outline`）：
     `mastery_quiz belongs to the 'study' or 'review' mode, and this conversation is in 'outline'. Call mastery_mode with mode='study' first — tell the learner what you are switching to and why.`

6. **`admissionError` 用 `normalize`，`toolIsAllowed` 用 `enforced`**
   - 这是唯一一处两个归一函数交叉使用的地方，必须显式留痕。
   - 后果：`admissionError(null, hasOutline=false)` **返回拒绝文案**（视为 study）；
     `toolIsAllowed("mastery_build", null)` **放行**。

7. **`BehaviorBudget` 用 `public static final int` 常量类**
   - 选择：`final class BehaviorBudget` + 私有构造 + 三个 `public static final int`。
   - 理由：与 `MasteryPolicy` 的常量风格一致；`0008`/`0013` 直接静态引用。

8. **`MODE_INSTRUCTIONS` 作为 `SessionMode` 的字段**
   - 选择：枚举构造参数携带 `instruction`，`instruction()` 返回。
   - 理由：避免一个与枚举平行的 `Map`，且编译期保证每个模式都有提示词。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 把 `enforced` 与 `normalize` 合并 | `TC-D4` 或 `TC-D5` 必有一个挂 | 两个函数分别有专项用例，且 `TC-D4` 同时断言 `null`/`""`/`"bogus"` 三种输入 |
| 共享工具写进 `TOOL_MODES` | `TC-D9` 不相交断言失去意义 | 守卫用 `SHARED_TOOLS ∩ TOOL_MODES.keySet()` 计算重叠，测试构造重叠场景 |
| 文案单引号写成双引号 | `test_wrong_mode_message_names_target` 等价用例失败 | 文案逐字比对，不只用 `contains` |
| `owningModes` 返回顺序不稳 | `mode='study'` 可能变成 `'review'` | 用 `SessionMode` 枚举声明顺序遍历，不用 `Set` 迭代 |
| 分类表与工具实际清单脱节 | 新增工具静默变成「共享」 | `assertClassificationComplete(Set<String>)` 接受外部清单；`0013` 装配时传入挂载集 |

## Migration Plan

1. `SessionMode`（无依赖）。
2. `BehaviorBudget`（无依赖）。
3. `ToolClassification`（依赖 `SessionMode`）。
4. `ModeGate`（依赖前三者）。
5. 测试：`SessionModeTest`、`ToolClassificationTest`、`ModeGateTest`、`BehaviorBudgetTest`。
6. `verify.ps1 -Scope unit` → 全量 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/capabilities/mastery/mode.py` 与 `behavior.py` 为唯一依据。
- 已知文档与代码差异（不改，只记录）：`docs/02-mode-tool-matrix.md:49-50` 把
  `mastery_topics` / `mastery_sessions` / `mastery_open_session` / `mastery_new_session`
  列为共享，但参考实现代码的 `SHARED_TOOLS` 不含这 4 个，`nav_tools.py` 也不参与启动断言。
  本 change 以**代码为准**（4 个导航工具留待 `0014` 落地时一并归类）。
