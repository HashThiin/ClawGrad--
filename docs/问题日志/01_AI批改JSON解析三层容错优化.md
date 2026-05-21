# 01_AI批改JSON解析三层容错优化

## 日期
2026-05-20

## 问题描述
AI 返回的批改结果 JSON 质量不稳定，导致批改结果页只显示总分（如 60/100），没有逐题评分和详细解析。具体问题包括：
1. 键名非法字符（如 `\ufeff` BOM 头、`\n` 等控制字符混入）
2. 嵌套同名字段（如 `knowledgePoints` 在 DTO 中未定义，触发 Jackson 的 `FAIL_ON_UNKNOWN_PROPERTIES`）
3. JSON 外层结构损坏（如数组缺少关闭标记 `]`，导致 Jackson 树模型 `readTree()` 也失败）
4. AI 返回的 JSON 被 markdown 代码块包裹或混入多余文字

## 修复方案

### 白话方案
AI 返回的 JSON 质量不可控。不能只靠一种解析方式，需要做三层保险：
- **第一层**：标准 Jackson 反序列化（AI 返回正常 JSON 时能用）
- **第二层**：Jackson 树模型 + 正则提取（第一层失败时，用树模型提取总分和逐题数据）
- **第三层**：纯正则大括号深度匹配（前两层都失败，就从原始文本里硬抠出每个题目的数据）

### 代码解释

**第三层兜底：大括号深度匹配提取逐题数据**

```java
// FeedbackStage.java - extractItemsViaRegex()
// 思路：定位 "items":[ 区域，逐字符扫描，统计大括号深度
// braceDepth=0 且遇到 '{' → 开始记录一个题目对象
// braceDepth 归零时 → 已找到一个完整对象，提取各字段
private List<ItemGradingResult> extractItemsViaRegex(String json) {
    // 1. 用正则定位 "items":[ 的开始位置
    Matcher regionMatcher = Pattern.compile("\"items\"\\s*:\\s*\\[(.*?)(?:\\]|$)", Pattern.DOTALL).matcher(json);
    // 2. 逐字符扫描，braceDepth 统计嵌套深度
    int braceDepth = 0;
    int objStart = -1;
    for (int i = 0; i < region.length(); i++) {
        char c = region.charAt(i);
        if (c == '{') {
            if (braceDepth == 0) objStart = i;  // 开始一个新对象
            braceDepth++;
        } else if (c == '}') {
            braceDepth--;
            if (braceDepth == 0 && objStart >= 0) {
                // 一个完整对象提取完成 → 解析各字段
                String objStr = region.substring(objStart, i + 1);
                items.add(parseItemObject(objStr));  // 正则提取 index/score/feedback...
            }
        }
    }
}
```

**各题字段提取辅助方法**

```java
// parseItemObject()：从子串中用正则提取各字段
private ItemGradingResult parseItemObject(String objStr) {
    item.setIndex(extractIntField(objStr, "index", 0));
    item.setScore(extractDoubleField(objStr, "score", 0.0));
    item.setFeedback(extractStringField(objStr, "feedback", ""));
    // ...
}

// extractIntField()：整数正则提取
private Integer extractIntField(String json, String fieldName, int defaultValue) {
    Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)");
    Matcher m = pattern.matcher(json);
    if (m.find()) return Integer.parseInt(m.group(1));
    return defaultValue;
}
```

### 涉及修改的文件、函数

| 文件 | 函数 | 修改内容 |
|------|------|---------|
| `FeedbackStage.java` | `extractItemsArray()` | catch 块由只 log 改为调用 regex 兜底 |
| `FeedbackStage.java` | `extractItemsViaRegex()` | **新增**：大括号深度匹配提取 items |
| `FeedbackStage.java` | `parseItemObject()` | **新增**：从子串提取各题目字段 |
| `FeedbackStage.java` | `extractIntField()` | **新增**：整数正则提取辅助 |
| `FeedbackStage.java` | Jackson config | 增加 `@JsonAlias` 和 `FAIL_ON_UNKNOWN_PROPERTIES` 配置 |