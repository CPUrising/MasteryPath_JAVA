# Tasks

## 1. 路径概览与 scratch

- [x] 1.1 `listPathOverviews()`：9 键、跳过空 map、`name` 回退 `pathId`、按 `path_id` 升序
- [x] 1.2 `ensureScratchPath(sessionId)`：id 派生（`[^A-Za-z0-9_-]`→`_`、`strip('_')`、兜底 `default`）、owner 认领（不发事件）、建档发 `path.created{scratch:true}`

## 2. 会话绑定与删除

- [x] 2.1 `bindSessionPath(sessionId, pathId)`：空 session 返回 `null` 不写库
- [x] 2.2 `deleteSession(sessionId)`：删会话 + 三重条件（owner 匹配 + 无知识点 + 无其他会话）才删路径；session 不存在返回 `false`
- [x] 2.3 `listSessions(pathId)` / `getSession(sessionId)`：存储层转发

## 3. 测试

- [x] 3.1 `PathBindingTest`：概览列出已建路径 + 跳过空 scratch + `name` 回退 + 排序
- [x] 3.2 scratch：id 派生边界（`"!!!"`→`default`）、owner 认领不发事件、建档事件 payload
- [x] 3.3 绑定：空 session 短路、正常绑定改 `path_id`
- [x] 3.4 删除：删会话 + 删自有空 scratch；有知识点时不删路径；session 不存在返回 `false`

## 4. 验证

- [x] 4.1 `.\scripts\verify.ps1 -Scope unit -Change 0013-path-binding-scratch`（391 通过）
- [x] 4.2 全量 `.\scripts\verify.ps1 -Change 0013-path-binding-scratch`（478 通过）
- [x] 4.3 与参考实现对照（`pytest tests/test_path_binding.py -q` → 5 passed）
- [x] 4.4 回填 `docs/verify/0013-path-binding-scratch.md`、`docs/regression-log.md`、`docs/traceability.md`、`docs/verification-debt.md`、`docs/ai-collaboration/0013-path-binding-scratch.md`、`README.md`
