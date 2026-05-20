# 🦞 ClawGrad — 把 AI 批改装进流水线

> **一份作业，四步到位。** 上传 → 识别 → 批改 → 反馈，每一步都是一个独立的 AI 技能，像搭积木一样自由组合。

<p align="center">
  <img src="https://img.shields.io/badge/Java-Spring%20Boot%203.2-6DB33F?logo=spring" alt="Spring Boot 3.2"/>
  <img src="https://img.shields.io/badge/React-Vite-61DAFB?logo=react" alt="React"/>
  <img src="https://img.shields.io/badge/OpenClaw-Gateway-blue" alt="OpenClaw"/>
  <img src="https://img.shields.io/badge/Pipeline-Pattern-orange" alt="Pipeline Pattern"/>
  <img src="https://img.shields.io/badge/MultiModal-OCR%20%7C%20Grading-purple" alt="MultiModal"/>
</p>

---

## 📖 项目简介

ClawGrad 是一个基于 **OpenClaw Gateway** 的 AI 智能作业批改平台，支持：

| 维度 | 能力 |
|------|------|
| 🎯 **科目覆盖** | 数学 · 语文 · 英语 · 物理 · 化学 · 编程 · 任意可文字表达的学科 |
| 📸 **输入方式** | 纯文本提交 · 拍照上传（多模态 OCR 识别） |
| 🔀 **模型切换** | GLM · Qwen · GPT-4o · Claude — 配置文件改一行即生效 |
| 🔄 **架构模式** | 流水线模块化（Pipeline Pattern），四阶段独立可替换 |

**一句话总结**：用户上传图片或文本 → 流水线自动编排四个 AI 技能 → 返回结构化的逐题评分 + 错误分析 + 知识点诊断。

---

## 🏗️ 系统架构

### 全景图

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              全 景 架 构                                      │
│                                                                              │
│   ┌─────────────┐   POST JSON / Multipart    ┌─────────────────────────────┐  │
│   │  Frontend   │ ──────────────────────────▶ │       Java Backend          │  │
│   │  React 18   │                               │  Spring Boot 3.2 (WebFlux) │  │
│   │  + Ant Design│ ◀────── poll progress ────── │                             │  │
│   │  + Vite     │                               │  ┌─ GradingController       │  │
│   └─────────────┘                               │  │    ↓                     │  │
│                                                  │  │  GradingPipeline        │  │
│                                                  │  │    ├─ UploadStage       │  │
│                                                  │  │    ├─ OrganizeStage     │  │
│                                                  │  │    ├─ GradingStageImpl  │  │
│                                                  │  │    └─ FeedbackStage     │  │
│                                                  │  │                          │  │
│                                                  │  └─ OpenClawClientService ──┼──▶
│                                                  └─────────────────────────────┘  │
│                                                                                  │
│   ┌──────────────┐  ┌───────────┐  ┌────────────┐  ┌────────────────────────┐   │
│   │  MySQL 8.0   │  │ Redis 7   │  │ RabbitMQ 3 │  │  OpenClaw Gateway      │   │
│   │  数据存储     │  │ 状态缓存   │  │ 异步分发    │  │  → GLM / Qwen / GPT /… │   │
│   └──────────────┘  └───────────┘  └────────────┘  └────────────────────────┘   │
│                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 技术栈一览

```
前端层    React 18 + TypeScript + Vite + Ant Design 5.x
         ─────────────────────────────────────────────
网关层    GradingController (REST) + GradingTaskStore (任务状态)
         ─────────────────────────────────────────────
流水线    UploadStage → OrganizeStage → GradingStage → FeedbackStage
         ─────────────────────────────────────────────
AI 接入   OpenClawClientService (OpenAI 兼容 /v1/chat/completions)
         ─────────────────────────────────────────────
数据层    MySQL 8.0 + Redis 7 + RabbitMQ 3
```

---

## 🔄 流水线深度解析

### 总览：数据流与上下文传递

整个流水线围绕 **`GradingContext`** 展开——一个贯穿四阶段的状态容器，每一阶段读取上游产物、写入下游所需：

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│                           GradingContext 数据流                                       │
│                                                                                      │
│  ┌──────────┐       ┌──────────────┐      ┌─────────────┐      ┌──────────────┐      │
│  │  原始请求  │  ──▶  │  结构化作业   │  ──▶ │  AI 批改结果  │  ──▶  │  最终反馈     │      │
│  │          │       │              │       │  (纯文本)    │       │  (JSON对象)  │      │
│  │ request  │       │ organizedHW  │       │ aiRawText   │       │ result       │      │
│  └──────────┘       └──────────────┘       └─────────────┘       └──────────────┘      │
│       ▲                      ▲                       ▲                      ▲          │
│       │                      │                       │                      │          │
│  ┌────┴────┐           ┌────┴─────┐           ┌────┴─────┐           ┌────┴─────┐     │
│  │Upload   │           │Organize  │           │Grading   │           │Feedback  │     │
│  │Stage    │           │Stage     │           │StageImpl │           │Stage     │     │
│  │order=10 │           │order=20  │           │order=30  │           │order=40  │     │
│  └─────────┘           └──────────┘           └──────────┘           └──────────┘     │
│                                                                                      │
│  贯穿始终的上下文字段：                                                                │
│    taskId · modelId · multimodal · images[] · systemPrompt · userPrompt               │
│    stageTimings Map<String, Long> · error                                             │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 阶段一：UploadStage — 文件接收 & 初始化

```
order = 10  │  Skill: 接收
```

| 维度 | 详情 |
|------|------|
| **文件** | `pipeline/stage/UploadStage.java` |
| **输入** | `HttpServletRequest`（JSON body 或 `multipart/form-data`） |
| **输出** | `GradingContext`（taskId、request、images 已填充） |
| **AI 调用** | 无 |

**核心流程：**

```
Controller 接收请求
    │
    ├─ JSON 提交 ──▶ 解析 AssignmentGradingRequest
    │                   ├─ subject / question / answer / maxScore
    │                   └─ modelId（可选，由 OrganizeStage 最终解析）
    │
    └─ Multipart 提交 ──▶ 保存文件到 storage/{taskId}/
                          ├─ 读取文件为 base64
                          ├─ 构造 ImageAttachment(filename, mimeType, base64)
                          └─ 标记 multimodal = true
    │
    ▼
创建 GradingContext → 写入 TaskStore（状态: PENDING）
```

**关键代码片段：**

```java
// Controller 中调用 UploadStage
GradingContext ctx = uploadStage.execute(request, images);
taskStore.createTask(ctx.getTaskId());
pipeline.runAsync(ctx);  // @Async 异步执行，不阻塞 HTTP 线程
```

---

### 阶段二：OrganizeStage — AI 识别 & 结构化

```
order = 20  │  Skill: 识别
```

| 维度 | 详情 |
|------|------|
| **文件** | `pipeline/stage/OrganizeStage.java` |
| **输入** | `GradingContext.request` + `GradingContext.images[]` |
| **输出** | `GradingContext.organizedHomework`（`OrganizedHomework` 对象） |
| **AI 调用** | ✅ 多模态时调用 `OpenClawClientService.chatCompletionParts()` |

**双路径处理：**

```
                    OrganizeStage.execute(ctx)
                            │
              ┌─────────────┴─────────────┐
              ▼                           ▼
     ┌─────────────────┐        ┌────────────────────┐
     │   纯文本路径     │        │    多模态路径       │
     │  organizeLocal() │        │  organizeByAi()    │
     └────────┬────────┘        └────────┬───────────┘
              │                          │
              │  本地打包                 │  1. modelCatalog.resolve() 选择模型
              │  question → items[1]     │  2. 视觉能力降级判断
              │                          │  3. 组装 content parts:
              │                          │     ├─ text (system + user prompt)
              │                          │     └─ image_url[] (base64 data URLs)
              │                          │  4. client.chatCompletionParts()
              │                          │  5. parseOrganizedJson(raw)
              │                          │     ├─ 清除 markdown 代码块
              │                          │     ├─ 正则提取 JSON 块
              │                          │     └─ ObjectMapper 反序列化
              │                          │  6. normalizeItemMaxScores() 均摊满分
              │                          │  7. 题号兜底修正
              │                          │
              ▼                          ▼
          organizedHomework ←──────── organizedHomework
```

**AI Prompt 设计（识别阶段）：**

```java
// System Prompt
"你是作业图片识别助手。任务：识别图片中所有题目及学生答题内容，按顺序拆分。"
+ "只输出 JSON，不要 markdown 代码块。"
+ "格式: {subject, items:[{index, question, answer, maxScore}], remark}"

// User Prompt
"请从图片中识别所有题目与答案。"
+ (req.getMaxScore() != null ? "【总满分】" + req.getMaxScore() : "")
+ "【输出格式】{subject, items:[...], remark}"
```

**数据产物 — `OrganizedHomework`：**

```java
OrganizedHomework {
    String subject;           // "数学" / "语文" / "英语" / ...
    List<HomeworkItem> items; // 题目清单
    boolean fromImage;        // 是否来自图片识别
    String remark;            // 识别备注
    Double totalMaxScore;     // 总满分
}

HomeworkItem {
    Integer index;            // 题号
    String question;          // 题目原文
    String answer;            // 学生答案
    Double maxScore;          // 单题满分
}
```

---

### 阶段三：GradingStageImpl — AI 批改

```
order = 30  │  Skill: 批改
```

| 维度 | 详情 |
|------|------|
| **文件** | `pipeline/stage/GradingStageImpl.java` |
| **输入** | `GradingContext.organizedHomework` + `GradingContext.modelId` |
| **输出** | `GradingContext.aiRawText`（AI 返回的纯文本 JSON） |
| **AI 调用** | ✅ 调用 `OpenClawClientService.chatCompletion(model, systemPrompt, userPrompt)` |

**核心流程：**

```
GradingStageImpl.execute(ctx)
    │
    ├─ buildGradingSystemPrompt()
    │     "你是一位资深教育专家AI批改助手..."
    │     + "逐题评分 + 综合分析"
    │     + "严格 JSON 格式输出"
    │
    ├─ buildGradingUserPrompt(hw)
    │     组装题目清单：
    │     ┌─────────────────────────────────────────────┐
    │     │ 【学科】数学                                  │
    │     │ 【题目总数】5 道                              │
    │     │ 【作业总满分】100 分                           │
    │     │                                             │
    │     │ 【题目清单】                                 │
    │     │ --- 第 1 题 (满分 20 分) ---                  │
    │     │ 题目：求 f(x) = x² + 2x 的导数               │
    │     │ 学生答案：f'(x) = 2x + 2                     │
    │     │                                             │
    │     │ --- 第 2 题 (满分 20 分) ---                  │
    │     │ ...                                         │
    │     │                                             │
    │     │ 【批改要求】                                 │
    │     │ 1. 逐题评分 (score/feedback/correctness/errors) │
    │     │ 2. 维度评分 (dimensionScores)                │
    │     │ 3. 知识点掌握 (knowledgePoints)              │
    │     │ 4. 改进建议 (suggestions)                    │
    │     │                                             │
    │     │ 【输出格式 - 严格 JSON】                      │
    │     └─────────────────────────────────────────────┘
    │
    ├─ client.chatCompletion(modelId, systemPrompt, userPrompt)
    │     POST /v1/chat/completions
    │     Authorization: Bearer {token}
    │     body: { model, messages: [{role: system}, {role: user}] }
    │
    └─ ctx.setAiRawText(aiText)
```

**AI 输出 JSON Schema：**

```json
{
  "totalScore": 85,
  "maxScore": 100,
  "dimensionScores": { "准确性": 82, "规范性": 90, "逻辑性": 83 },
  "feedback": "整体评语，100字以内",
  "overallSummary": "综合分析，200字以内",
  "items": [{
    "index": 1,
    "score": 8,
    "maxScore": 10,
    "feedback": "单题评语",
    "correctness": "correct | partial | wrong",
    "errors": [{ "location": "第2步", "errorType": "步骤缺失", "description": "...", "correction": "..." }]
  }],
  "errors": [{ "location": "...", "errorType": "...", "description": "...", "correction": "..." }],
  "suggestions": ["建议1", "建议2"],
  "knowledgePoints": [{ "name": "导数运算", "masteryLevel": "mastered | partial | weak", "description": "..." }],
  "reasoningSteps": ["分析步骤1", "分析步骤2"]
}
```

---

### 阶段四：FeedbackStage — 解析 & 存储

```
order = 40  │  Skill: 反馈
```

| 维度 | 详情 |
|------|------|
| **文件** | `pipeline/stage/FeedbackStage.java` |
| **输入** | `GradingContext.aiRawText` + `GradingContext.organizedHomework` |
| **输出** | `GradingContext.result`（`AssignmentGradingResult` 对象）+ TaskStore 更新为 COMPLETED |
| **AI 调用** | 无 |

**核心流程：**

```
FeedbackStage.execute(ctx)
    │
    ├─ extractJson(aiRawText)
    │     ├─ 清除 markdown 代码块 (```json / ```)
    │     ├─ 正则匹配第一个 { ... } JSON 块
    │     └─ cleanJsonKeys() 清理键名中的非法空白字符
    │
    ├─ objectMapper.readValue(json, AssignmentGradingResult.class)
    │
    ├─ 数据回填 & 兜底
    │     ├─ 从 organizedHomework 回填题目原文与学生答案
    │     ├─ 补充 maxScore（AI 未返回时从源数据获取）
    │     └─ 计算 totalScore（从 items 逐项累加）
    │
    └─ taskStore.completeTask(taskId, result)
          更新数据库 → 前端轮询可获取最终结果
```

**关键方法对照表：**

| 方法 | 作用 |
|------|------|
| `extractJson(text)` | 从 AI 返回文本中清理并提取 JSON 块 |
| `cleanJsonKeys(json)` | 修复 AI 可能生成的不规范键名（如 `"total Score"` → `"totalScore"`） |
| `objectMapper.readValue()` | Jackson 反序列化为 `AssignmentGradingResult` |
| `taskStore.completeTask()` | 写入 MySQL + 更新 Redis 缓存 |

---

### 四阶段完整对照表

| # | 阶段 | 文件 | order | AI 调用 | 核心方法 | 产物 |
|---|------|------|-------|---------|---------|------|
| 1 | **Upload** | `UploadStage.java` | 10 | ❌ | `execute(request, images)` | `ctx.request` · `ctx.images[]` |
| 2 | **Organize** | `OrganizeStage.java` | 20 | ✅ 多模态 | `organizeLocal()` / `organizeByAi()` | `ctx.organizedHomework` |
| 3 | **Grading** | `GradingStageImpl.java` | 30 | ✅ | `buildGradingSystemPrompt()` / `buildGradingUserPrompt()` | `ctx.aiRawText` |
| 4 | **Feedback** | `FeedbackStage.java` | 40 | ❌ | `extractJson()` / `cleanJsonKeys()` | `ctx.result` |

**技能映射关系：**

```
┌────────────────────────────────────────────────────────────────┐
│              Stage → OpenClaw Skill 映射                        │
├────────────┬───────────────┬────────────────────────────────────┤
│   Stage    │   Skill 角色   │         可替换 / 增强方向           │
├────────────┼───────────────┼────────────────────────────────────┤
│ Upload     │   接收         │   支持 PDF / Word 等多格式解析      │
│ Organize   │   识别         │   接入专用 OCR 服务 · 公式识别引擎   │
│ Grading    │   批改         │   RAG 增强 · 多维度评分模型          │
│ Feedback   │   反馈         │   学情分析 · 推送通知 · 报告生成     │
└────────────┴───────────────┴────────────────────────────────────┘
```

---

## 🤖 OpenClaw Skills 理念实践

ClawGrad 的流水线本质上是对 OpenClaw **Skills 能力**的一次完整落地：

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Pipeline Pattern = Skills 编排                    │
│                                                                     │
│   每个 Stage 都实现同一个接口：                                       │
│                                                                     │
│       interface GradingStage {                                      │
│           String name();          // 技能名                          │
│           int order();            // 执行顺序                        │
│           void execute(GradingContext ctx);  // 技能逻辑             │
│       }                                                             │
│                                                                     │
│   ★ 新增一个 Skill = 新增一个 @Component 实现                         │
│   ★ 替换一个 Skill = 替换 order 值                                    │
│   ★ 监控一个 Skill = stageTimings 精确到毫秒                          │
│                                                                     │
│   这就是 Skills 的精髓：独立培养、自由组合、即插即用。                   │
└─────────────────────────────────────────────────────────────────────┘
```

**代码示例 — 新增一个查重 Skill：**

```java
@Component
public class PlagiarismStage implements GradingStage {
    @Override public String name() { return "plagiarism"; }
    @Override public int order() { return 35; } // 排在 Grading(30) 之后

    @Override
    public void execute(GradingContext ctx) {
        OrganizedHomework hw = ctx.getOrganizedHomework();
        // 检索历史作业比对...
    }
}
```

**不需要修改已有代码，不需要改配置，Spring 自动装配，流水线自动编排。**

---

## 🔌 OpenClaw Gateway 集成

### 调用链路

```
ClawGrad Backend                    OpenClaw Gateway                    大模型供应商
      │                                   │                                  │
      │── POST /v1/chat/completions ──────▶│                                  │
      │   { model, messages }              │── 路由/转发 ────────────────────▶│
      │   Authorization: Bearer {token}    │   (按 model 参数选择目标)         │
      │   (不传 x-openclaw-agent-id)       │◀── 返回响应 ─────────────────────│
      │◀── { choices: [{ message }] } ────│                                  │
```

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 认证方式 | `Authorization: Bearer {token}` | OpenClaw 推荐的标准方式 |
| Agent ID 头 | **不传** `x-openclaw-agent-id` | 避免模型锁定 |
| 响应体上限 | **8 MB** | 支持长作业批改 |
| 多模态格式 | OpenAI 兼容 `content[]` array | text + image_url 原生支持 |
| 超时配置 | 由 `OpenClawGatewayProperties.timeout` 控制 | 灵活适配不同模型响应速度 |

---

## ✨ 核心特性

### 🎯 全科目通用

- 无需为每个科目开发独立模块
- AI 自动识别学科（数学/语文/英语/物理/化学/编程...）
- 统一 Prompt 模板，结构化 JSON 输出

### 📸 多模态图片批改

- 拍照上传 → OCR 识别 → 自动结构化题目清单
- 手写体辨识 · 公式/图表理解
- 视觉能力降级：所选模型不支持 vision 时自动退回纯文本

### 🔀 多模型可切换

```yaml
# 配置文件维护模型清单，前端下拉选择
grading:
  models:
    - id: "bailian-token-plan/qwen3.6-plus"
      name: "Qwen 3.6 Plus"
      supportsVision: true
    - id: "bailian-token-plan/glm-5"
      name: "GLM-5"
      supportsVision: false
```

### ⚡ 异步任务 + 实时轮询

```
请求 ──▶ Controller ──▶ 创建任务 ──▶ 返回 taskId (~100ms)
                              │
                              ▼
                        @Async 异步执行
                        Upload → Organize → Grading → Feedback
```

- 提交不阻塞，前端轮询 `GET /grading/ai-tasks/{taskId}` 获取进度
- 每阶段独立计时（`stageTimings` Map）
- 任意阶段失败立即终止，错误信息写入 TaskStore

### 📊 结构化输出

AI 返回严格遵循 JSON Schema 的批改结果，包含：
- **逐题评分** — 每题独立 score / feedback / correctness / errors
- **维度分析** — 多维度能力评分（准确性/规范性/逻辑性等）
- **知识点诊断** — 各知识点掌握程度（mastered/partial/weak）
- **改进建议** — 针对性学习建议

---

## 🚀 快速开始

### 前置条件

- Docker & Docker Compose
- 一个可用的 OpenClaw Gateway 实例

### 三步启动

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env，填入 OpenClaw Gateway 地址和 Token

# 2. 一键启动所有服务
docker compose up -d
# → MySQL + Redis + RabbitMQ + 后端

# 3. 启动前端
cd frontend && npm install && npm run dev
# → http://localhost:5173
```

后端默认监听 `http://localhost:8080/api/v1`

---

## 🔌 API 速览

### 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/grading/models` | 获取可用模型列表（含视觉能力标记） |
| `POST` | `/api/v1/grading/ai-grade` | 纯文本提交批改 |
| `POST` | `/api/v1/grading/ai-grade-multipart` | 文本 + 图片提交批改 |
| `GET` | `/api/v1/grading/ai-tasks/{taskId}` | 轮询进度 & 获取结果 |
| `POST` | `/api/v1/grading/code` | 编程作业同步批改 |

### 30 秒体验

```bash
# 提交一份数学作业
curl -X POST http://localhost:8080/api/v1/grading/ai-grade \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "数学",
    "modelId": "bailian-token-plan/qwen3.6-plus",
    "items": [{
      "index": 1,
      "question": "求 f(x) = x² + 2x 的导数",
      "answer": "f'\''(x) = 2x + 2",
      "maxScore": 10
    }]
  }'

# 返回 taskId，轮询结果
curl http://localhost:8080/api/v1/grading/ai-tasks/{taskId}
```

---

## 📁 项目结构

```
ClawGrad--/
├── docker-compose.yml          # MySQL + Redis + RabbitMQ + 后端，一键编排
├── .env.example                # 环境变量模板
│
├── frontend/                   # 🎨 React 18 + Vite + Ant Design
│   └── src/
│       ├── pages/
│       │   ├── HomePage.tsx    # 首页 — 模型选择 & 功能导航
│       │   ├── GradePage.tsx   # 批改页 — 上传 & 实时进度
│       │   └── ResultPage.tsx  # 结果页 — 结构化展示 & 分析
│       └── services/api.ts     # API 调用封装
│
├── java-backend/               # ⚙️ Spring Boot 3.2 + WebFlux
│   └── src/main/java/.../grading/
│       ├── OpenClawGradingApplication.java
│       ├── controller/         # REST 入口
│       ├── service/
│       │   ├── OpenClawClientService.java   # ★ OpenClaw Gateway 客户端
│       │   ├── ModelCatalogService.java     #   模型目录服务
│       │   └── GradingTaskStore.java        #   任务状态管理
│       ├── pipeline/           # ★ 流水线核心
│       │   ├── GradingPipeline.java         #   编排器
│       │   ├── GradingContext.java          #   上下文
│       │   ├── GradingStage.java            #   接口定义
│       │   └── stage/
│       │       ├── UploadStage.java         #   阶段 1: 接收
│       │       ├── OrganizeStage.java       #   阶段 2: 识别
│       │       ├── GradingStageImpl.java    #   阶段 3: 批改
│       │       └── FeedbackStage.java       #   阶段 4: 反馈
│       ├── model/
│       │   └── dto/            #   DTOs (Request/Result/HomeworkItem...)
│       └── config/             #   配置类
│
└── storage/                    # 📂 上传图片落盘（按 taskId 分目录）
```

---

## 💡 设计决策记录

| 决策 | 选择 | 理由 |
|------|------|------|
| Pipeline vs Service | **Pipeline Pattern** | 解耦、可测试、可独立扩展 |
| 同步 vs 异步 | **异步 + 轮询** | 批改耗时 2-10s，不能阻塞 HTTP 线程 |
| 直连模型 vs Gateway | **OpenClaw Gateway** | 统一接口、模型切换零成本 |
| 自由文本 vs JSON | **严格 JSON Schema** | 前端可直接渲染，无需二次解析 |
| 不传 agent-id | **只传 Bearer Token** | 避免 OpenClaw 模型锁定 |

---

## 📜 许可证

仅用于教学与课程实践。

---

> **ClawGrad 想证明一件事：好的 AI 应用不需要"大而全"，而是把每个小技能做好，然后让它们一起工作。** 🦞
