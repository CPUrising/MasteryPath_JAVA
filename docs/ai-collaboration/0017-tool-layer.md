# AI 协作留痕：0017-tool-layer

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/adapter/tool/*`（新增 10 个类）、
  `src/main/java/com/masterypath/service/LearningService.java`（+3 方法）、
  `src/main/java/com/masterypath/storage/LearningStore.java`（+1 方法）、
  `src/main/java/com/masterypath/storage/Transaction.java`（`ACTIVE_STATUSES` 改包级可见）、
  `src/main/java/com/masterypath/domain/mode/ToolClassification.java`（+4 个共享工具）、
  `src/test/java/com/masterypath/adapter/tool/*Test.java`（新增 4 个）、
  `scripts/verify.ps1`、`openspec/changes/0017-tool-layer/**`、`docs/**`。
- 明确不做的事：`TurnRunner` / WS（`0019`）、知识库服务本体（`0020`）、REST（`0018`）。
- 出处：用户指令原文「提交后，继续0017」；`docs/migration-plan.md` §5 第 17 行（拆分后）。

## 2. AI 产出

- 服务层缺口：`LearningStore.getOpenInteraction(pathId)`、
  `LearningService.setSessionMode` / `proposeObjective` / `noteGap`
- 工具层（`adapter/tool`）：
  - `ToolSchemas`（19 份 schema）、`ToolSupport`（门控 / `repr` / 真值）、
    `StatePayload`（导师视角快照）
  - `SharedTools`（5）、`OutlineTools`（2）、`StudyTools`（5）、
    `RepairTools`（5）、`SourceTools`（2）、`NavTools`（4）
  - `MasteryTools`（注册表 + `mountedTools`）
- 新增测试：`ToolSchemasTest`（5）、`MasteryToolsTest`（15）、
  `SourceToolsTest`（10）、`StatusAndQuizToolsTest`（17）

## 3. 人工修正点

- AI 自我修正 3 轮：
  1. **`LearningService` 缺 `KnowledgeType` import** → 编译失败，补 import。
  2. **`ToolClassification.SHARED_TOOLS` 缺 4 个导航工具** → `MasteryTools` 静态初始化时
     `assertClassificationComplete` 抛 `IllegalStateException`，全部 15 个用例报
     `NoClassDefFoundError`。参考实现 `mode.py:44` 的注释写着
     「add the navigation/profile tools when they land」——导航工具本意就是共享。
  3. **测试泛型推断失败**（`List<?>` / `Map<?, ?>` 传给 `containsExactly` / `containsOnlyKeys`）
     → 改为显式 `List<String>` / `Map<String, Object>` + `@SuppressWarnings("unchecked")`。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 47 / 0；全量 601 / 0（554 → 601）。
- 证据链接：[`docs/verify/0017-tool-layer.md`](../verify/0017-tool-layer.md)
- 日志：`artifacts/verify-0017-tool-layer-20261104-204508.log`
- 参考实现对照：6 个测试文件共 **37 passed**
  （`test_mastery_status` / `test_source_tools` / `test_loop` / `test_llm_client` /
  `test_repair_profile` / `test_mastery_nav`）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| 导航工具是否共享 | **是** | 参考实现 `mode.py:44` 注释「add the navigation/profile tools when they land」 |
| 门控用 `enforced` 还是 `normalize` | **`enforced`** | 未记录模式 → 不门控（`INV-09` / `TC-D4`） |
| `mastery_status` 是否重写 `status` | **重写为 `empty`** | 参考实现 `_state_payload`；否则模型对空路径说「恭喜学完」 |
| `mastery_quiz` 已有待答题时新建还是重放 | **重放** | `INV-02` 单槽位 |
| `ungraded` 是 `ok=true` 还是 `false` | **`false`** | 参考实现 `status == "ungraded"` → `ok=False` |
| `mastery_assess` 的 review 文案是否与服务层合并 | **不合并** | 两者是不同检查，文案也不同 |
| `rag` 多库未指定 `kb_id` 是否默认取第一个 | **不默认，拒绝** | 参考实现 `if len(ctx.kb_ids) != 1: return failure` |
| `read_source` 是否截断 | **截断 20000** | 参考实现 `[:20_000]` |
| `mountedTools` 是否改全局表 | **不改，返回副本** | 参考实现 `out = dict(TOOLS)` |
| 知识库服务如何调用 | **反射（鸭子类型）** | 服务本体属 `0020`；避免提前引入依赖 |
| `mastery_build` 的 `gaps` 是否全字段 | **只 4 个键** | 工具只消费 `id`/`kind`/`ref`/`reason`；已登记 D6 |
