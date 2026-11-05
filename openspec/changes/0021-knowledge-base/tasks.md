# Tasks

## 1. 来源扫描

- [x] 1.1 `knowledge/FolderScanner`：`scanFolder` / `readText` / `SUPPORTED_EXTENSIONS` / `_SKIP_DIRS`

## 2. 知识库存储

- [x] 2.1 `storage/KnowledgeStore`：`kb.json` 读写 + `raw/` / `index/` 目录 + `_SAFE_ID` / `_BAD_NAME`
- [x] 2.2 `storage/KnowledgeStore` 文件操作：`writeUploads`（`_safe_join`）/ `importFolder` / `linkedFolders` / `collectDocuments`

## 3. 索引与检索

- [x] 3.1 `knowledge/VectorIndexer`：字符窗口分块 + 嵌入批次 + `index/chunks.json` 持久化
- [x] 3.2 `knowledge/VectorIndexer.search`：余弦 top-k，返回 `{text, source, score}`

## 4. 服务

- [x] 4.1 `service/KnowledgeService`：CRUD + `resolveEmbedding` + `normalizeChunkConfig` + `effectiveConfig` / `needsReindex`
- [x] 4.2 `service/KnowledgeService` 索引：`reindex`（后台线程 + 进度表 + 同 run 去重）+ `progress`
- [x] 4.3 `service/KnowledgeService.search` + `knowledgeContext` 拼装

## 5. 传输与接线

- [x] 5.1 `adapter/web/KnowledgeController`：12 个 `/knowledge-bases` 端点
- [x] 5.2 `LearningController` 补 `POST/GET /paths/{id}/sources` 与 `PUT .../knowledge-bases`
- [x] 5.3 接线：`WebConfig` 提供 `KnowledgeService`；`TurnRunner` 的 `sourceIndex` 知识库分支；`ChatService.knowledgeContext`

## 6. 测试与验证

- [x] 6.1 `FolderScannerTest` + `KnowledgeStoreTest` + `VectorIndexerTest`
- [x] 6.2 `KnowledgeServiceTest` + `KnowledgeApiTest`（参考块注入由 `KnowledgeServiceTest#knowledgeContextBuildsBlock` 覆盖）
- [x] 6.3 `.\scripts\verify.ps1 -Scope api -Change 0021-knowledge-base` → 全量 → `pytest tests/test_knowledge.py tests/test_knowledge_chat.py -q` 对照
- [x] 6.4 回填 `docs/verify/0021-knowledge-base.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0021-knowledge-base.md`、`README.md`
