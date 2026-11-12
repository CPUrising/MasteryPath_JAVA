# 05 · 设计模型

> 对应作业工件 3。系统级图（C4 上下文 / 容器 / 组件）在 [02](02-architecture.md)，智能体回合状态机在 [03](03-agentic-design.md)；
> 本节聚焦**领域结构**与**关键用例的交互**。所有图使用 UML 语义（Mermaid 语法），类名 / 表名与代码一致。

## 1. 领域类图（关键抽象、职责、重要属性）

```mermaid
classDiagram
    direction LR

    class LearningProgress {
        <<AggregateRoot>>
        +String pathId
        +String name
        +String goal
        +String ownerSessionId
        +List~TopicSource~ sources
        +Map learnerProfile
        +double desiredRetention
        +List~LearningModule~ modules
        +Map knowledgeTypes
        +Map masteryLevels
        +Map qualitativeMastery
        +List~QuizAttempt~ quizAttempts
        +List~LearningEvidence~ learningEvidence
        +Map repetitionStates
        +List~ReviewTask~ reviewQueue
        +Map learnerMasteryOverrides
        +Map deferredObjectives
        +List~SourceGap~ sourceGaps
        +List~ObjectiveProposal~ proposals
        +CoverageReport coverage
        +PendingQuestion pendingQuestion
        +int consecutiveQuestions
        +long version
    }

    class LearningModule {
        +String id
        +String name
        +int order
        +String objective
        +double displayTarget
        +List~KnowledgePoint~ knowledgePoints
    }

    class KnowledgePoint {
        +String id
        +String name
        +KnowledgeType type
        +String moduleId
        +List~String~ prerequisiteIds
        +List~String~ sourceIds
        +Grounding grounding
        +List~String~ requiredEvidence
    }

    class PendingQuestion {
        +String questionId
        +String knowledgePointId
        +String prompt
        +QuestionType questionType
        +String expectedAnswer
        +List~PendingOption~ options
        +String explanation
        +String difficulty
        +List~String~ acceptedAnswers
    }

    class QuizAttempt {
        +String questionId
        +boolean isCorrect
        +String userAnswer
        +String errorType
        +boolean independent
        +boolean voided
    }

    class LearningEvidence {
        +String evidenceId
        +String knowledgePointId
        +AssessmentType assessmentType
        +AssessmentResult result
        +int hintsUsed
        +String source
        +boolean voided
    }

    class RepetitionState {
        +String kpId
        +int intervalIndex
        +int consecutiveCorrect
        +int consecutiveWrong
        +Double nextReviewAt
        +Double lastReviewAt
        +boolean scheduledAfterFailure
        +double easeFactor
        +String provenance
    }

    class ReviewTask {
        +String id
        +String knowledgePointId
        +double dueAt
        +double priority
        +double forgettingRisk
        +String state
    }

    class SourceGap {
        +String id
        +GapKind kind
        +String ref
        +String reason
        +List~String~ candidateSourceIds
        +GapResolution resolution
    }

    class ObjectiveProposal {
        +String id
        +String name
        +KnowledgeType type
        +String reason
        +String status
        +String proposedBy
    }

    class LearnerMasteryOverride {
        +String knowledgePointId
        +String note
        +double createdAt
    }

    class DeferredObjective {
        +String knowledgePointId
        +String note
    }

    class TopicSource {
        +String id
        +String kind
        +String sourceId
        +String label
        +String excerpt
        +boolean available
    }

    class TopicMetadata {
        +String pathId
        +String goal
        +String status
    }

    class CoverageReport {
        +int documents
        +int covered
        +List missing
        +boolean reported
        +double groundingRatio
        +boolean acyclic
    }

    LearningProgress "1" *-- "0..*" LearningModule
    LearningModule "1" *-- "0..*" KnowledgePoint
    LearningProgress "1" *-- "0..*" TopicSource
    LearningProgress "1" *-- "1" TopicMetadata
    LearningProgress "1" o-- "0..1" PendingQuestion : 单槽位
    LearningProgress "1" *-- "0..*" QuizAttempt
    LearningProgress "1" *-- "0..*" LearningEvidence : 只追加
    LearningProgress "1" *-- "0..*" RepetitionState
    LearningProgress "1" *-- "0..*" ReviewTask
    LearningProgress "1" *-- "0..*" SourceGap
    LearningProgress "1" *-- "0..*" ObjectiveProposal
    LearningProgress "1" *-- "0..*" LearnerMasteryOverride
    LearningProgress "1" *-- "0..*" DeferredObjective
    LearningProgress "1" o-- "0..1" CoverageReport
    KnowledgePoint "1" o-- "0..*" QuizAttempt : 证据
    KnowledgePoint "1" o-- "0..*" LearningEvidence : 证据
    KnowledgePoint "1" o-- "1" RepetitionState : 每点一份
    KnowledgePoint "0..*" --> "0..*" KnowledgePoint : prerequisiteIds
    ObjectiveProposal ..> KnowledgePoint : 大纲 reconcile 时转正
```

### 1.1 规则对象（无状态、纯函数，与实体分开画）

```mermaid
classDiagram
    direction TB

    class MasteryPolicy {
        +gateThreshold(KnowledgeType) double
        +isMastered(progress, kpId) boolean
        +nextObjective(progress, now) NextStep
        +dueReviews(progress, now) List
        +admission(mode, hasOutline) Optional~String~
    }
    class MasteryScore {
        +recentWeighted(attempts) double
        +int WINDOW
        +double[] WEIGHTS
    }
    class Grading {
        +grade(question, answer) Decision
        +normalize(text) String
        +similarity(a,b) double
    }
    class Decision {
        +Verdict verdict
        +String reason
        +boolean isUngraded()
    }
    class Sm2Scheduler {
        +seed(now, provenance) RepetitionState
        +applyReview(state, quality, now) RepetitionState
        +upsertQueue(progress, now) void
    }
    class ModeGate {
        +toolIsAllowed(tool, mode) boolean
        +admissionError(mode, hasOutline) Optional~String~
        +suggestMode(tool) SessionMode
    }
    class ToolClassification {
        +OUTLINE_ONLY
        +STUDY_REVIEW
        +SHARED_TOOLS
        +assertClassificationComplete(names)
    }
    class OutlineBuilder {
        +build(spec, sources) BuildResult
        +revise(progress, ops) ReviseResult
        +analyzeGaps(progress) List~SourceGap~
    }
    class Projectors {
        +questionCard(PendingQuestion)
        +quizSet(QuizSet)
        +bankCard(QuestionBankEntry)
    }

    MasteryPolicy ..> MasteryScore : 使用
    MasteryPolicy ..> RepetitionState : 读锚点
    Grading ..> Decision : 产出
    ModeGate ..> ToolClassification : 查表
    OutlineBuilder ..> CoverageReport : 产出
    Projectors ..> PendingQuestion : 白名单投影
```

**为什么把"规则"单独画？** 因为它们是可被替换、可被单测、可被第三部分 PoC 标定的部分；实体是数据的形状，规则是产品的承诺。把两者混在一张图里，会让人误以为"改规则 = 改数据结构"。

> 图中方法名为**职责示意**（表达"这类规则负责什么"），精确签名以代码为准；常量值是契约（见 [04](04-adr.md) ADR-04/05/07 与 `domain` 源码）。

## 2. 存储模型（ER）

```mermaid
erDiagram
    paths {
        TEXT path_id PK
        INTEGER version
        TEXT data
        REAL updated_at
    }
    events {
        INTEGER id PK
        TEXT path_id
        INTEGER revision
        TEXT event_type
        TEXT payload
        TEXT session_id
        TEXT turn_id
        REAL created_at
    }
    interactions {
        TEXT interaction_id PK
        TEXT path_id
        TEXT status
        TEXT data
        REAL created_at
        REAL updated_at
    }
    question_bank {
        TEXT entry_id PK
        TEXT path_id
        TEXT data
        REAL created_at
        REAL updated_at
    }
    sessions {
        TEXT session_id PK
        TEXT path_id
        TEXT title
        TEXT mode
        TEXT status
        INTEGER message_count
        TEXT preferences
        REAL created_at
        REAL updated_at
    }
    messages {
        INTEGER id PK
        TEXT session_id
        TEXT turn_id
        TEXT role
        TEXT content
        TEXT data
        REAL created_at
    }
    quiz_sets {
        TEXT set_id PK
        TEXT path_id
        TEXT session_id
        TEXT turn_id
        TEXT status
        TEXT data
    }
    quiz_items {
        TEXT item_id PK
        TEXT set_id
        INTEGER seq
        TEXT data
    }

    paths ||--o{ events : "revision 对账"
    paths ||--o{ interactions : "题目事务"
    paths ||--o{ question_bank : "题库"
    paths ||--o{ sessions : "会话绑定"
    sessions ||--o{ messages : "消息"
    paths ||--o{ quiz_sets : "题卷"
    quiz_sets ||--o{ quiz_items : "题目"
```

三点设计说明：

1. **`paths.data` 是唯一真相**（ADR-02），其余表都是"便于查询/审计的旁路"：`events` 只追加不重建，`question_bank` 是证据的可读副本，`interactions` 是幂等锚点。
2. **`sessions.mode` / `has_pending_question` 是冗余字段**，用于列表页与导航，不参与判定；判定永远读聚合。
3. **知识库不在库里**：`knowledge_bases/<id>/{kb.json, raw/, index/chunks.json}` 以文件系统为真相，理由是原始文档必须人类可读、可被用户直接复制（见 [02](02-architecture.md) §3）。

## 3. 交互模型（关键用例）

### 3.1 UC-02 生成大纲（含缺口处理）

```mermaid
sequenceDiagram
    autonumber
    participant U as 学习者
    participant W as WsTurnHandler
    participant T as TurnRunner
    participant A as AgentLoop
    participant M as 模型
    participant OT as OutlineTools
    participant OB as OutlineBuilder
    participant S as LearningService
    participant DB as LearningStore

    U->>W: "帮我规划大纲"（mode=outline）
    W->>T: TurnRequest
    T->>T: 读来源摘录 + 画像 + 状态快照
    T->>A: stream(history, tools)
    A->>M: chat/completions
    M-->>A: tool_call: mastery_build{modules[]}
    A->>OT: build(spec, confirm?)
    OT->>S: buildOutline(...)
    S->>OB: 校验（名称/类型/唯一/前置可解析）
    alt 路径非空且未确认
        OB-->>OT: 拒绝 + 将丢失的内容清单
        OT-->>A: ok=false（可操作错误）
    else 校验通过
        OB->>OB: 生成模块/KP + 覆盖报告 + 缺口分类
        S->>DB: mutate（写地图 + source_gaps + coverage + 事件）
        OT-->>A: ok + map 摘要 + gaps[]
        A-->>U: tool_result → text_delta（解释缺口）→ turn_end
    end
    U->>W: 选择"接受缺口"或"补充来源"
    W->>S: resolveGap / 追加来源
    S->>DB: mutate（resolution / sources）
```

### 3.2 UC-08 定性评估（concept / design）

```mermaid
sequenceDiagram
    autonumber
    participant U as 学习者
    participant A as AgentLoop
    participant RT as RepairTools.Assess
    participant S as LearningService
    participant P as MasteryPolicy
    participant DB as LearningStore

    A->>RT: mastery_assess{kp_id, passed, note}
    RT->>S: assess(...)
    S->>P: 校验类型必须是 concept/design
    alt 类型不符（memory/procedure）
        P-->>RT: 拒绝（应走量化门槛）
        RT-->>A: ok=false
    else 通过
        S->>S: 写 qualitativeMastery[kp]=passed
        S->>S: 追加 LearningEvidence(type=qualitative, result=CORRECT/PARTIAL)
        S->>S: 不写 masteryLevels（关键）
        S->>DB: mutate + 事件；通过则播种复习
        RT-->>A: ok + 掌握状态
    end
```

### 3.3 UC-10 复习到期

```mermaid
sequenceDiagram
    autonumber
    participant U as 学习者
    participant T as TurnRunner
    participant P as MasteryPolicy
    participant ST as StudyTools
    participant SM as Sm2Scheduler
    participant DB as LearningStore

    U->>T: 进入 review 模式（REST 写模式 + 下一回合）
    T->>P: nextObjective(progress, now)
    P->>P: 待答 → 到期复习（风险降序 / 到期升序）→ 未掌握前沿
    P-->>T: action=review, kp=最早到期项
    T->>ST: mastery_quiz（仅已掌握点）
    ST-->>U: question_card
    U->>T: 作答
    T->>SM: applyReview(state, quality, now)
    alt 失败（quality 小于 3）
        SM->>SM: intervalIndex=0, nextReviewAt=now+600s, scheduledAfterFailure=true
    else 成功
        SM->>SM: intervalIndex+1, nextReviewAt=now+interval×86400
    end
    SM->>DB: mutate（重排复习队列，每 KP 至多一条）
    Note over P: 复习只巩固，不推进前沿
```

### 3.4 声明已掌握（双轨）

```mermaid
sequenceDiagram
    autonumber
    participant U as 学习者
    participant LC as LearningController
    participant S as LearningService
    participant P as MasteryPolicy
    participant SM as Sm2Scheduler
    participant DB as LearningStore

    U->>LC: POST /learning/paths/{id}/objectives/{kp}/override
    LC->>S: overrideMastery(kp, note)
    S->>P: 前置是否满足？（不满足 → 412）
    S->>S: 写 learnerMasteryOverrides（来源 learner）
    Note over S: 不写 masteryLevels、不写 LearningEvidence
    S->>SM: 以 provenance=learner 播种（首间隔 ×0.5）
    S->>DB: mutate + 声明事件（不含证据）
    LC-->>U: 新状态（下一路标已推进）
```

### 3.5 知识库装配与检索（回合侧）

```mermaid
sequenceDiagram
    autonumber
    participant T as TurnRunner
    participant KS as KnowledgeService
    participant VI as VectorIndexer
    participant HE as HttpEmbedder
    participant MT as MasteryTools

    T->>KS: 解析路径绑定的知识库（三态：有 / 无 / 不可用）
    KS-->>T: kbIds + 状态
    T->>KS: 组装参考块（每库 topK=4）
    KS->>VI: 加载 chunks.json
    KS->>HE: 向量化查询
    HE-->>KS: 向量
    KS->>KS: 余弦相似度排序 → 文本片段
    KS-->>T: "Reference material: # 库名 ..."（无命中/不可达则跳过）
    T->>MT: mountedTools(kbIds, sourceIndex)
    Note over MT: rag / read_source 仅在对应条件满足时挂载
```

## 4. 状态模型

### 4.1 题目交互（`InteractionStatus`）

```mermaid
stateDiagram-v2
    [*] --> registered: mastery_quiz 注册成功
    registered --> awaiting_input: 题卡下发
    awaiting_input --> answered: 学习者提交作答
    awaiting_input --> abandoned: 跳过 / 作废
    answered --> graded: 确定性判分产出结论
    answered --> awaiting_input: ungraded（零状态变更，保留题卡）
    graded --> [*]: 终态保留（幂等重放）
    abandoned --> [*]
```

### 4.2 知识点掌握与复习（派生状态）

```mermaid
stateDiagram-v2
    [*] --> new: 大纲创建
    new --> learning: 首次讲解 / 出题 / 作答
    learning --> mastered: 跨过门槛（量化 0.9 或定性通过）
    learning --> learning: 未跨门槛（含 ungraded）
    mastered --> mastered: 复习成功（间隔延长）
    mastered --> relearning: 复习失败（间隔重置为 10 分钟）
    relearning --> mastered: 重学后再次跨门槛
    mastered --> learning: 证据作废（坏题修正后重算）
    note right of relearning
        scheduledAfterFailure=true
        仅影响复习节奏，不改分数
    end note
```

### 4.3 路径与会话（导航侧）

```mermaid
stateDiagram-v2
    [*] --> ScratchPath: 无路径时开启会话（ownerSessionId）
    ScratchPath --> BoundPath: mastery_switch / 新建主题
    BoundPath --> ScratchPath: mastery_leave
    BoundPath --> BoundPath: 同一路径内切模式（outline/study/review）
    ScratchPath --> [*]: 会话删除且无目标（随会话清理）
    BoundPath --> [*]: 删除路径（级联会话/题卷/题库，保留事件）
```

## 5. 活动模型：大纲生成 → 缺口消解

```mermaid
flowchart TB
    A["学习者输入目标（可选：接资料 / 知识库）"] --> B{"已有非空地图？"}
    B -- 是 --> C["要求 confirm 并列出将丢失内容"]
    C -- 未确认 --> A
    C -- 已确认 --> D
    B -- 否 --> D["导师读来源摘录 + 画像 + 状态"]
    D --> E["mastery_build：模块 + KP（含类型与来源）"]
    E --> F{"引擎校验通过？"}
    F -- 否 --> G["返回可操作错误（名称/类型/重名/前置）"]
    G --> D
    F -- 是 --> H["写入地图 + 事件"]
    H --> I["计算覆盖报告 + 缺口（goal_gap / source_omission）"]
    I --> J{"存在缺口？"}
    J -- 否 --> K["进入学习模式（next_objective）"]
    J -- 是 --> L["学习者决策：接受缺口 / 补充来源 / 重新生成"]
    L -- 接受 --> M["标记 ungrounded（证据不带来源链接）"]
    M --> K
    L -- 补充来源 --> N["追加来源并重检覆盖"]
    N --> J
    L -- 重新生成 --> D
```

## 6. 用例 → 模型映射（便于逐条评审）

| 用例 | 主要参与组件 | 关键事件 / 端点 | 模型证据 |
| --- | --- | --- | --- |
| UC-02 生成大纲 | `TurnRunner`、`OutlineTools`、`OutlineBuilder`、`LearningService` | `tool_call: mastery_build` | §3.1、§5 |
| UC-03 编辑大纲 | `OutlineTools.Revise`、`LearningService` | `mastery_revise`（差分 ops，保留进度） | §1、ADR-02 |
| UC-05 缺口处理 | `LearningService`、`SourceGap` | `POST .../gaps/{id}/resolve` | §5 |
| UC-06 讲解与出题 | `AgentLoop`、`StudyTools.Quiz` | `question_card` + `turn_end(card)` | §4.1、[03](03-agentic-design.md) §5 |
| UC-07 作答判分 | `TurnRunner`（答前判分）、`Grading`、`MasteryScore` | `tool_result.mastery_grade` | §4.1、ADR-05 |
| UC-08 定性评估 | `RepairTools.Assess`、`MasteryPolicy` | `mastery_assess` | §3.2 |
| UC-09 声明掌握 | `LearningController`、`LearningService`、`Sm2Scheduler` | `POST .../override` | §3.4、ADR-08 |
| UC-10 复习到期 | `MasteryPolicy`、`Sm2Scheduler` | `GET .../review-queue` | §3.3、§4.2 |
| UC-12 配置提供方 | `SettingsController`、`ProviderProbe`、`SettingsStore` | `POST /settings/providers/probe` | [02](02-architecture.md) §3 |
| UC-13 切换模式 | `SessionController`、`SharedTools.Mode`、`ModeGate` | `PUT .../mode`、`session_meta` | §4.3、ADR-03 |
| UC-14/15 判定与排期 | `MasteryPolicy`、`Sm2Scheduler` | 内部纯函数 | §1.1、§4.2 |
| 生成型练习卷 | `QuizSetTurn`、`QuizSetGenerator`、`QuizSetService` | `quiz_plan / quiz_question / quiz_set` | ADR-15 |
