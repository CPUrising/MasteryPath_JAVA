# AI 协作留痕：0013-path-binding-scratch

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/service/LearningService.java`（+6 公开方法 +2 私有辅助）、
  `src/test/java/com/masterypath/service/PathBindingTest.java`（新增）、
  `scripts/verify.ps1`（`-Scope unit` 补 `*PathBindingTest`）、`openspec/changes/0013-path-binding-scratch/**`、`docs/**`。
- 明确不做的事：工具层 `MasteryPathsTool` / `MasterySwitchTool` / `MasteryLeaveTool`（`0015`）、
  只读导航与 hand-off（`0014`）、REST 端点（`0016`）、WS 回合内切换（`0017`）。
- 出处：用户指令原文「好，提交后继续实现计划」；`docs/migration-plan.md` §5 第 13 行（拆分后）。

## 2. AI 产出

- 修改文件：`LearningService` —— `listPathOverviews` / `ensureScratchPath` /
  `bindSessionPath` / `deleteSession` / `listSessions` / `getSession`
  + 私有 `hasKnowledgePoints` / `stripUnderscores`
- 新增测试：`PathBindingTest`（22）
- 关键决策：
  - **scratch 靠「空 map」隐藏，不靠标志位**：`mapSummary` 返回空 map 即跳过。
    后果是「建过大纲又被清空的普通路径」也会隐藏——参考实现原样，照抄。
  - **`strip("_")` 必须手写**：Java 的 `String.strip()` 只去空白，`"___"` 不会被清空，
    会得到 `scratch-___` 而非 `scratch-default`。
  - **认领分支不发事件**：路径已存在，`path.created` 已发过；再发会让事件流出现重复创建。
  - **`deleteSession` 先删 session 再判断是否删 path**：因此「无其他会话」条件在删除后
    自然成立。顺序是参考实现原样。
  - **`deleteSession` 返回 `true` 即使没删路径**：返回值语义是「会话删除成功」。
  - **`bindSessionPath` 空 session 返回 `null` 不写库**：避免孤儿会话记录。

## 3. 人工修正点

- 无人工修正。本 change 一次通过（unit 391 / 全量 478，0 失败）。
- 主动澄清一处编号问题：参考实现只在 `tests/test_path_binding.py` 首行声明 `TC-S*` 家族，
  **未细分 `S1`–`S4`**。`design.md` / `tasks.md` / `verify` 报告里的 `TC-S1`–`TC-S4`
  是 Java 侧分组标签，已在 verify 报告 §2 显式注明，避免被误读为参考实现的正式编号。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 22 / 0；全量 478 / 0（456 → 478）。
- 证据链接：[`docs/verify/0013-path-binding-scratch.md`](../verify/0013-path-binding-scratch.md)
- 日志：`artifacts/verify-0013-path-binding-scratch-20261104-193129.log`
- 参考实现对照：在 `../MasteryPath` 执行 `pytest tests/test_path_binding.py -q` → **5 passed**。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| scratch 如何隐藏 | **空 map 跳过**，不加 `is_scratch` 标志 | 参考实现 `if not summary: continue` |
| `strip("_")` 用 Java 原生 `strip()` | **不行，手写** | `strip()` 只去空白；`"!!!"` 会得到 `scratch-___` |
| 认领 owner 是否发事件 | **不发** | 参考实现认领分支只 `mutate`，无 `emit` |
| `deleteSession` 的删除顺序 | **先删 session，再判 path** | 参考实现原样；「无其他会话」条件因此自然成立 |
| `deleteSession` 返回值含义 | **会话删除成功**，与 path 是否删除无关 | 参考实现 `return True` 在 path 判断之前 |
| 概览排序键 | **`path_id` 升序** | 参考实现 `sort(key=lambda item: item["path_id"])` |
| 概览 `name` 空值处理 | **回退 `path_id`** | 参考实现 `progress.name or progress.path_id` |
| 工具层三个 `Tool` 类是否本 change 实现 | **不实现** | 属 `0017`；服务层用例已覆盖同样的语义 |
| 「切换到不存在的路径」的拒绝在哪层 | **工具层** | 服务层 `setSessionPath` 对未知 session 返回 `null`，不做路径存在性校验 |
