# Design: REST API

## Context

参考实现 `api/app.py`（559 行）分七块：

| 块 | 行号 | 内容 |
| --- | --- | --- |
| 路径 | 133–226 | 列表 / 创建 / 详情 / 删除 / 改名 / redo / review-settings |
| 地图 | 228–275 | `PUT map`（build 或 revise）、`POST rebuild`、override、gap resolve |
| 进度 | 278–283 | `GET mastery`（返回 `state["map"]`） |
| 来源 | 286–331 | 附加文件夹、列出、设置知识库 |
| 事件与题库 | 333–358 | `review-queue`、`events`、`questions`、`tags` |
| 捕获与覆盖 | 361–387 | `proposals`、`resolve`、`coverage`、`eval` |
| 会话与学习 | 470–552 | 会话 CRUD、模式、偏好、`quiz`/`grade`/`assess`/`skip-question` |

Java 侧 `pom.xml` 已有 `spring-boot-starter-web`；`0017` 已提供 23 个工具。
本 change 只写控制器与异常映射。

## Goals / Non-Goals

**Goals**

1. 路径与字段名逐字一致（`snake_case`，`/learning/paths/{id}/map`）。
2. 错误码语义固定：`404` / `409` / `412` / `422`。
3. REST 学习端点复用工具层（`_run_tool` 等价），保证与 WS 行为一致。
4. CORS 允许任意来源（`access-control-allow-origin: *`）。

**Non-Goals**

1. 不做 WS（`0019`）。
2. 不做知识库端点（`0020`）。
3. 不做设置探测（`0021`）。
4. 不做 `suggest-question` / `eval`（`0021` / `0022`）。

## Decisions

1. **`GET /learning/paths` 跳过「有 owner 但无知识点」的路径**
   - 参考实现：`if progress.owner_session_id and not any(module.knowledge_points ...): continue`。
   - 与 `0013` 的 `listPathOverviews`（跳过空 map）**不同**：这里只跳过**有 owner** 的，
     普通空路径仍会列出。两者不可互换。

2. **`POST /learning/paths` 返回 201 + 完整 state**
   - 参考实现：`status_code=201`，返回 `service.get_state(path_id)`。
   - `path_id` 为空 → `422 "path_id required"`；已存在 → `409`（`PreconditionError`）。

3. **`PUT /learning/paths/{id}/map` 的模式门控**
   - `_require_outline(session_id)`：**未记录模式不门控**（`INV-09`），
     已记录且非 `outline` → `409 "mode_conflict: outline mode required"`。
   - 请求体含 `ops` → 走 `revise`，返回 `{status, applied}`；
     否则走 `build`，返回 `{status, version}`。
   - `refused_rebuild` → `409 "rebuild_requires_confirm"`。
   - `OutlineError` → `422`。

4. **`POST /learning/paths/{id}/rebuild` 必须显式 `confirm`**
   - 无 `confirm` → `409 "rebuild_requires_confirm"`（在调用服务之前）。
   - 返回 `{status, rebuilt}`。

5. **`POST .../objectives/{kp_id}/override` 用 412**
   - 参考实现：`PreconditionError` → `412`（不是 404，也不是 422）。
   - 这是唯一用 412 的端点。

6. **`POST .../gaps/{gap_id}/resolve` 用 422**
   - `OutlineError` 与 `PreconditionError` 都映射成 `422`。
   - 返回 `{gap_id, resolution}`。

7. **`GET .../mastery` 返回 `state["map"]` 而非整个 state**
   - 参考实现：`return state["map"]`。
   - 路径不存在 → `404 "unknown path"`。

8. **`PUT .../sessions/{sid}/mode` 的三级校验**
   ```
   模式不在 MODES        → 422 "mode must be one of: outline, study, review"
   路径不存在            → 404 "unknown path"
   准入失败（无大纲切 study）→ 409（admission_error 文案）
   ```
   - 顺序不可调换：先校验模式值，再查路径，最后判准入。
   - 返回 `{session_id, mode, actor}`，`actor` 默认 `"learner"`。

9. **`PATCH .../sessions/{sid}` 的偏好合并**
   - 请求体有 `preferences` 字典 → 用它；否则从 `llm_selection` / `knowledge_bases`
     两个键组装。
   - 会话不存在时**先建档**（`upsert_session`），再写偏好。
   - 写偏好返回 `null` → `404 "unknown session"`。

10. **REST 学习端点复用工具层**
    - `_run_tool(name, pathId, payload)`：调 `MasteryTools.all().get(name).execute(...)`，
      `ok=false` → `422`（`detail` 是工具文案），`ok=true` → `{ok: true, content, ...metadata}`。
    - 因此 `quiz` 的返回含 `status` / `question_id` / `question_card`，
      `grade` 的返回含 `status` / `result` / `gate_cleared`。

11. **`GET /learning/sessions/{sid}` 返回 `{session, messages}`**
    - 参考实现：`return service.get_session(session_id)`，而 `service.get_session`
      返回 `{"session": session, "messages": [...]}`（`service.py:1008-1012`）。
    - `test_f0_api.py#test_sessions_and_mode` 断言 `detail["session"]["session_id"]`，
      与代码一致，无偏差。

12. **`DELETE /learning/sessions/{sid}` 返回 204，不存在返回 404**
    - 参考实现：`if not service.delete_session(session_id): raise 404`。

13. **`GET /learning/paths/{id}/events?since=N` 直接转发存储层**
    - 参考实现：`return store.events_since(path_id, since)`。
    - 不校验路径存在性（未知路径返回空列表）。

14. **CORS 用 `*`**
    - 参考实现测试断言 `access-control-allow-origin == "*"`。
    - Spring 用 `CorsConfiguration` + `allowedOriginPatterns("*")`。

15. **异常映射集中在一处**
    - `PreconditionFailedException` → 默认 `409`，但 override 端点显式 `412`。
    - `QuizSpec.QuizException` / `OutlineBuilder` 的 `IllegalArgumentException` → `422`。
    - 因此不用全局 `@ExceptionHandler` 一刀切，而是在各端点显式 `try/catch`，
      与参考实现的逐端点 `HTTPException` 一致。

16. **`reset` 与 `setReviewSettings` 补在服务层**
    - `reset`：调 `OutlineBuilder.resetLearningState` + 发 `path.reset`。
    - `setReviewSettings`：`clamp(0.7, 0.99)` + 回写全部 `repetitionStates` + 发 `review.settings`。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| `GET /learning/paths` 用 `listPathOverviews` | 普通空路径被隐藏（与参考实现不一致） | 用例断言「无 owner 的空路径仍列出」 |
| `map` 端点未做模式门控 | study 下也能改地图（`TC-I1` 失败） | 用例断言 `409` |
| `rebuild` 先调服务再判 `confirm` | 服务已写库才拒绝 | 用例断言拒绝时地图不变 |
| override 用 404 而非 412 | 前端错误处理分支错 | 用例断言 `412` |
| 学习端点直接调服务而非工具 | REST 与 WS 行为分叉 | 用例断言 `quiz` 返回含 `question_card` |
| 全局异常处理器一刀切 | override 的 412 变成 409 | 逐端点 `try/catch` |
| CORS 只允许 `localhost:3000` | 其他来源被拒 | 用例断言 `*` |

## Migration Plan

1. 服务层 3 个缺口（`reset` / `setReviewSettings` / `resolveProposal`）。
2. `ApiExceptionHandler` + `CorsConfig`。
3. `LearningController`（路径 + 地图 + 进度 + 来源 + 事件 + 题库 + 捕获）。
4. `SessionController` + `StudyController`。
5. `SettingsController`（分组读写，内存实现；持久化属 `0021`）。
6. 测试：`LearningApiTest` + `SessionApiTest` + `StudyApiTest` + `SettingsApiTest`。
7. `verify.ps1 -Scope api` → 全量 → `pytest test_api.py test_f0_api.py` 对照 → 回填报告。

## Open Questions

- 无。语义以 `../MasteryPath/masterypath/api/app.py` 为唯一依据。
- 参考实现测试与代码一致，无需要登记的差异。
