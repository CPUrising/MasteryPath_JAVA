# Tasks

## 1. 服务层缺口

- [x] 1.1 `LearningService.reset(pathId)`：清学习态保留地图 + 发 `path.reset`
- [x] 1.2 `LearningService.setReviewSettings(pathId, retention)`：`clamp(0.7, 0.99)` + 回写全部 `repetitionStates` + 发 `review.settings`
- [x] 1.3 `LearningService.resolveProposal(pathId, proposalId, action)`：accept / reject + 重算覆盖 + 发事件

## 2. 基础设施

- [x] 2.1 `CorsConfig`：`allowedOrigins("*")` + 全部方法与头
- [x] 2.2 `ApiExceptionHandler`：`PreconditionFailedException` → 409、`QuizException` / `IllegalArgumentException` → 422

## 3. 路径与地图

- [x] 3.1 `LearningController`：`GET/POST /learning/paths`、`GET/DELETE/PATCH /learning/paths/{id}`
- [x] 3.2 `POST /redo`、`PUT /review-settings`、`PUT /map`（build/revise + 模式门控）、`POST /rebuild`
- [x] 3.3 `POST /objectives/{kp}/override`（412）、`POST /gaps/{gap}/resolve`（422）、`GET /mastery`
- [x] 3.4 `GET /review-queue`、`GET /events`、`GET /questions`、`POST /questions/{id}/tags`、`GET /proposals`、`POST /proposals/{id}/resolve`、`GET /coverage`

## 4. 会话与学习

- [x] 4.1 `SessionController`：`GET /paths/{id}/sessions`、`GET/DELETE /learning/sessions/{id}`、`PUT .../mode`、`PATCH .../sessions/{id}`
- [x] 4.2 `StudyController`：`POST /quiz`、`/grade`、`/assess`、`/skip-question`（复用工具层）
- [x] 4.3 `SettingsController`：`GET /settings`、`GET/PUT /settings/{section}`

## 5. 测试与验证

- [x] 5.1 `LearningApiTest`：路径 CRUD、map 门控、rebuild 确认、override 412、events、mastery
- [x] 5.2 `SessionApiTest`：会话列表/详情/删除、模式三级校验、偏好合并
- [x] 5.3 `StudyApiTest`：quiz → grade 闭环、assess、skip-question
- [x] 5.4 `SettingsApiTest`：分组读写、未知分组 404
- [x] 5.5 `.\scripts\verify.ps1 -Scope api -Change 0018-rest-api`
- [x] 5.6 全量 `.\scripts\verify.ps1 -Change 0018-rest-api`
- [x] 5.7 与参考实现对照（`pytest tests/test_api.py tests/test_f0_api.py -q`，WS 用例除外）
- [x] 5.8 回填 `docs/verify/0018-rest-api.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0018-rest-api.md`、`README.md`
