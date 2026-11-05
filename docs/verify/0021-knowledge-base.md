# 验证报告：0021-knowledge-base

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0021-knowledge-base` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-05 10:42:41（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0021-knowledge-base` |
| 日志 | `artifacts/verify-0021-knowledge-base-20261105-104241.log`（全量） |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

### 2.1 文件夹扫描（`FolderScannerTest`，8 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 只收支持扩展名，跳过隐藏项与构建目录 | `#scanFindsSupportedFilesOnly` | PASS | 对应 `test_scan_folder_finds_supported_files` |
| 来源 id 为 `src:<相对路径>` + 摘要 | `#sourceIdsAndExcerpts` | PASS | 对应 `test_source_map_has_label_and_basename` 的 id 部分 |
| `walk` 区分保留与被忽略数 | `#walkCountsSkipped` | PASS | `.hidden.md` + `node_modules/e.md` = 2 |
| 坏字节替换 | `#readTextReplacesBadBytes` | PASS | 对应 `test_read_text_replaces_bad_bytes` |
| `readText` 遵守上限 | `#readTextHonoursLimit` | PASS | |
| 文件夹不存在 / 不是目录抛错 | `#missingFolderThrows` | PASS | |
| 摘要折叠空白并截断 | `#excerptCollapsesWhitespace` | PASS | |
| 扩展名大小写不敏感 | `#extensionIsCaseInsensitive` | PASS | |

### 2.2 知识库存储（`KnowledgeStoreTest`，20 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 目录布局 | `#directoryLayout` | PASS | `knowledge_bases/<id>/{kb.json,raw,index}` |
| 重名加序号 | `#duplicateNameGetsSuffix` | PASS | |
| 名称清理与空名校验 | `#nameSlugAndValidation` | PASS | |
| 不安全 id → `UnknownKnowledgeBase` | `#unsafeIdRejected` | PASS | `../etc` / `a/b` / 空串 |
| 未知 id → `UnknownKnowledgeBase` | `#unknownIdRejected` | PASS | |
| 上传保留子目录 | `#writeUploadsPreservesStructure` | PASS | |
| **上传路径穿越被拒** | `#uploadTraversalRejected` | PASS | `../` / 绝对路径 / 盘符三种 |
| 不支持扩展名被拒 | `#unsupportedExtensionRejected` | PASS | |
| `conflict=skip` | `#uploadConflictSkip` | PASS | |
| `conflict=rename` | `#uploadConflictRename` | PASS | |
| 导入文件夹复制 | `#importFolderCopies` | PASS | |
| 链接文件夹不复制、重复被拒 | `#linkedFolder` | PASS | |
| `files` 列出两类来源 | `#filesListBothSources` | PASS | `linked` 标记正确 |
| `collectDocuments` 过滤空白 | `#collectDocumentsSkipsBlank` | PASS | |
| `scanStats` 统计 | `#scanStatsCounts` | PASS | |
| 删除后 404 | `#deleteRemovesDirectory` | PASS | |
| `update` 白名单 | `#updateWhitelist` | PASS | |
| `update` 空名被拒 | `#updateRejectsBlankName` | PASS | |
| `setIndexResult` 清零块数 | `#setIndexResultResetsChunks` | PASS | |
| `list` 按时间降序 | `#listSortedByUpdatedAt` | PASS | |

### 2.3 索引与检索（`VectorIndexerTest`，9 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 按文件报告进度 | `#buildReportsFileProgress` | PASS | 对应 `test_build_index_reports_file_progress` |
| 更大分块产生更少块 | `#largerChunkYieldsFewerChunks` | PASS | 对应 `test_larger_chunk_size_yields_fewer_chunks` |
| 无文档抛 `IndexUnavailable` | `#noDocumentsThrows` | PASS | |
| 纯空白文件计入 `skipped` | `#blankFileIsSkipped` | PASS | |
| 检索按相似度排序 | `#searchRanksBySimilarity` | PASS | 对应 `test_import_index_and_search` 的检索部分 |
| 空查询返回空 | `#blankQueryReturnsEmpty` | PASS | |
| 未建索引抛错 | `#searchBeforeIndexThrows` | PASS | |
| 分块步长与末块 | `#splitUsesStepAndKeepsTail` | PASS | |
| 余弦边界 | `#cosineEdgeCases` | PASS | |

### 2.4 知识库服务（`KnowledgeServiceTest`，17 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 无嵌入模型 → `rag_prerequisite_missing` | `#createWithoutEmbeddingIsPrerequisiteMissing` | PASS | 对应 `test_create_without_embedding_is_prerequisite_missing` |
| 有嵌入模型 → `empty` | `#createWithEmbeddingIsEmpty` | PASS | |
| 默认分块 512/64 | `#defaultConfigComesFromGlobal` | PASS | 对应 `test_global_default_and_kb_override` 前半 |
| 单库覆盖 | `#kbOverrideConfig` | PASS | 同上前半的后半 |
| 全局配置变更生效 | `#globalConfigChangeApplies` | PASS | 同上 |
| 非法分块配置被拒 | `#invalidChunkConfigRejected` | PASS | 对应 `test_invalid_chunk_config_rejected` |
| 索引后 `ready` | `#indexThenReady` | PASS | 对应 `test_import_index_and_search` |
| 无文档 `empty` + `percent=null` | `#reindexWithoutDocuments` | PASS | 对应 `test_reindex_requires_documents` |
| `progress` 就绪后 `percent=1.0` | `#progressAfterReady` | PASS | |
| 重复 reindex 返回同一 `run_id` | `#concurrentReindexReturnsSameRun` | PASS | 对应 `test_concurrent_reindex_runs_once` |
| `needs_reindex` 变化 | `#needsReindexAfterConfigChange` | PASS | 对应 `test_snapshot_and_needs_reindex` |
| 未知库进度抛错 | `#progressUnknownThrows` | PASS | |
| 检索排序 | `#searchReturnsRankedHits` | PASS | |
| 未就绪检索返回空 | `#searchNotReadyReturnsEmpty` | PASS | |
| 未知库检索抛错 | `#searchUnknownThrows` | PASS | |
| 参考块拼装 | `#knowledgeContextBuildsBlock` | PASS | 对应 `test_chat_injects_knowledge_context` 的块形状 |
| 参考块空情形 | `#knowledgeContextEmptyCases` | PASS | |

### 2.5 知识库 REST（`KnowledgeApiTest`，19 例）

| Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|
| 创建 201 + 前置缺失 | `#createWithoutEmbedding` | PASS | |
| 空名 → 422 | `#createBlankNameIs422` | PASS | |
| 非法分块 → 422 | `#createInvalidChunkConfigIs422` | PASS | |
| 未知库 4 个端点 → 404 | `#unknownIs404` | PASS | 对应 `test_delete_and_unknown` |
| 路径穿越形状 id → 404 | `#traversalShapedIdIs404` | PASS | 对应 `test_delete_and_unknown` 末句 |
| PATCH 标记 `needs_reindex` | `#patchConfigMarksNeedsReindex` | PASS | |
| PATCH 非法配置 → 422 | `#patchInvalidConfigIs422` | PASS | |
| DELETE 204 → 再删 404 | `#deleteThen404` | PASS | |
| 附加文件夹并索引到 `ready` | `#addFolderStartsIndexing` | PASS | 对应 `test_import_index_and_search` 全链路 |
| 缺 folder 422 / 不存在 422 | `#folderErrors` | PASS | |
| 无文档 reindex → `empty` | `#reindexWithoutDocuments` | PASS | |
| 链接文件夹不复制 | `#linkFolder` | PASS | |
| 上传并索引 | `#uploadIndexes` | PASS | 对应 `test_upload_indexes_and_reports_chunks` |
| 上传穿越 → 422 | `#uploadTraversalIs422` | PASS | 对应 `test_upload_path_traversal_rejected` |
| 删除文件 204 | `#deleteFile` | PASS | |
| `PUT knowledge-bases` 写来源 | `#setPathKnowledgeBases` | PASS | 对应 `test_create_path_with_knowledge_bases` |
| `kb_ids` 非数组 422 / 未知 404 | `#setPathKnowledgeBasesErrors` | PASS | |
| 附加文件夹为来源 | `#attachFolderSources` | PASS | 对应 `test_api_attach_and_list_sources` |
| sources 端点错误码 | `#sourcesEndpointErrors` | PASS | |

本 change 新增用例：**73 个**（`FolderScannerTest` 8 + `KnowledgeStoreTest` 20 +
`VectorIndexerTest` 9 + `KnowledgeServiceTest` 17 + `KnowledgeApiTest` 19），全部 PASS。
全量 **805 个，0 失败**（732 → 805）。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| API 专项 | `verify.ps1 -Scope api -Change 0021-knowledge-base` | Tests run: 139, Failures: 0 |
| 全量 | `verify.ps1 -Change 0021-knowledge-base` | BUILD SUCCESS，Tests run: 805, Failures: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` | 无回退（`0020` 的 732 项全部仍通过） |
| 参考实现对照 | `pytest tests/test_knowledge.py tests/test_knowledge_chat.py -q` | **22 passed** |

### 与参考实现的对齐方式

| Python 用例 | Java 对应 | 说明 |
|---|---|---|
| `test_scan_folder_finds_supported_files` | `FolderScannerTest#scanFindsSupportedFilesOnly` | 同断言（labels 三件、kind=file、摘要含 Alpha） |
| `test_source_map_has_label_and_basename` | `#sourceIdsAndExcerpts` | id 与基名别名已由 `0013` 的 `SourceResolver` 覆盖 |
| `test_read_text_replaces_bad_bytes` | `#readTextReplacesBadBytes` | 同断言（前缀 `ok `） |
| `test_attach_sources_and_build_uses_them` | `KnowledgeApiTest#attachFolderSources` + `0013` 的 `SourceAttachmentTest` | 扫描 + 附加同断言；build 的缺口分类由 `0007` 覆盖 |
| `test_api_attach_and_list_sources` | `#attachFolderSources` | 同断言（3 个来源、label 集合） |
| `test_create_without_embedding_is_prerequisite_missing` | `KnowledgeServiceTest#createWithoutEmbeddingIsPrerequisiteMissing` | 同断言（状态 + id + 列表） |
| `test_import_index_and_search` | `#indexThenReady` + `KnowledgeApiTest#addFolderStartsIndexing` | 同断言（ready / percent=1.0 / doc_count / files） |
| `test_reindex_requires_documents` | `#reindexWithoutDocuments` | 同断言（empty / percent=null） |
| `test_delete_and_unknown` | `KnowledgeApiTest#deleteThen404` + `#unknownIs404` + `#traversalShapedIdIs404` | 同断言（204 / 404 / 穿越 404） |
| `test_upload_indexes_and_reports_chunks` | `#uploadIndexes` | 同断言（indexing / uploaded=1 / ready / doc_count） |
| `test_upload_path_traversal_rejected` | `#uploadTraversalIs422` | 同断言（422） |
| `test_concurrent_reindex_runs_once` | `#concurrentReindexReturnsSameRun` | 同断言（同一 `run_id`） |
| `test_global_default_and_kb_override` | `#defaultConfigComesFromGlobal` + `#kbOverrideConfig` + `#globalConfigChangeApplies` | 同断言（effective_config / index_config_source） |
| `test_invalid_chunk_config_rejected` | `#invalidChunkConfigRejected` | 同断言（422） |
| `test_snapshot_and_needs_reindex` | `#needsReindexAfterConfigChange` | 同断言（indexed_config / needs_reindex 翻转） |
| `test_larger_chunk_size_yields_fewer_chunks` | `VectorIndexerTest#largerChunkYieldsFewerChunks` | 同断言（单调性） |
| `test_build_index_reports_file_progress` | `#buildReportsFileProgress` | 同断言（首末帧） |
| `test_chat_injects_knowledge_context` | `KnowledgeServiceTest#knowledgeContextBuildsBlock` | 块形状同断言；chat 侧注入由 `0020` 的 `ChatServiceTest` 覆盖 |

### Java 侧超出参考测试覆盖的部分

| Java 用例 | 补齐的空白 |
|---|---|
| `KnowledgeStoreTest#uploadTraversalRejected` 的三种形态 | 参考实现只测 `../evil.md` 一种 |
| `KnowledgeStoreTest#updateWhitelist` / `#updateRejectsBlankName` | 参考实现无白名单与空名用例 |
| `KnowledgeStoreTest#setIndexResultResetsChunks` | 参考实现无 `chunk_count` 清零断言 |
| `KnowledgeStoreTest#filesListBothSources` | 参考实现无 `linked` 标记断言 |
| `VectorIndexerTest#splitUsesStepAndKeepsTail` / `#cosineEdgeCases` | 参考实现无分块与余弦的单元用例 |
| `KnowledgeServiceTest#searchNotReadyReturnsEmpty` / `#searchUnknownThrows` | 参考实现无检索三重短路的用例 |
| `KnowledgeApiTest#folderErrors` / `#sourcesEndpointErrors` | 参考实现无 422 / 404 分工用例 |

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | **不引入 LlamaIndex，自实现分块 + 余弦检索** | 中 | JVM 无等价库；引入向量库会带来新依赖与持久化格式。检索契约（`{text, source, score}`、top-k、余弦）保持一致 |
| D2 | **分块按字符而非 token** | 中 | 无 tiktoken；`indexed_config.tokenizer` 写 `char/cl100k_base` 标明差异；`needs_reindex` 判定只比较 `chunk_size` / `chunk_overlap`，行为一致 |
| D3 | 索引持久化为 `index/chunks.json` | 中 | 本实现自有格式；参考实现的 LlamaIndex 目录不互通（`0024` 验收只要求学习数据互通） |
| D4 | `knowledge_context` 的 `top_k` 用 4 | 无 | 与参考实现 `knowledge_context(top_k=4)` 一致；`rag` 工具仍用 5 |
| D5 | `KnowledgeStore` 用文件系统而非 SQLite | 无 | 参考实现就是 `kb.json` + 目录；改库会让旧数据无法互通 |
| D6 | 索引用单线程池而非每库一线程 | 无 | 参考实现是 `threading.Thread`；单线程池仍能并发，且同库去重逻辑一致 |
| D7 | `ApiTestSupport` 新增 `knowledgeService` 字段 | 无（必要） | `LearningController` 多了三个 sources 端点，需要它 |

## 5. 未覆盖项（已登记验证债）

| 场景 | 归属 change | 原因 |
|---|---|---|
| 提供方探测（`/settings/providers/probe`）、Task Model | `0022` | 设置与任务模型 |
| `suggest-question`、`eval` 端点 | `0022` / `0023` | 依赖 Task Model / 评测 CLI |
| 真实嵌入 provider 的端到端 | `0024` | 需真实 Key，属手工验证项 |
| 前端联调 | `0024` | 需全部端点就绪 |

## 6. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 805, Failures: 0` 来自
`artifacts/verify-0021-knowledge-base-20261105-104241.log`；API 专项 139 项来自
`artifacts/verify-0021-knowledge-base-20261105-104210.log`；参考实现对照见
`pytest tests/test_knowledge.py tests/test_knowledge_chat.py -q` 的 `22 passed`。
