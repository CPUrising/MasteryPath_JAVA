# AI 协作留痕：0018-rest-api

## 1. prompt 范围

- 授权修改的文件：`src/main/java/com/masterypath/adapter/web/*`（新增 9 个类）、
  `src/main/java/com/masterypath/service/LearningService.java`（+4 方法）、
  `src/main/java/com/masterypath/domain/outline/OutlineBuilder.java`（`resetLearningState` 改 `public`）、
  `src/main/resources/application.properties`、`src/test/resources/application.properties`、
  `src/test/java/com/masterypath/adapter/web/*Test.java`（新增 5 个）、
  `scripts/verify.ps1`、`openspec/changes/0018-rest-api/**`、`docs/**`。
- 明确不做的事：WS 回合（`0019`）、知识库端点（`0020`）、设置落盘与提供方探测（`0021`）、
  `suggest-question` / `eval`（`0021` / `0022`）。
- 出处：用户指令原文「0018继续」「继续」；`docs/migration-plan.md` §5 第 18 行。

## 2. AI 产出

- 服务层缺口：`reset` / `setReviewSettings` / `resolveProposal` / `getSessionDetail`
  + 私有 `selectedSourceIds`
- 传输层（`adapter/web`）：
  - `CorsConfig`（字面量 `*`）、`ApiExceptionHandler`（兜底映射）、`ApiException`、
    `ApiSupport`（Python 宽松取值）、`WebConfig`（`@Bean` 装配）
  - `LearningController`（路径 / 地图 / 进度 / 事件 / 题库 / 捕获）
  - `QuestionBankController`（根路径 `/questions` 全局视图）
  - `SessionController`（会话 CRUD + 模式三级校验 + 偏好合并）
  - `StudyController`（`quiz` / `grade` / `assess` / `skip-question`，复用工具层）
  - `SettingsController`（分组读写 + 归一，内存实现）
- 新增测试：`LearningApiTest`（36）、`SessionApiTest`（14）、`StudyApiTest`（10）、
  `SettingsApiTest`（9）、`CorsApiTest`（1）

## 3. 人工修正点

- AI 自我修正 3 轮（均为测试基线写错，非实现缺陷）：
  1. **`containsExactly` 泛型推断失败**（`List<?>` 元素类型是 `capture#1`）
     → 改为显式 `List<String>` + `@SuppressWarnings("unchecked")`。
  2. **`GET /learning/paths/{id}/sessions` 返回对象而非数组**，测试却按数组解析
     → 改为先取 `sessions` 键再断言（参考实现返回 `{path_id, sessions}`）。
  3. **`quiz` 重放时 `question_id` 在 `question_card` 里而非顶层**
     → 断言改为读 `question_card.question_id`（与参考实现 `metadata` 结构一致）。
  4. **`deleteSessionDropsOwnedEmptyScratchPath` 未先建会话行**
     → `deleteSession` 对未知会话返回 `false`（参考实现同样如此），补 `setSessionMode` 建档。

## 4. 回归结果

- 用例数 / 失败数：本 change 新增 70 / 0；全量 671 / 0（601 → 671）。
- 证据链接：[`docs/verify/0018-rest-api.md`](../verify/0018-rest-api.md)
- 日志：`artifacts/verify-0018-rest-api-20261104-214554.log`
- 参考实现对照：`pytest tests/test_api.py tests/test_f0_api.py -q` → **18 passed**
  （其中 2 个 WS 用例属 `0019`，不计入本 change 的对照范围）。

## 5. 高风险判断留痕

| 判断 | 结论 | 依据 |
| --- | --- | --- |
| `GET /learning/paths` 用 `listPathOverviews` 还是独立实现 | **独立实现** | 跳过条件不同：REST 只跳过「有 owner 且无知识点」，`listPathOverviews` 跳过所有空 map |
| `session_id` 缺失时是否退化为「未记录模式」 | **不退，422** | 参考实现 `Query(...)` 必填；退化会让漏传参数静默绕过门控 |
| 模式三级校验顺序 | **模式值 → 路径 → 准入** | 参考实现 `app.py:488-500`；顺序反了会把 422 变成 404 |
| `rebuild` 的 `confirm` 判定位置 | **调服务之前** | 否则服务已写库才拒绝 |
| override 用 404 还是 412 | **412** | 参考实现 `app.py:267`；全项目唯一 |
| 异常映射是否全局一刀切 | **不，逐端点 `try/catch`** | 否则 override 的 412 会变成 409 |
| REST 学习端点直调服务还是复用工具层 | **复用工具层** | 保证 REST 与 WS 行为一致（同样的门控与 metadata） |
| CORS 用 `allowedOrigins("*")` 还是 `allowedOriginPatterns("*")` | **`allowedOrigins("*")`** | 后者回显来源，前端契约要求字面量 `*` |
| `GET /learning/sessions/{id}` 返回扁平行还是 `{session, messages}` | **`{session, messages}`** | 服务层 `service.get_session` 就是这个结构；`app.py:479` 的扁平返回是端点层漏包一层（见 verify §4 D1） |
| 设置分组是否落盘 | **不落盘（内存）** | 落盘与探测属 `0021`；本 change 只冻结分组语义与归一规则 |
| `LearningStore` 如何注入 | **`@Bean`** | 参考实现把 store 作为 `create_app` 参数；Spring 侧用 bean 表达同一件事 |
