# Design: 领域模型与 JSON 映射

## Context

参考实现用 Pydantic v2 定义 20 个实体，其行为有三处是 Java 侧必须显式复刻的「隐式契约」：

| Python 行为 | 后果 | Java 侧风险 |
| --- | --- | --- |
| `model_config = ConfigDict(extra="ignore")` | 旧库多出的字段不会导致加载失败 | Jackson 默认对未知字段抛 `UnrecognizedPropertyException` |
| `KnowledgeType._missing_` 映射 `"概念型"→concept` | 历史数据可读 | 枚举 `valueOf` 只认英文，历史值直接崩 |
| `PendingQuestion._read_legacy_option_strings` | 旧式 `["A: x"]` 可读 | 强类型 `List<PendingOption>` 无法接受字符串 |

另有一处差异：Python 用 snake_case 字段名直接序列化（`path_id`、`mastery_levels`），
旧库 JSON 与前端契约都依赖 snake_case，Java 默认 camelCase 会直接破坏数据兼容。

## Goals / Non-Goals

**Goals**
1. 20 个实体 + 7 个枚举，字段名/默认值/可空性 100% 对齐 `learning/models.py`。
2. 三条兼容行为全部有测试（`TC-A1`…`A6`）。
3. `domain.model` 包不依赖 Spring（只依赖 Jackson 注解与 `java.*`）。

**Non-Goals**
1. 不做校验框架统一（Bean Validation 在 REST 层用，领域层用构造器校验）。
2. 不做实体不可变化（`LearningProgress` 是可变的聚合根，服务层就地改字段）。
3. 不做 equals/hashCode 全字段实现（只有 `TC-A6` 需要往返相等，用 JSON 比较即可）。

## Decisions

1. **字段命名：全局 `SNAKE_CASE` 策略**
   - 选择：`JsonSupport.objectMapper()` 设置 `PropertyNamingStrategies.SNAKE_CASE`。
   - 理由：一处配置覆盖 20 个实体，避免 200+ 个 `@JsonProperty` 注解；与旧库、前端契约天然一致。
   - 放弃的方案：逐字段注解（噪声大、易漏）；改字段名为 snake_case（违反 Java 命名惯例）。

2. **未知字段：`FAIL_ON_UNKNOWN_PROPERTIES = false`**
   - 选择：关闭失败。
   - 理由：直接对齐 `extra="ignore"`；这是旧库能加载的前提（`TC-A4`）。
   - 风险：拼错字段名会被静默忽略。缓解：`TC-A6` 往返测试会暴露「写出去又读不回来」的字段。

3. **枚举历史值映射：`@JsonCreator` 静态工厂**
   - 选择：`KnowledgeType.fromValue(String)` 先查英文，再查中文映射表，都不中则抛异常。
   - 理由：Pydantic 的 `_missing_` 语义就是「未知即拒绝、历史即映射」；用 `@JsonCreator` 保证
     反序列化路径走同一逻辑（`TC-A2`/`TC-A3`）。
   - 放弃的方案：`@JsonEnumDefaultValue`（会静默兜底成某个值，违反「未知拒绝」）。

4. **实体用可变类 + 显式默认值，而非 record**
   - 选择：普通 class + 字段 + getter/setter（`LearningProgress` 需要就地变更）。
   - 理由：聚合根在 `mutate` 回调里被逐步修改；record 每次改动都要构造新实例，与参考实现的
     「读-改-写」模型不符，且会让服务层代码变得笨重。
   - 放弃的方案：record + `with` 风格复制（会在 `0006` 起产生大量样板）。

5. **校验位置：构造器/setter 内，而非注解**
   - 选择：`KnowledgePoint` 在 `setName` 与构造器里校验 `name` 长度；`LearningProgress` 校验
     `desired_retention ∈ [0.7, 0.99]`。
   - 理由：Jackson 反序列化**不经过** Bean Validation 注解，只有写在 setter 里才能同时覆盖
     「直接构造」与「JSON 反序列化」两条路径（`TC-A1` 要求后者也拒绝）。

6. **旧式选项解析：静态工厂 + `@JsonCreator`**
   - 选择：`PendingQuestion.options` 的 setter 接受 `List<?>`，若元素是 `String` 则走
     `OptionParser.parse`（正则 `^\s*([A-Za-z])\s*[:.)、]\s*(.+)$`），否则按对象解析。
   - 理由：正则与「全部带标签才用标签、否则按位置编号」的规则必须与 `pending.py` 逐字一致。
   - 放弃的方案：自定义 `JsonDeserializer`（等价但更绕，且 setter 方案对 `TC-A5` 更直观）。

## Risks / Trade-offs

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 20 个实体手写样板多，易漏字段 | 数据丢字段 | `TC-A6` 往返测试 + `0003` 用真实旧库比对字段快照 |
| `SNAKE_CASE` 对缩写字段（`kp_id`、`ef`）的转换 | 命名不符预期 | Jackson 的 `SNAKE_CASE` 对 `kpId` → `kp_id` 正确；用测试固化关键字段名 |
| 浮点默认值精度（`0.3`、`0.9`） | 与 Python 微小差异 | 统一用 `double`；只做阈值比较，不做等值断言 |

## Migration Plan

1. 先写枚举与映射（无依赖）。
2. 再写叶子实体（`KnowledgePoint` / `LearningModule` / `PendingQuestion` 等）。
3. 最后写聚合根 `LearningProgress`（依赖前两者）。
4. `JsonSupport` 与测试。
5. 跑 `verify.ps1`，回填报告。

## Open Questions

- 无。字段清单以 `../MasteryPath/masterypath/learning/models.py` 为唯一依据。
