# ClawGrad 智能作业批改系统 (Java 后端)

> 项目级总览请见仓库根目录的 [README.md](../README.md)。本文聚焦后端模块的细节、API 与 OpenClaw Gateway 集成。

基于 Spring Boot 3.x 的高性能智能作业批改系统，集成 OpenClaw Gateway 实现多渠道作业提交和批改结果推送。

## 🚀 技术栈

- **后端框架**: Spring Boot 3.2.0
- **Java 版本**: 17+
- **数据库**: MySQL 8.0
- **缓存**: Redis 7.4
- **消息队列**: RabbitMQ
- **HTTP 客户端**: Spring WebFlux (异步非阻塞)
- **构建工具**: Maven

## 📦 项目结构

```
java-backend/
├── src/main/java/com/openclaw/grading/
│   ├── OpenClawGradingApplication.java    # 主应用入口
│   ├── config/                             # 配置类
│   │   └── OpenClawGatewayProperties.java  # OpenClaw Gateway 配置
│   ├── controller/                         # REST 控制器
│   │   ├── GradingController.java          # 作业批改 API
│   │   └── OpenClawWebhookController.java  # Webhook 接收端点
│   ├── model/dto/                          # 数据传输对象
│   │   ├── CodeGradingRequest.java         # 编程作业批改请求
│   │   ├── CodeGradingResult.java          # 编程作业批改结果
│   │   ├── TutorRequest.java               # 答疑请求
│   │   └── TutorResponse.java              # 答疑响应
│   └── service/                            # 业务服务
│       ├── OpenClawClientService.java      # OpenClaw Gateway 客户端
│       └── CodeGradingService.java         # 编程作业批改服务
├── src/main/resources/
│   └── application.yml                     # 应用配置
└── pom.xml                                 # Maven 依赖
```

## 🔧 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.8+
- MySQL 8.0
- Redis 7.4
- RabbitMQ 3.12+
- OpenClaw Gateway (已部署)

### 2. 配置 OpenClaw Gateway

编辑 `src/main/resources/application.yml`:

```yaml
openclaw:
  gateway:
    url: http://你的OpenClaw服务器IP:18789
    token: 你的OpenClawGatewayToken
    timeout: 30000
    enabled: true
```

### 3. 配置数据库

```yaml
spring:
  datasource:
    url: jdbc:mysql://你的MySQL服务器IP:3306/openclaw_grading?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: 你的数据库用户名
    password: 你的数据库密码
```

### 4. 构建项目

```bash
cd java-backend
mvn clean package -DskipTests
```

### 5. 运行应用

```bash
java -jar target/openclaw-intelligent-grading-1.0.0.jar
```

或使用 Maven:

```bash
mvn spring-boot:run
```

## 📡 API 接口

### 1. 编程作业批改

**同步批改**
```bash
POST /api/v1/grading/code
Content-Type: application/json

{
  "language": "java",
  "requirement": "实现一个快速排序算法",
  "studentCode": "public class QuickSort { ... }",
  "testCases": [
    {
      "input": "[3,1,2]",
      "expectedOutput": "[1,2,3]",
      "description": "基本排序测试"
    }
  ],
  "rubric": "- 正确性 40%\n- 代码规范 30%\n- 算法效率 30%"
}
```

**异步批改（推荐大批量）**
```bash
POST /api/v1/grading/code/async

响应:
{
  "taskId": "uuid",
  "status": "processing",
  "queryUrl": "/api/v1/grading/tasks/uuid"
}
```

**查询任务结果**
```bash
GET /api/v1/grading/tasks/{taskId}
```

### 2. OpenClaw Webhook

**接收 OpenClaw 消息**
```bash
POST /api/v1/webhook/openclaw
Content-Type: application/json

{
  "sender": {
    "id": "telegram:123456789",
    "name": "张三",
    "channel": "telegram"
  },
  "message": {
    "type": "text",
    "content": "请批改：用 Python 实现快速排序",
    "timestamp": "2026-04-27T10:30:00Z",
    "attachments": []
  },
  "session": {
    "id": "sess_abc123",
    "context": {}
  }
}
```

### 3. 健康检查

```bash
GET /api/v1/grading/health
GET /api/v1/webhook/openclaw/health
```

## 🔌 OpenClaw Gateway 集成

### 在 OpenClaw 中配置 Webhook

编辑 OpenClaw 配置文件 `~/.openclaw/openclaw.json`:

```json
{
  "webhooks": [
    {
      "url": "http://你的Java应用服务器IP:8080/api/v1/webhook/openclaw",
      "events": ["message.received"],
      "secret": "your-webhook-secret"
    }
  ]
}
```

### 消息推送流程

1. 学生通过 Telegram/企业微信等渠道发送作业
2. OpenClaw Gateway 接收消息并通过 Webhook 转发给 Java 应用
3. Java 应用异步处理作业批改
4. 批改完成后通过 OpenClaw Gateway API 推送结果给学生

## 🤖 模型调用方案

### 调用原理

系统通过 **OpenClaw Gateway** 调用 AI 模型，采用 OpenAI 兼容的 `/v1/chat/completions` 接口。

```
前端选择模型 → 后端验证模型ID → 构建请求体 → POST /v1/chat/completions → 解析响应
```

### 请求格式

```bash
POST http://<gateway-host>:18789/v1/chat/completions
Authorization: Bearer <token>
Content-Type: application/json

{
  "model": "bailian-token-plan/qwen3.6-plus",
  "messages": [
    {"role": "system", "content": "系统提示词"},
    {"role": "user", "content": "用户消息"}
  ]
}
```

**多模态请求（图片批改）：**

```json
{
  "model": "bailian-token-plan/qwen3.6-plus",
  "messages": [
    {"role": "system", "content": "系统提示词"},
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "请批改以下作业图片"},
        {"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}}
      ]
    }
  ]
}
```

### 模型切换机制

- **请求头**：仅需 `Authorization: Bearer <token>`
- **模型切换**：通过请求体中的 `model` 字段指定，无需 `x-openclaw-agent-id` 头
- **模型ID格式**：`<提供商>/<模型名>`，如 `bailian-token-plan/qwen3.6-plus`

### 可用模型清单

| 模型ID | 展示名 | 多模态 | 上下文 | 最大输出 | 描述 |
|----------|--------|--------|--------|----------|------|
| `bailian-token-plan/qwen3.6-plus` | Qwen3.6 Plus | ✅ | 977K | 64K | ⭐ 默认模型，超大上下文，适合长作业、多页作业、手写作业拍照 |
| `bailian-token-plan/qwen3.6-flash` | Qwen3.6 Flash | ✅ | 977K | 64K | 快速响应模型，适合需要快速反馈的作业批改 |
| `bailian-token-plan/kimi-k2.6` | Kimi K2.6 | ✅ | 256K | 256K | Moonshot 最新模型，适合详细反馈、作文批改 |
| `bailian-token-plan/kimi-k2.5` | Kimi K2.5 | ✅ | 256K | 256K | Moonshot Kimi 多模态模型，支持图片理解 |
| `bailian-token-plan/deepseek-v4-pro` | DeepSeek V4 Pro | ❌ | 160K | 64K | 强推理能力，适合逻辑推理、数学题、复杂分析 |
| `bailian-token-plan/deepseek-v4-flash` | DeepSeek V4 Flash | ❌ | 160K | 64K | 快速推理模型，适合一般文本批改 |
| `bailian-token-plan/deepseek-v3.2` | DeepSeek V3.2 | ❌ | 160K | 64K | 经典文本模型，适合基础题目批改 |
| `bailian-token-plan/glm-5.1` | GLM-5.1 | ❌ | 198K | 16K | 智谱最新模型，均衡性能与质量 |
| `bailian-token-plan/glm-5` | GLM-5 | ❌ | 198K | 16K | 智谱经典模型，适合一般文本批改 |
| `bailian-token-plan/MiniMax-M2.5` | MiniMax-M2.5 | ❌ | 192K | 128K | 通用文本模型，适合一般题目批改 |

> 多模态模型支持 `text + image` 输入，纯文本模型仅支持 `text` 输入。前端会根据 `supportsVision` 字段自动显示/隐藏图片上传区域。

### 核心代码实现

**OpenClawClientService.java** —— 负责与 Gateway 通信：

```java
// 仅需 Authorization 头，通过 model 字段切换模型
WebClient.builder()
    .baseUrl("http://47.122.119.189:18789")
    .defaultHeader("Authorization", "Bearer " + token)
    .build();

// 请求体中指定模型
Map<String, Object> body = Map.of(
    "model", "bailian-token-plan/qwen3.6-plus",  // ← 此处切换模型
    "messages", messages
);
```

**模型解析流程**（PreparationStage.java）：

```java
// 1. 获取前端选择的模型ID
String requested = ctx.getModelId();

// 2. 验证模型在配置清单中存在，不存在则报错
ModelInfo model = modelCatalog.resolve(requested);

// 3. 多模态降级：有图片但模型不支持 vision → 降级为纯文本
if (ctx.isMultimodal() && !model.isSupportsVision()) {
    ctx.setMultimodal(false);
}
```

---

## 🎯 核心功能

### ✅ 已实现

- [x] 编程作业批改（支持多语言）
- [x] OpenClaw Gateway 集成
- [x] Webhook 消息接收
- [x] 异步批改任务处理
- [x] 批改结果推送回原渠道
- [x] 健康检查和监控
- [x] **图片上传批改（多模态）** —— `POST /grading/ai-grade-multipart`
- [x] **多模型可切换** —— `GET /grading/models`，`openclaw.models` 配置
- [x] **批改流水线模块化** —— `Upload → Preparation → Grading → Feedback`

### 🚧 待实现

- [ ] 数据库模型和持久层（替换内存任务存储）
- [ ] 用户管理和权限控制
- [ ] 教师审核队列
- [ ] 批量作业批改
- [ ] 学情分析报表
- [ ] 答疑对话模块
- [ ] 代码沙箱执行
- [ ] Redis 缓存优化

## 📊 监控

应用已集成 Spring Boot Actuator:

```bash
# 健康检查
GET /actuator/health

# 应用信息
GET /actuator/info

# 指标数据
GET /actuator/metrics
```

## 🐛 故障排查

### OpenClaw Gateway 连接失败

检查配置：
```bash
curl http://你的OpenClaw服务器:18789/api/messages/send
```

### 数据库连接失败

检查 MySQL 服务：
```bash
mysql -h 你的MySQL服务器 -u root -p
```

## 📝 开发计划

1. **Phase 1** - 基础功能（当前）
   - OpenClaw Gateway 集成 ✅
   - 编程作业批改 ✅
   - Webhook 接收 ✅

2. **Phase 2** - 完善功能
   - 数据库集成
   - 用户系统
   - 教师审核

3. **Phase 3** - 高级功能
   - 批量批改
   - 学情分析
   - 性能优化

## 📄 License

MIT License

## 👥 联系方式

如有问题，请提交 Issue 或联系开发团队。
