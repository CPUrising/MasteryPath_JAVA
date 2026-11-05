# Proposal: 验收

## Why

`0001`–`0023` 已全部实现并归档，全量 864 项测试通过。但「行为等价重写」的三维度里，
还有两项只在 fixture 层面被证明、未在真实环境验证：

1. **数据等价**：`ReferenceWorkspaceCompatibilityTest` 只证明「fixture 库可读」，
   没有证明「参考实现真实运行产生的库可读」，也没有证明**反向**（Java 建库 → Python 读）。
2. **协议等价**：REST/WS 契约由单测与 standalone `MockMvc` 覆盖，
   没有验证**真实 Next.js 前端**能否直接驱动 Java 后端。

这两项是 G7 的门槛（`docs/migration-plan.md` §4），必须产出真实执行证据，不能靠推断。

## What Changes

- 反向兼容性证据：用 `../MasteryPath` 的 venv 读取 Java 建出的库，逐字段对照
- 真实旧库读取：直接打开 `../MasteryPath/data/learning.db`（5 条路径 / 125 事件 / 8 会话），
  对比 `/learning/paths` 与若干路径的 state
- 前端联调：`NEXT_PUBLIC_API_BASE=http://127.0.0.1:8080` 启动现有 Next.js 前端，
  浏览器验证关键页面与一次真实回合
- 产出 `docs/verify/0024-acceptance.md`（G7 清单逐项勾选 + 证据）
- `README.md` 的 G7 置为**通过**

## Capabilities

### New Capabilities

无。本 change 不新增业务代码，只产出验收证据。

### Modified Capabilities

无。`skip_specs: true`。

## Impact

- 影响文件：
  - `docs/verify/0024-acceptance.md`（新增）
  - `docs/ai-collaboration/0024-acceptance.md`（新增）
  - `docs/regression-log.md`、`docs/verification-debt.md`、`README.md`
  - 不修改 `src/main/**` 与 `src/test/**`（若无缺陷）
- 明确不做：
  - 不改前端代码（只通过环境变量指到 Java 端口）
  - 不把参考实现的 `data/` 复制进本仓库的版本控制（含明文 Key 与真实学习数据）
