# ClawGrad · OpenClaw 智能作业批改系统

基于 OpenClaw Gateway 的 AI 作业批改平台，支持**多科目通用批改、图片(多模态)批改、多模型可切换**，后端采用**流水线模块化架构**（上传 → 整理 → 批改 → 反馈），便于扩展和监控。

```
┌──────────┐   POST(JSON / multipart)   ┌─────────────────────────────────────────────┐
│ Frontend │ ─────────────────────────► │            Java Backend (Spring Boot)        │
│  React   │                            │                                              │
│  + Antd  │ ◄───── poll task ───────── │  Controller → Pipeline                       │
└──────────┘                            │     ├─ UploadStage      (输入整理 / 附件)    │
                                        │     ├─ PreparationStage (选模型 / 拼Prompt) │
                                        │     ├─ GradingStage     (OpenClaw / 多模态) │
                                        │     └─ FeedbackStage    (解析 / 入TaskStore)│
                                        │                                              │
                                        │  OpenClawClientService → OpenClaw Gateway    │
                                        └─────────────────────────────────────────────┘
```

## 📦 目录结构

```
ClawGrad--/
├── README.md                # ← 项目总览
├── docker-compose.yml       # MySQL / Redis / RabbitMQ / 后端 一键起
├── .env.example
├── frontend/                # React + Vite + Antd
│   └── src/
│       ├── pages/           # HomePage / GradePage / ResultPage
│       ├── services/api.ts  # 后端调用封装
│       └── components/
├── java-backend/            # Spring Boot 3.2
│   ├── src/main/java/com/openclaw/grading/
│   │   ├── controller/      # REST 入口
│   │   ├── service/         # 业务服务
│   │   ├── pipeline/        # ★ 批改流水线
│   │   │   ├── GradingPipeline.java
│   │   │   ├── GradingContext.java
│   │   │   ├── GradingStage.java
│   │   │   └── stage/       # UploadStage / PreparationStage / GradingStage / FeedbackStage
│   │   ├── model/dto/       # 请求 / 响应
│   │   └── config/
│   └── README.md            # 后端独立文档（详细 API、OpenClaw 集成等）
└── storage/                 # 上传图片落盘目录（按 taskId 分目录）
```

## ✨ 核心特性

- **全科目通用批改** — 数学 / 语文 / 英语 / 物理 / 化学 / 编程 …，AI 自动识别
- **图片批改（多模态）** — 直接上传作业拍照，由具备视觉能力的模型识别并批改
- **多模型可切换** — 配置文件维护模型清单（GLM / Qwen / GPT-4o / Claude 等），前端下拉选择
- **流水线模块化** — Upload → Preparation → Grading → Feedback 四阶段独立可替换
- **异步任务 + 轮询** — 提交立即返回 `taskId`，前端轮询 `GET /grading/ai-tasks/{taskId}` 获取结果
- **OpenClaw Gateway 集成** — 兼容 OpenAI `/v1/chat/completions`，统一接入多家大模型

## 🚀 快速开始

### 1. 启动依赖（MySQL / Redis / RabbitMQ）

```bash
docker compose up -d mysql redis rabbitmq
```

### 2. 启动后端

```bash
cd java-backend
mvn spring-boot:run
# 默认监听 http://localhost:8080/api/v1
```

详细 API 文档与 OpenClaw 配置见 [java-backend/README.md](./java-backend/README.md)。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
# http://localhost:5173
```

## 🔌 主要 API 速览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET`  | `/api/v1/grading/models`               | 获取可用模型列表（含是否支持视觉） |
| `POST` | `/api/v1/grading/ai-grade`             | 纯文本提交批改（JSON） |
| `POST` | `/api/v1/grading/ai-grade-multipart`   | 文本 + 图片提交批改（multipart） |
| `GET`  | `/api/v1/grading/ai-tasks/{taskId}`    | 轮询批改结果 |
| `POST` | `/api/v1/grading/code`                 | 编程作业同步批改 |
| `POST` | `/api/v1/callback/grading`             | OpenClaw 回调接收 |
| `POST` | `/api/v1/webhook/openclaw`             | OpenClaw Webhook |

## 🧩 流水线扩展指南

实现新的 `GradingStage` 并在 `GradingPipeline` 注册即可，例如：
- `RetrieveKnowledgeStage`：批改前检索知识库（RAG）
- `PlagiarismStage`：作业查重
- `NotifyStage`：批改完成后通过 OpenClaw 推送回学生渠道

```java
@Component
public class MyStage implements GradingStage {
    @Override public String name() { return "my-stage"; }
    @Override public void execute(GradingContext ctx) { /* ... */ }
}
```

## 📜 License

仅用于教学与课程实践。
