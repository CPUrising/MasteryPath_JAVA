# Proposal: REST API

## Why

`0001`–`0017` 把引擎、存储、服务、LLM、回合循环、工具层全部搬完了，但**没有任何 HTTP 入口**——
Next.js 前端至今无法连上 Java 后端。

参考实现 `api/app.py`（559 行）暴露了 40+ 个端点，并有三条硬性约束：

1. **路径与字段名不变**（`AGENTS.md` §4.5）：`/learning/paths/{id}/map`、`snake_case` 字段。
2. **错误码语义固定**：`404` 未知资源、`409` 模式冲突 / 需确认、`412` 前置条件失败、
   `422` 参数非法。
3. **REST 学习端点复用工具层**（`_run_tool`）：`quiz` / `grade` / `assess` / `skip-question`
   直接调 `MasteryTools`，因此 REST 与 WS 的行为天然一致。

没有这一层，`0019` 的 WS 回合也无从挂载（它复用同一套 `TurnRunner` 与 CORS 配置）。

## What Changes

- 新增 `adapter/web` 包：
  - `LearningController`：路径 CRUD、map、rebuild、override、gap、mastery、sources、
    review-queue、events、questions、proposals、coverage
  - `SessionController`：会话列表 / 详情 / 删除 / 模式 / 偏好
  - `StudyController`：`quiz` / `grade` / `assess` / `skip-question`（复用工具层）
  - `SettingsController`：`/settings` 与 `/settings/{section}`（分组读写）
  - `ApiExceptionHandler`：把领域异常映射成固定状态码
  - `CorsConfig`：`access-control-allow-origin: *`
- 补齐服务层 3 个缺口：`reset` / `setReviewSettings` / `resolveProposal`

## Capabilities

### New Capabilities

无新增能力；实现 `specs/agent-turn` 的 REST 契约（行为以参考实现 `api/app.py` 为唯一依据）。

### Modified Capabilities

无。`skip_specs: true`：本 change 补齐参考实现已有、而 `0001`–`0017` 尚未实现的能力。

## Impact

- 影响文件：
  - `src/main/java/com/masterypath/adapter/web/*`（新增约 7 个类）
  - `src/main/java/com/masterypath/service/LearningService.java`（+3 方法）
  - `src/test/java/com/masterypath/adapter/web/*Test.java`（新增）
  - `scripts/verify.ps1`（`-Scope api` 补 `*ApiTest`）
- 影响测试：既有 601 个用例**不修改**。
- 明确不做（Out of scope）：
  - WS 回合 `/ws` → 属 `0019`。
  - 知识库端点（`/knowledge/*`）→ 属 `0020`。
  - 设置探测（`/settings/providers/probe` 等）→ 属 `0021`。
  - `suggest-question` / `eval` → 属 `0021` / `0022`。
  - `test_api.py` 的 `test_ws_turn_builds_outline`、`test_f0_api.py` 的
    `test_ws_turn_persists_session` → 属 `0019`。
