# JSON 解析容错机制

## 概述

本目录下的 Stage 组件实现了**双层容错 JSON 解析机制**，确保 AI 返回的 JSON 即使存在格式问题或部分字段缺失，系统仍能正常提取关键数据并完成批改流程。

## 问题背景

AI 模型返回的 JSON 常存在以下问题：
- 字符串值中包含未转义的控制字符（换行符 `\n`、制表符 `\t` 等）
- 缺少某些非关键字段
- 包含未知的额外字段
- JSON 键名中包含空格或特殊字符

这些情况会导致 Jackson 解析失败，整个批改任务中断。

## 容错架构

### 第一层：Jackson 容错配置

```java
objectMapper.reader()
    .with(FAIL_ON_UNKNOWN_PROPERTIES)      // 忽略未知字段
    .without(FAIL_ON_IGNORED_PROPERTIES)   // 不要求所有字段都存在
    .readValue(json, ResultClass.class)
```

**适用场景**：JSON 格式基本正确，但字段不完全匹配 DTO 定义。

### 第二层：正则表达式兜底解析

当 Jackson 解析完全失败时，自动降级为手动提取关键字段：

#### OrganizeStage（作业整理阶段）

提取关键字段：
- `subject`：学科（默认："未知"）
- `items[]`：题目列表
  - `question`：题目原文
  - `answer`：学生答案
  - `maxScore`：单题满分
- `maxScore`：总满分（默认：100.0）

#### FeedbackStage（批改反馈阶段）

提取关键字段：
- `totalScore`：总分（缺失时从 items 自动累加）
- `maxScore`：满分
- `feedback`：总体评语
- `overallSummary`：综合分析
- `items[]`：逐题批改结果
  - `score`：得分
  - `maxScore`：满分
  - `feedback`：单题评语
  - `correctness`：正确性（correct/partial/wrong）

## 控制字符处理

在 JSON 解析前，自动清洗字符串值中的非法控制字符：

```java
escapeControlCharsInJsonStringValues(json)
```

**处理规则**：
- `\n`（换行符，ASCII 10）→ 转义为 `\\n`
- `\r`（回车符，ASCII 13）→ 转义为 `\\r`
- `\t`（制表符，ASCII 9）→ 转义为 `\\t`
- 其他控制字符（ASCII < 32）→ 转义为 `\\u00XX`

**关键实现**：
- 只处理双引号内的字符串值
- 不破坏 JSON 结构（键名、分隔符等保持不变）
- 正确处理已转义的字符（如 `\\n` 不再次转义）

## 超时配置

为配合复杂批改任务，超时时间已调整：

```yaml
# application.yml
openclaw:
  gateway:
    timeout: 180000  # 从 60 秒增加到 180 秒（3 分钟）
```

**原因**：多题批改（4 题以上）+ 复杂题目（编程、数学推导）需要更长的 AI 响应时间。

## 日志监控

容错解析时会记录警告日志：

```
WARN  o.g.p.stage.OrganizeStage - Organize JSON parse error, attempting fallback: ...
INFO  o.g.p.stage.OrganizeStage - Using fallback parser for OrganizedHomework
```

可通过日志判断是否触发了兜底解析。

## 测试验证

运行测试脚本验证容错功能：

```powershell
# 简单题批改
question: "What is 2+2?"
answer: "4"
结果: ✓ Score: 10.0/10.0

# 复杂多题（含换行符）
question: "Q1: Explain OOP\nQ2: What is inheritance?"
answer: "A1: Object-oriented programming\nA2: Inheritance allows..."
结果: ✓ Score: 35.0/100.0, Items: 3
```

## 相关文件

- `OrganizeStage.java` - 作业整理阶段（含容错解析）
- `FeedbackStage.java` - 批改反馈阶段（含容错解析）
- `application.yml` - 超时配置（180 秒）

## 维护建议

1. **优先完善 system prompt**：在 prompt 中明确要求 AI 输出标准 JSON，减少触发兜底解析的概率
2. **监控日志**：定期检查 `Using fallback parser` 日志频率，分析常见解析失败原因
3. **扩展兜底逻辑**：如发现新的 JSON 格式问题，可在 fallback 方法中添加额外提取规则
4. **保持向后兼容**：兜底解析返回的对象结构与标准解析一致，不影响下游组件
