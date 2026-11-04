# Knowledge Base Capability Spec

## Purpose

定义来源与知识库能力：本地文件夹扫描为来源、知识库的创建/上传/索引/检索、以及为回合提供参考材料。该能力只做检索与读取，MUST NOT 在检索时调用 LLM。

## Requirements

### Requirement: Local Folder As Sources

系统 SHALL 递归扫描本地文件夹，把每个可读文本文件转为一个来源（稳定 id、标签、摘要），并 MUST 支持以标签或唯一文件名引用来源。不可读或不可解析的文件 MUST 被跳过而不是使整个扫描失败。

#### Scenario: Attach folder then build

- **GIVEN** 一个包含多个 txt/md 的文件夹
- **WHEN** 附加为来源后执行 `build`
- **THEN** 每个文件 MUST 成为一个可引用的来源，且引用 MUST 解析到对应来源 id

### Requirement: Retrieval Without LLM

系统 SHALL 提供知识库检索接口，只做向量/关键词检索，MUST NOT 在检索路径中调用模型。检索结果 MUST 包含来源定位信息以便回读全文。

#### Scenario: Search returns located chunks

- **GIVEN** 已索引的知识库
- **WHEN** 执行检索
- **THEN** 结果 MUST 含文本片段与来源定位，且 MUST NOT 触发任何模型调用

### Requirement: Source Read Prefers Full Text

系统 SHALL 在读取来源时优先返回文件全文或知识库文档全文，仅在旧数据缺失全文时回退到摘要。

#### Scenario: Legacy source falls back to excerpt

- **GIVEN** 一个只存了摘要的历史来源
- **WHEN** 读取该来源
- **THEN** 系统 MUST 返回摘要内容且 MUST NOT 报错

### Requirement: Conditional Tool Mounting

系统 SHALL 仅在有附挂知识库时挂载检索工具，仅在有来源时挂载来源读取工具。

#### Scenario: No knowledge base means no rag tool

- **GIVEN** 路径未附挂任何知识库
- **WHEN** 装配工具 schema
- **THEN** 检索工具 MUST NOT 出现在模型中
