# Settings And Task Model Capability Spec

## Purpose

定义设置中心的后端契约与 Task Model 的降级行为：提供方与语言模型/任务模型/嵌入模型的增删改查与连通性探测，以及「建议下一问」在无模型或模型报错时的确定性回退。

## Requirements

### Requirement: Settings Are List Plus Active

系统 SHALL 以「列表 + 默认项」的形式存储提供方与模型配置，MUST 支持按分组读取与整体写入，并 MUST 在配置缺失时返回可用默认值而不是报错。

#### Scenario: Read and write a settings section

- **GIVEN** 一个全新工作区
- **WHEN** 读取设置分组后再写入修改
- **THEN** 读取 MUST 返回结构完整的默认值，写入后 MUST 能原样读回

### Requirement: Provider Probe

系统 SHALL 提供提供方连通性探测与模型列表获取，探测失败 MUST 返回结构化错误而不是抛出未处理异常。

#### Scenario: Probe failure is structured

- **GIVEN** 一个不可达的提供方地址
- **WHEN** 执行探测
- **THEN** 系统 MUST 返回含错误信息的结构化结果

### Requirement: Suggest Question Falls Back

系统 SHALL 优先用任务模型生成「建议下一问」，当无可用模型或调用报错时 MUST 回退到确定性建议，MUST NOT 使接口失败。生成结果 MUST 作为草稿返回，MUST NOT 自动发送。

#### Scenario: Fallback without provider

- **GIVEN** 未配置任何提供方
- **WHEN** 请求建议下一问
- **THEN** 系统 MUST 返回基于当前目标的确定性草稿

#### Scenario: Fallback on provider error

- **GIVEN** 提供方调用抛错
- **WHEN** 请求建议下一问
- **THEN** 系统 MUST 回退到本地建议并返回成功
