# Tasks: 题库按知识库分类

## 1. 规范

- [x] 1.1 新增 `specs/question-bank/spec.md`（delta）
- [x] 1.2 `openspec validate 0047-question-bank-knowledge-bases --strict` 通过

## 2. 后端

- [x] 2.1 `domain/model/KnowledgeBaseRef`（record）
- [x] 2.2 `QuestionBankEntry.knowledgeBases`（`@JsonIgnore` 派生）
- [x] 2.3 `domain/bank/QuestionBankKnowledgeBases`（`of` / `matches`）
- [x] 2.4 `QuestionBankService`：`pathIndex` / `enrich` / `list(kbIds)` / `practiceQueue(kbIds)` / `kbGroups`
- [x] 2.5 `QuestionBankProjector.practiceCard` 追加 `knowledge_bases`
- [x] 2.6 `QuestionBankPracticeController`：`/kb-groups` + `knowledge_base_ids` 参数

## 3. 前端

- [x] 3.1 `web/lib/types.ts` 新增 `KnowledgeBaseRef` / `QuestionKbGroup`，扩展 `QuestionEntry` / `PracticeCard`
- [x] 3.2 `web/lib/api.ts` 新增 `questionKbGroups`，扩展搜索/刷题入参
- [x] 3.3 `QuestionBankPanel` 知识库 chips + 分组 + 行内徽章
- [x] 3.4 `PracticeRunner` 显示知识库徽章
- [x] 3.5 `zh.json` / `en.json` 文案

## 4. 测试与验证

- [x] 4.1 `QuestionBankKnowledgeBasesTest`
- [x] 4.2 `QuestionBankPracticeServiceTest` 扩展（OR 过滤、kbGroups、未关联）
- [x] 4.3 `QuestionBankApiTest` 扩展（kb-groups、参数、旧端点不变）
- [x] 4.4 `QuestionBankProjectorTest` 更新键集合
- [x] 4.5 `.\mvnw.cmd -q test` 全绿
- [x] 4.6 `cd web; npm run typecheck; npm run lint`
- [x] 4.7 `verify.ps1 --change 0047-question-bank-knowledge-bases` 产出证据
