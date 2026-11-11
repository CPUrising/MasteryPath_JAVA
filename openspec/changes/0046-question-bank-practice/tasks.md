# Tasks: 题库刷题与标签管理

## 1. 规范

- [x] 1.1 新增 `specs/question-bank/spec.md`（delta）
- [x] 1.2 `openspec validate 0046-question-bank-practice --strict` 通过

## 2. 后端

- [x] 2.1 `LearningStore.getBankEntry` / `listAllBankEntries`
- [x] 2.2 `domain/bank/QuestionBankGrading`（practiceable / grade）
- [x] 2.3 `domain/projection/QuestionBankProjector.practiceCard`
- [x] 2.4 `service/QuestionBankService`（list / tags / rename / merge / delete / batch / queue / attempt）
- [x] 2.5 `adapter/web/QuestionBankPracticeController` + `WebConfig` Bean
- [x] 2.6 `QuestionBankController.entryMap` 放宽为包内可见（输出不变）

## 3. 前端

- [x] 3.1 `web/lib/types.ts` 新增题库类型
- [x] 3.2 `web/lib/api.ts` 新增题库端点客户端
- [x] 3.3 `QuestionBankPanel` / `TagManagerDialog` / `PracticeRunner`
- [x] 3.4 `SpaceWorkspace` 题库 tab 接入工作台
- [x] 3.5 `zh.json` / `en.json` 文案

## 4. 测试与验证

- [x] 4.1 `QuestionBankGradingTest`
- [x] 4.2 `QuestionBankProjectorTest`
- [x] 4.3 `QuestionBankPracticeServiceTest`（含掌握闭环不变断言）
- [x] 4.4 `QuestionBankApiTest` + `ApiTestSupport` 装配
- [x] 4.5 `.\mvnw.cmd -q test` 全绿
- [x] 4.6 `cd web; npm run typecheck; npm run lint` 通过
- [x] 4.7 `.\scripts\verify.ps1 --scope all --change 0046-question-bank-practice` 产出证据
