# Design: 简答题长度契约

## 1. 校验规则

判分器的既有事实（`Grading.gradeShort`）：归一化后 `expected` 或 `learner` 任一超过 30 字，
只做精确匹配。因此 30 字是「模糊匹配是否可能」的硬边界。

注册时增加一条与之一致的校验：

```
type == "short"
AND normalize(expected_answer).length > 30
AND 不存在 normalize(accepted_answers[i]).length <= 30
→ 拒绝
```

- 归一化复用 `Grading.normalize`（NFKC → 小写 → 折叠空白 → 剥首尾标点 → CJK 去空格），
  保证「判分多长」与「校验多长」是同一把尺子。
- 逃生舱：只要给出一条 ≤30 字的 `accepted_answers`，长 `expected_answer` 仍可注册——
  判分时会先比等价答案（`0043` 已恢复该规则）。
- `choice` / `open` 完全不受影响。

拒绝文案（面向模型）：

```
short answers are graded by exact/fuzzy match and an expected answer longer than 30
characters never matches; use question_type=open with required_keywords/optional_keywords,
or add an accepted_answers entry of 30 characters or fewer.
```

## 2. 工具描述

`mastery_quiz` 的描述追加：

```
Use question_type=short only when the expected answer is 30 characters or fewer;
for explanations or multi-line answers use open with keywords, or assess the
objective qualitatively with mastery_assess.
```

## 3. 兼容与风险

- 已在库里的长 `short` 题不受影响（校验只在注册期）。
- 模型若坚持出长 `short`，会收到 422 与明确的改法提示，可自行改用 `open`。
- 不做题干语义检测（例如「说明」「解释」字样）：靠长度这一确定性信号，避免误判。

## 4. 测试

- `StatusAndQuizToolsTest`：长 short 无短等价答案 → `ok=false` 且文案含 `question_type=open`；
  带 ≤30 字 `accepted_answers` → 注册成功；长 `open` → 注册成功。
- `StudyApiTest`：同一路径返回 422；带短等价答案返回 `status=registered`。
