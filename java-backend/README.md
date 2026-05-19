# OpenClaw 智能作业批改系统 (Java 版本)

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
