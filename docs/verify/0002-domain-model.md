# 验证报告：0002-domain-model

## 1. 基本信息

| 项 | 值 |
|---|---|
| change id | `0002-domain-model` |
| 执行人 | AI 智能体（Build + Verify），待人类审查 |
| 执行时间 | 2026-11-04 12:24:36（Asia/Shanghai） |
| 机器 / 系统 | Windows / PowerShell 5.1 |
| 工具链版本 | JDK 21.0.11+9-LTS、Maven 3.9.16（wrapper）、Spring Boot 3.5.16 |
| 运行环境 | 本地（非 CI） |
| 命令 | `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1 -Change 0002-domain-model` |
| 日志 | `artifacts/verify-0002-domain-model-20261104-122436.log` |
| 结果包 | `target/surefire-reports/` |

## 2. 需求覆盖

| Requirement | Scenario | 测试方法 | 结果 | 备注 |
|---|---|---|---|---|
| Typed Knowledge Point | TC-A1 名称过短（构造） | `KnowledgePointTest#nameTooShortRejectedOnConstruction` | PASS | |
| Typed Knowledge Point | TC-A1 名称过短（反序列化） | `KnowledgePointTest#nameTooShortRejectedOnDeserialization` | PASS | 证明校验不只在构造器里 |
| Typed Knowledge Point | TC-A2 未知类型拒绝 | `KnowledgePointTest#unknownTypeRejected` | PASS | |
| Typed Knowledge Point | TC-A3 历史中文映射 | `KnowledgePointTest#legacyChineseTypeMapped` | PASS | `概念型 → concept` |
| Typed Knowledge Point | TC-A4 忽略未知字段 | `KnowledgePointTest#unknownFieldsIgnored` | PASS | |
| Typed Knowledge Point | grounding 派生 | `KnowledgePointTest#groundingIsDerived` | PASS | 且不进序列化 |
| Learning Tools | TC-A5 旧式选项解析 | `PendingQuestionTest#legacyOptionStringsParsed` | PASS | |
| Learning Tools | 无标签按位置编号 | `PendingQuestionTest#labelsAssignedPositionallyWhenMissing` | PASS | |
| Learning Tools | 四种分隔符 | `PendingQuestionTest#allLabelSeparatorsRecognized` | PASS | `:` `.` `)` `、` |
| Learning Tools | 新式对象形态 | `PendingQuestionTest#structuredOptionsAccepted` | PASS | |
| Learning Domain | TC-A6 往返相等 | `LearningProgressJsonTest#roundTripKeepsAllFields` | PASS | 含二次往返稳定性 |
| Learning Domain | 往返后可按 id 定位 | `LearningProgressJsonTest#findKnowledgePointAfterRoundTrip` | PASS | |
| Learning Domain | 空聚合往返 | `LearningProgressJsonTest#emptyProgressRoundTrip` | PASS | |
| —（数据等价） | 读参考实现 JSON | `ReferenceJsonCompatibilityTest#readsReferenceImplementationJson` | PASS | fixture 由 Python 原样产出 |
| —（数据等价） | 写出兼容形态 | `ReferenceJsonCompatibilityTest#writesBackCompatibleShape` | PASS | |
| —（字段契约） | 聚合 snake_case | `JsonFieldNameTest#progressUsesSnakeCase` | PASS | |
| —（字段契约） | 模块/知识点 snake_case | `JsonFieldNameTest#moduleAndKnowledgePointUseSnakeCase` | PASS | |
| —（字段契约） | 布尔字段名 | `JsonFieldNameTest#booleanFieldsKeepPythonNames` | PASS | `is_correct` 而非 `correct` |
| —（字段契约） | 调度状态 snake_case | `JsonFieldNameTest#repetitionStateUsesSnakeCase` | PASS | |
| —（字段契约） | 题目字段 snake_case | `JsonFieldNameTest#pendingQuestionHidesNothingButNamesAnswersInSnakeCase` | PASS | |

本 change 内新增用例：**21 个，全部 PASS**。加上 `0001` 的 3 个（其中 `AppContextTest` 2 项 + `PackageLayoutTest` 1 项），全量 **23 个，0 失败**。

## 3. 回归

| 范围 | 命令 | 结果 |
|---|---|---|
| 全量测试 | `mvnw.cmd -B -ntp test` | BUILD SUCCESS，Tests run: 23, Failures: 0, Errors: 0, Skipped: 0 |
| 既有通过项是否回退 | 对照 `docs/regression-log.md` 的 `0001` 行 | 无回退（`AppContextTest` 2 项、`PackageLayoutTest` 1 项仍 PASS） |
| 参考实现对照 | Python 侧 `LearningProgress.model_dump_json()` 实际输出 → 存为 fixture | 已执行，Java 读取成功 |

### 跨语言兼容验证方法

1. 在 `../MasteryPath` 用 `.venv` 运行 Python，构造一个含模块、知识点（历史中文类型）、复习状态、待答题目的聚合；
2. 取其 `model_dump_json(indent=2)` 的**原始输出**，不做任何手工修改，存为
   `src/test/resources/fixtures/reference-progress.json`；
3. Java 侧用 `JsonSupport.objectMapper()` 直接反序列化，逐字段断言。

这一步是「数据等价」维度的实证，不是自证：fixture 的每一行都来自 Python。

## 4. 偏差与风险

| 编号 | 偏差 | 等级 | 处理 |
|---|---|---|---|
| D1 | `JsonSupport` 使用 `setVisibility(...)` 时被 javac 提示「已过时的 API」（deprecation） | 一般 | 不影响功能与行为。`0003` 引入存储层时统一清理该告警（或改用 `MapperBuilder`），本 change 不扩大范围 |
| D2 | `is_correct` / `voided` / `independent` 等布尔字段若用默认命名会被 Jackson 推断为 `correct`/`voided`/`independent` 中的错误形态 | 严重（若未处理会导致数据不兼容） | 已用 `@JsonProperty` 显式命名，并由 `JsonFieldNameTest#booleanFieldsKeepPythonNames` 固化 |
| D3 | `NON_NULL` 序列化策略会省略空值，导致 Python 侧读取时缺字段 | 严重（若未处理会破坏互读） | 已改为 `Include.ALWAYS`（对齐 Pydantic 默认），由往返测试固化 |
| D4 | 编译日志中文显示为 `????`（javac 输出编码与控制台不一致） | 一般 | 仅影响日志可读性，不影响构建结果。后续可在 `pom.xml` 设置 `maven.compiler.encoding` 与 `-Dfile.encoding` |

> D2、D3 是在实现过程中被测试**主动发现**的两个真实兼容性缺陷，均已在提交前修正。

## 5. 结论

- [x] Ready for archive
- [ ] 需修复后复验
- [ ] 需人类决策

**证据说明**：`BUILD SUCCESS` 与 `Tests run: 23, Failures: 0` 来自 `artifacts/verify-0002-domain-model-20261104-122436.log`；跨语言 fixture 在 `src/test/resources/fixtures/reference-progress.json`。
