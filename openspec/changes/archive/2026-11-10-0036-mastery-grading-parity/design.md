# Design: 掌握式判分与掌握度算法对齐 DeepTutor

## 1. 判分（逐字复刻 `deeptutor/learning/grading.py`）

```python
user = user_answer.strip().lower()
expected = expected_answer.strip().lower()
if not expected: return False
if choice: return user.replace(" ", "") == expected.replace(" ", "")
if short:
    if user == expected: return True
    if len(expected) <= 30: return SequenceMatcher(None, user, expected).ratio() >= 0.85
    return False
if open:
    keywords = [k.strip() for k in re.split(r"[,;，；。\n]+", expected) if k.strip()]
    return sum(1 for kw in keywords if kw in user) / len(keywords) >= 0.6
return False
```

Java 侧新增 `MasteryGrading.isCorrect(userAnswer, expectedAnswer, questionType)`：

- `SequenceMatcher.ratio()` 用 `difflib` 的 matching-blocks 语义实现（`2*M/T`），
  不用 Levenshtein，避免相似度阈值漂移。
- 只返回布尔；调用方映射为 `correct` / `incorrect`。
- `MasteryGrading` 独立于 `Grading`（题卷继续使用后者）。

## 2. 掌握度（逐字复刻 `deeptutor/learning/mastery.py`）

```
最近 5 次，权重 (0.5,0.7,0.85,0.95,1.0)
score = Σ(w * correct) / Σw
cap = {1:0.5, 2:0.8}; score = min(score, cap.get(n, 1.0))
```

只统计 `independent && !voided` 的作答（DeepTutor `calculate_mastery` 的过滤在服务层）。
因此：

- 首次答对：分数 1.0 → 上限 0.5，**不能过 0.9 门槛**；
- 第二次答对：1.0 → 上限 0.8，仍不能过；
- 第三次答对：1.0 → 过门槛。

## 3. 服务层

`LearningService.grade`：

- 用 `MasteryGrading` 得到布尔结果；
- `status` 恒为 `graded`；
- 删除 `ungraded` 分支、`open correct→partial`、`hints>0→partial`、`hints>2→partial`；
- 仍记录 `hints_used` 与 `independent = hints == 0`；
- 幂等、单槽位、事件、题库写入保持不变（题库写入本 change 不删，后续批次按 DeepTutor 语义调整）。

## 4. 兼容与影响

- `Decision` 仍保留 `partial/ungraded` 常量供题卷使用，但掌握判分不再产生它们。
- 旧数据中已有的 `partial` 证据保留；新判分只写 `correct/incorrect`。
- 前端 `ungraded` 分支变为不可达，后续批次清理。
- 所有依赖「一次答对即掌握」的测试必须改为多次正确作答。

## 5. 测试

- 新增 `MasteryGradingTest`，用例逐条对应 DeepTutor `tests/test_grading.py`。
- 重写 `MasteryScoreTest`：近期加权、置信上限、voided/independent 过滤。
- 更新 `GradeFlowTest`：无法解析→incorrect、提示→correct 但 mastery 不涨、开放题→correct、
  三次正确才过门槛。
- 全量 `verify.ps1` 回归。
