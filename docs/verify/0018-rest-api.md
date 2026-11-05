# 验证报告：0018-rest-api

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0018-rest-api` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 21:45:54（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0018-rest-api` |
| 日志 | `artifacts/verify-0018-rest-api-20261104-214554.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 路径与地图（TC-I1 / TC-I2）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 非 outline 模式写地图 → 409 且地图不变 | `LearningApiTest#mapWriteRequiresOutlineMode` | PASS | `TC-I1` |
| 未记录模式不门控（`INV-09`） | `#mapWriteWithoutRecordedModeIsAllowed` | PASS | 参考实现 `_require_outline` 同语义 |
| outline 模式可写地图 | `#mapWriteInOutlineModeBuilds` | PASS | 对应 `test_outline_mode_allows_map_write` |
| 带 `ops` 走 revise | `#mapWriteWithOpsRevises` | PASS | 返回 `{status, applied}` |
| 非法 payload → 422 | `#mapWriteWithInvalidPayloadIs422` | PASS | `OutlineError` → 422 |
| 缺 `session_id` → 422 | `#mapWriteWithoutSessionIdIs422` | PASS | 参考实现 `Query(...)` 必填 |
| 重建未带 `confirm` → 409 | `#rebuildRequiresConfirm` | PASS | `TC-I2`，对应 `test_tc_i2_rebuild_requires_confirm` |
| 重建带 `confirm` 替换地图 | `#rebuildWithConfirmReplacesMap` | PASS | 返回 `{status, rebuilt}` |
| override 未知目标 → **412** | `#overrideUnknownObjectiveIs412` | PASS | 全项目唯一 412 |
| override 已知目标成功 | `#overrideKnownObjectiveSucceeds` | PASS | `TC-I7` 的 REST 侧 |
| 缺口未知 → 422 | `#resolveUnknownGapIs422` | PASS | `OutlineError` 与 `PreconditionError` 都映射 422 |
| 缺口处理返回 resolution | `#resolveGapReturnsResolution` | PASS | |

### 2.2 路径生命周期

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 创建返回 201 + 完整 state | `#createPathReturns201AndState` | PASS | 对应 `test_create_and_read_path` |
| 缺 `path_id` → 422 | `#createPathWithoutIdIs422` | PASS | |
| 重复创建 → 409 | `#createDuplicatePathIs409` | PASS | |
| 未知路径 → 404 | `#getUnknownPathIs404` | PASS | |
| 删除 → 204 | `#deletePathReturns204` | PASS | |
| 改名 | `#renamePath` | PASS | 对应 `test_rename_redo_delete` |
| 无 `name` 键退化为读状态 | `#patchWithoutNameReturnsState` | PASS | |
| redo 清学习态保留地图 | `#redoClearsLearningStateButKeepsMap` | PASS | 对应 `test_rename_redo_delete` |
| redo 未知路径 → 404 | `#redoUnknownPathIs404` | PASS | |
| 复习保留率限幅 | `#reviewSettingsClampsToRange` | PASS | 对应 `test_review_settings_update` |
| 列表跳过有 owner 的空 scratch、保留普通空路径 | `#listPathsSkipsOwnedEmptyScratchButKeepsPlainEmptyPath` | PASS | 与 `listPathOverviews` 刻意不同 |

### 2.3 进度 / 事件 / 题库 / 捕获

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| `mastery` 只返回 map | `#masteryReturnsMapOnly` | PASS | 参考实现 `return state["map"]` |
| `mastery` 未知路径 → 404 | `#masteryUnknownPathIs404` | PASS | |
| `events?since=N` 过滤 | `#eventsSinceFiltersByRevision` | PASS | `TC-I4`，对应 `test_tc_i4_events_since` |
| 未知路径事件为空列表 | `#eventsForUnknownPathIsEmptyList` | PASS | 不校验存在性 |
| 失败写入不产事件 | `#failedMapWriteEmitsNoEvent` | PASS | `TC-I5` / `INV-51` |
| 复习队列 | `#reviewQueueReturnsDueReviews` | PASS | |
| 标签整体替换 + 去重 | `#tagQuestionReplacesTags` | PASS | |
| 未知题库条目 → 404 | `#tagUnknownEntryIs404` | PASS | |
| 全局题库聚合 + `path_name` | `#globalQuestionsAggregatesAcrossPaths` | PASS | |
| 候选目标列表 + 接受 | `#proposalsListAndResolve` | PASS | |
| 候选目标非法动作 → 422 | `#resolveProposalWithUnknownActionIs422` | PASS | |
| 覆盖报告 404 / 正常 | `#coverageUnknownPathIs404`、`#coverageReturnsReport` | PASS | |

### 2.4 会话与模式

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 写模式建档并出现在列表 | `SessionApiTest#setModeCreatesSessionAndListsIt` | PASS | 对应 `test_sessions_and_mode` |
| 非法模式值 → 422 | `#setModeRejectsUnknownValueWith422` | PASS | 对应 `test_mode_rejects_unknown_value` |
| 未知路径 → 404 | `#setModeRejectsUnknownPathWith404` | PASS | 对应 `test_mode_rejects_unknown_path` |
| 无大纲切 study → 409 | `#setModeRejectsStudyWithoutOutlineWith409` | PASS | `TC-D5` |
| **模式值先于路径校验** | `#modeValueIsCheckedBeforePathExistence` | PASS | 三级顺序不可调换 |
| 模式值归一（大小写/空白） | `#modeIsNormalisedToLowerCase` | PASS | |
| `actor` 缺省 `learner` | `#actorDefaultsToLearner` | PASS | |
| 会话详情为 `{session, messages}` | `#getSessionReturnsNestedSessionAndMessages` | PASS | 与 `service.get_session` 一致 |
| 未知会话 → 404 | `#getUnknownSessionIs404` | PASS | |
| 删除会话 204 → 再删 404 | `#deleteSessionReturns204Then404` | PASS | |
| 删除会话连带清理空 scratch | `#deleteSessionDropsOwnedEmptyScratchPath` | PASS | 三重条件 |
| 偏好字典写入 | `#patchSessionWritesPreferencesDict` | PASS | |
| 偏好从两个已知键组装 | `#patchSessionAssemblesPreferencesFromKnownKeys` | PASS | |
| 会话不存在先建档 | `#patchSessionCreatesMissingSessionFirst` | PASS | |

### 2.5 REST 学习端点（复用工具层）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| quiz → grade 闭环 | `StudyApiTest#quizThenGradeClosesTheLoop` | PASS | 对应 `test_rest_quiz_and_grade` |
| quiz 返回含 `question_card` | 同上 | PASS | 证明复用工具层而非直调服务 |
| 缺 `expected_answer` → 422 | `#quizWithoutExpectedAnswerIs422` | PASS | |
| 未知目标 → 422 | `#quizOnUnknownObjectiveIs422` | PASS | |
| 单槽位重放（`INV-02`） | `#quizReplaysOpenQuestionInsteadOfCreatingNewOne` | PASS | 返回 `already_pending` |
| 未知题号判分 → 422 | `#gradeUnknownQuestionIs422` | PASS | |
| assess 定性目标 | `#assessConceptObjective` | PASS | 对应 `test_rest_assess_and_skip` |
| assess 定量目标 → 422 | `#assessMemoryObjectiveIs422` | PASS | `INV-05` |
| skip-question | `#skipQuestionAbandonsIt` | PASS | 对应 `test_rest_assess_and_skip` |
| outline 模式下 quiz 被拒 | `#quizIsRefusedInOutlineMode` | PASS | `TC-D1` |
| 未记录模式放行 | `#quizIsAllowedWhenModeIsUnrecorded` | PASS | `INV-09` |

### 2.6 设置与 CORS

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 默认值与参考实现一致 | `SettingsApiTest#defaultsMatchReference` | PASS | |
| 提供方往返 | `#providersRoundtrip` | PASS | 对应 `test_settings_roundtrip` |
| 未知分组 → 404 | `#unknownSectionIs404` | PASS | 对应 `test_settings_roundtrip` |
| 模型分组往返 | `#modelSectionRoundtrip` | PASS | 对应 `test_model_section_list_roundtrip` |
| 旧式单条折叠成列表 | `#legacySingleModelFoldsIntoList` | PASS | 同上 |
| 未知协议回退 `openai` | `#unknownProtocolFallsBackToOpenai` | PASS | |
| 非字典 `detection` 丢弃 / 字典保留 | `#nonDictDetectionIsDropped`、`#dictDetectionIsPreserved` | PASS | |
| `general` 分组往返 | `#generalSectionRoundtrip` | PASS | |
| CORS 返回字面量 `*` | `CorsApiTest#allowsAnyOriginWithLiteralStar` | PASS | 对应 `test_cors_allows_frontend_origin` |

本 change 新增用例：**70 个**（`LearningApiTest` 36 + `SessionApiTest` 14 +
`StudyApiTest` 10 + `SettingsApiTest` 9 + `CorsApiTest` 1），全部 PASS。
另在 `0017` 的 `MasteryToolsTest` 补测 4 个捕获工具用例（`propose_objective` / `note_gap`），
关闭该处验证债。
全量 **675 个，0 失败**（601 → 675）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| REST 专项 | `verify.ps1 -Scope api -Change 0018-rest-api` | Tests run: 70, Failures: 0 |
| 全量 | `verify.ps1 -Change 0018-rest-api` | BUILD SUCCESS，Tests run: 675, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（0017 的 601 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_api.py tests/test_f0_api.py -q` | **18 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_tc_i1_map_write_requires_outline_mode` | `LearningApiTest#mapWriteRequiresOutlineMode` | 同断言（409） |
| `test_mode_rejects_unknown_value` | `SessionApiTest#setModeRejectsUnknownValueWith422` | 同断言（422） |
| `test_mode_rejects_unknown_path` | `#setModeRejectsUnknownPathWith404` | 同断言（404） |
| `test_tc_i2_rebuild_requires_confirm` | `LearningApiTest#rebuildRequiresConfirm` | 同断言（409） |
| `test_create_and_read_path` | `#createPathReturns201AndState` + `#getPath` | 同断言 |
| `test_outline_mode_allows_map_write` | `#mapWriteInOutlineModeBuilds` | 同断言（`status=built`） |
| `test_tc_i4_events_since` | `#eventsSinceFiltersByRevision` | 同断言（含 `since=999` 为空） |
| `test_settings_roundtrip` | `SettingsApiTest#providersRoundtrip` + `#unknownSectionIs404` | 同断言 |
| `test_model_section_list_roundtrip` | `#modelSectionRoundtrip` + `#legacySingleModelFoldsIntoList` | 同断言 |
| `test_dashboard_fields` | `LearningApiTest#getPath`（state 19 键） | 服务层已由 `PathStateTest` 覆盖 |
| `test_review_settings_update` | `#reviewSettingsClampsToRange` | 同断言 |
| `test_rename_redo_delete` | `#renamePath` + `#redoClearsLearningStateButKeepsMap` + `#deletePathReturns204` | 同断言 |
| `test_rest_quiz_and_grade` | `StudyApiTest#quizThenGradeClosesTheLoop` | 同断言 |
| `test_rest_assess_and_skip` | `#assessConceptObjective` + `#skipQuestionAbandonsIt` | 同断言 |
| `test_sessions_and_mode` | `SessionApiTest#setModeCreatesSessionAndListsIt` + `#getSessionReturnsNestedSessionAndMessages` | 同断言（`detail["session"]["session_id"]`） |
| `test_cors_allows_frontend_origin` | `CorsApiTest#allowsAnyOriginWithLiteralStar` | 同断言（字面量 `*`） |
| `test_ws_turn_builds_outline` / `test_ws_turn_persists_session` | — | **WS 用例，属 `0019`** |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `#mapWriteWithoutRecordedModeIsAllowed` / `#mapWriteWithoutSessionIdIs422` | 参考实现无「未记录模式」与「缺参数」用例 |
| `#failedMapWriteEmitsNoEvent` | `TC-I5`：参考实现无 REST 侧失败不产事件用例 |
| `#listPathsSkipsOwnedEmptyScratchButKeepsPlainEmptyPath` | 参考实现无「普通空路径仍列出」用例 |
| `#modeValueIsCheckedBeforePathExistence` | 参考实现无三级校验顺序用例 |
| `#quizReplaysOpenQuestionInsteadOfCreatingNewOne` | `INV-02` 的 REST 侧 |
| `#quizIsRefusedInOutlineMode` / `#quizIsAllowedWhenModeIsUnrecorded` | REST 学习端点的门控 |
| `#patchSessionAssemblesPreferencesFromKnownKeys` / `#patchSessionCreatesMissingSessionFirst` | 参考实现无偏好组装用例 |
| `#unknownProtocolFallsBackToOpenai` / `#nonDictDetectionIsDropped` / `#dictDetectionIsPreserved` | 参考实现无设置归一边界用例 |
| `#resolveProposalWithUnknownActionIs422` / `#coverageUnknownPathIs404` | 参考实现无捕获端点边界用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `GET /learning/paths` 用独立实现而非 `listPathOverviews` | 无（必要） | 两者跳过条件不同：REST 只跳过「有 owner 且无知识点」，`listPathOverviews` 跳过所有空 map。合并会让普通空路径消失 |
| D2 | 设置分组为**内存实现** | 低 | 落盘（`settings.json`）与提供方探测属 `0022`；本 change 只冻结分组语义与归一规则 |
| D3 | `WebConfig` 用 `@Bean` 装配 `LearningStore` | 无 | 参考实现把 store 作为 `create_app` 参数；Spring 侧用 bean 表达同一件事。工作目录由 `masterypath.data-dir` 指定 |
| D4 | `ApiExceptionHandler` 只做兜底 | 无 | 需要非默认状态码的端点（412 / 404）在控制器内显式 `try/catch`，与参考实现逐端点 `HTTPException` 一致 |
| D5 | `GET /learning/paths/{id}/suggest-question` 与 `POST /eval` 未实现 | 低 | 分别属 `0022`（Task Model）与 `0023`（评测 CLI），`design.md` Non-Goals 已声明 |
| D6 | `POST /learning/paths/{id}/sources`（文件夹扫描）与 `PUT /knowledge-bases` 未实现 | 低 | 属 `0021-knowledge-base`，`design.md` Non-Goals 已声明 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| WS 回合（`/ws`）与事件顺序 | `0019` | 需 WS 层 |
| 文件夹来源扫描、知识库 CRUD | `0021` | 知识库服务本体 |
| 设置落盘、提供方目录/探测、Task Model | `0022` | 设置与任务模型 |
| `suggest-question`、`eval` 端点 | `0022` / `0023` | 依赖 Task Model / 评测 CLI |
| 前端联调 | `0024` | 需全部端点就绪 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 675, Failures: 0` 来自
`artifacts/verify-0018-rest-api-20261104-214554.log`（671 项）与随后补测的
4 个捕获工具用例；参考实现对照见
`pytest tests/test_api.py tests/test_f0_api.py -q` 的 `18 passed`（其中 2 个 WS 用例
不计入本 change 的对照范围）。
