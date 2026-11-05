# Tasks

## 1. 旧库 → Java

- [x] 1.1 复制 `../MasteryPath/data/learning.db` 到临时目录，起 Java 后端指向它
- [x] 1.2 对比结构事实：路径数/id 集合/version、模块与 KP 数、事件条数、会话数
      （44 次结构化比对 0 差异；并修掉 `listMessages` 的 2 处形状偏差）

## 2. Java → Python

- [x] 2.1 Java 在独立临时目录建路径 + 大纲 + 声明掌握 + 事件
- [x] 2.2 用 `../MasteryPath` 的 venv 读取，逐字段对照结构与语义

## 3. 前端联调

- [x] 3.1 `NEXT_PUBLIC_API_BASE=http://127.0.0.1:8080` 启动 Next.js（不改前端代码）
- [x] 3.2 浏览器验证：路径列表/详情、设置中心（含探测器）、知识库、一次真实回合

## 4. 证据与归档

- [x] 4.1 产出 `docs/verify/0024-acceptance.md`（G7 清单逐项勾选）
- [x] 4.2 回填 `docs/regression-log.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0024-acceptance.md`、`README.md`
- [x] 4.3 全量回归 → 归档 `0024-acceptance`
