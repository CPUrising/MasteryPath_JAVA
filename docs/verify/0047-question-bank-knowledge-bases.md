# 验证报告：0047-question-bank-knowledge-bases

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0047-question-bank-knowledge-bases` |
| 执行时间 | 2026-11-11 16:54–16:55（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1，本机 |
| 工具链 | JDK 21.0.11 / Maven Wrapper / Spring Boot 3.5.16 |
| 命令 | `.\scripts\verify.ps1 --scope all --change 0047-question-bank-knowledge-bases` |
| 日志 | `artifacts/verify-0047-question-bank-knowledge-bases-20261111-165440.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 变更摘要

- 题库条目新增**派生**字段 `knowledge_bases`（`[{id, name}]`），由所属路径绑定的知识库解析，`@JsonIgnore` 不落库。
- 一道题可属于多个知识库（路径绑定多个库时全部归属）；路径未绑定或已删除归「未关联」。
- 新增 `GET /questions/kb-groups`；`/questions/search`、`/questions/practice` 支持 `knowledge_base_ids`（OR）。
- 刷题卡投影新增 `knowledge_bases`（12 键），仍隐藏答案键。
- Space 题库面板新增知识库 chips、行内徽章；刷题器显示知识库。
- 零 DDL：`question_bank.data` 字节不变。

## 3. 验证结果

```
[INFO] Tests run: 991, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[PASS] 测试通过。
```

基线 977（`0046`）→ 991（新增 14 个用例：`QuestionBankKnowledgeBasesTest` 6、
`QuestionBankPracticeServiceTest` +4、`QuestionBankApiTest` +4）。

### 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 |
|---|---|---|---|
| Derive Knowledge Base Membership | 单知识库归属 | `QuestionBankPracticeServiceTest#knowledgeBasesAreNotPersisted`、`QuestionBankKnowledgeBasesTest#onlyKnowledgeBaseSources` | PASS |
| Derive Knowledge Base Membership | 多知识库同时归属 | `QuestionBankKnowledgeBasesTest#onlyKnowledgeBaseSources`、`QuestionBankApiTest#kbGroups` | PASS |
| Derive Knowledge Base Membership | 未关联 | `QuestionBankKnowledgeBasesTest#emptyWhenMissing`、`QuestionBankPracticeServiceTest#kbGroupsHasUnassignedLast` | PASS |
| Derive Knowledge Base Membership | 数据保持兼容 | `QuestionBankPracticeServiceTest#knowledgeBasesAreNotPersisted` | PASS |
| Knowledge Base Grouping And Filtering | 多归属计数 | `QuestionBankPracticeServiceTest#kbGroupsCountsMultiMembership`、`QuestionBankApiTest#kbGroups` | PASS |
| Knowledge Base Grouping And Filtering | OR 过滤 | `QuestionBankPracticeServiceTest#listFiltersByKnowledgeBaseOr`、`QuestionBankApiTest#searchFiltersByKnowledgeBase` | PASS |
| Knowledge Base Grouping And Filtering | 未知 id 返回空 | `QuestionBankApiTest#searchFiltersByKnowledgeBase` | PASS |
| Knowledge Base Grouping And Filtering | 刷题卡隐藏答案 | `QuestionBankApiTest#practiceFiltersByKnowledgeBase`、`QuestionBankProjectorTest#practiceCardWhitelist` | PASS |
| Knowledge Base Grouping And Filtering | 旧端点不变 | `QuestionBankApiTest#legacyQuestionsEndpointUnchanged` | PASS |

### 前端静态检查

| 命令 | 结果 |
|---|---|
| `cd web; npm run typecheck` | PASS |
| `cd web; npm run lint` | PASS（No ESLint warnings or errors） |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | 浏览器手工验收（chips 交互、徽章显示）未执行 | 一般 | 已登记 `docs/verification-debt.md`；REST 契约由 API 测试覆盖 |
| D2 | 多归属导致分组 `total` 之和可大于总题数 | 低 | 设计语义，已在 design.md 与 spec 中说明 |
| D3 | 「未关联」筛选走前端本地过滤，不作为后端参数 | 低 | 空 id 作为查询参数会被解析为 `[""]`，本地过滤更可靠；分组计数仍来自后端 |

## 5. 结论

- [x] Ready for archive（等待人类确认）
- [ ] 需修复后复验
- [ ] 需人类决策
