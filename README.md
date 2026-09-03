# 错题实验室

当前仓库包含 React 前端，以及 Java、Maven、Spring Cloud 实现的 Gateway、Auth Service、Mistake Service 和 Payment Service。

```bash
cd frontend
npm install
npm run dev
```

API 对接契约和逐项实现状态见 [API.md](./API.md)。前端已接入认证、资料、Dashboard、错题和本地模拟支付 API；Mistake Service 使用 RabbitMQ Worker 调用 OpenAI Responses API 完成结构化分析。

## 本地端口

| 端口 | 服务 | 说明 |
| --- | --- | --- |
| `80` / `18080` | Nginx | `learnweb.test` 无端口入口及 `localhost:18080` 备用入口 |
| `18081` | Gateway | Nginx 将 `/api/**` 转发到此端口 |
| `18082` | Auth Service | 登录、注册服务，由 Gateway 通过 Nacos 转发 |
| `18083` | Nacos Console | 本地 Nacos 管理页面 |
| `18085` | Payment Service | 套餐查询和本地模拟支付，由 Gateway 通过 Nacos 转发 |
| `8848` | Nacos Server | Java 客户端注册与发现端口 |
| `9848` | Nacos gRPC | Nacos 客户端通信端口，由 Nacos 协议固定使用 |
| `3900` | Garage S3 API | 错题图片对象存储，仅监听本机 |

`8848` 和 `9848` 保留 Nacos 标准端口，是因为 Nacos 客户端会根据主端口自动计算 gRPC 端口，不能与应用端口一起简单顺延。

## 本地启动顺序

```bash
# 1. 启动 Nacos、PostgreSQL、Redis、RabbitMQ、Garage 和 Nginx
docker compose up -d

# 2. 启动 Auth Service（完成启动类和登录注册代码后）
mvn -f backend/pom.xml -pl auth-service spring-boot:run

# 3. 启动 Gateway
mvn -f backend/pom.xml -pl gateway spring-boot:run

# 4. 启动 Mistake Service；OPENAI_API_KEY 只从环境变量注入
OPENAI_API_KEY=your-key mvn -f backend/pom.xml -pl mistake-service spring-boot:run

# 5. 启动 Payment Service；模拟支付仅在本地显式开启
PAYMENT_MOCK_ENABLED=true mvn -f backend/pom.xml -pl payment-service spring-boot:run
```

在 `/etc/hosts` 中加入 `127.0.0.1 learnweb.test` 后，浏览器访问 `http://learnweb.test`；也可以继续使用备用地址 `http://localhost:18080`。`.test` 是保留给测试用途的顶级域名，不会与公网网站冲突。Nacos 控制台位于 `http://localhost:18083`。本地 Compose 中关闭了 Nacos 鉴权，仅用于开发环境。

## API 在线调试

同时启动 Auth Service、Gateway 和 Nginx 后，可在浏览器打开统一入口 [Swagger UI](http://learnweb.test/swagger.html)。Gateway 页面聚合各微服务的 OpenAPI 文档，目前包含 Auth Service；其原始聚合地址为 `http://learnweb.test/v3/api-docs/auth-service`。

需要调试受 JWT 保护的接口时，先调用登录接口取得 token，再点击 Swagger UI 右上角的 **Authorize**，填入 token 本身即可。生产环境可设置 `SWAGGER_ENABLED=false` 关闭页面和文档端点。

Gateway 和开启了模拟支付的 Payment Service 本地默认连接 `localhost:6379` 的 Redis；Payment Service 使用 Redis 分布式锁收敛跨实例并发，PostgreSQL 的唯一约束、行锁和事务仍是支付正确性的最终保障。Gateway 本地默认连接 `localhost:8848` 的 Nacos。Auth Service 本地默认使用 `postgres/postgres` 连接 `localhost:5432` 的 `learn` 数据库，可通过 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME` 和 `DB_PASSWORD` 覆盖。非本地环境还需注入至少 32 字节的 `JWT_SECRET`；Auth Service 签发 JWT 时必须使用与 Gateway 相同的 `JWT_SECRET`。

## 本地对象存储

Compose 使用 Garage `v2.3.0` 提供 S3 兼容对象存储，首次启动时自动创建私有 Bucket `mistake-images`。宿主机上运行的 Java 服务使用以下开发配置：

```yaml
storage:
  s3:
    endpoint: http://localhost:3900
    public-endpoint: http://localhost:3900
    region: garage
    bucket: mistake-images
    access-key: GK0123456789abcdef0123456789abcdef
    secret-key: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
    path-style-access: true
    presigned-url-ttl: 15m
```

所有字段都可以通过环境变量覆盖：`GARAGE_ENDPOINT`、`GARAGE_PUBLIC_ENDPOINT`、`GARAGE_REGION`、`GARAGE_BUCKET`、`GARAGE_ACCESS_KEY`、`GARAGE_SECRET_KEY`、`GARAGE_PATH_STYLE_ACCESS` 和 `GARAGE_PRESIGNED_URL_TTL`。

如果 Java 服务也运行在同一个 Compose 网络，将 `GARAGE_ENDPOINT` 改为 `http://garage:3900`，但 `GARAGE_PUBLIC_ENDPOINT` 仍必须使用浏览器能访问的地址。本地可以使用 `http://localhost:3900`，生产环境应使用对象存储域名。开发凭据明文保存在 Compose 中，只允许用于本地环境；生产环境需要通过 Secret 注入。对象数据持久化在 `garage-data` Volume，普通的 `docker compose down` 不会删除它。

## 数据库表结构

可复现的 PostgreSQL 建表脚本统一存放在 `sql_table/`。全新 PostgreSQL 数据卷首次启动时，Compose 会自动执行该目录中的 SQL。

| 脚本 | 表 | 说明 |
| --- | --- | --- |
| `001_create_users.sql` | `users` | 用户、额度和认证状态 |
| `002_create_questions.sql` | `official_questions`、`personal_questions` | 官方题与个人题；个人题可选择性匹配官方题 |
| `003_add_mistake_analysis.sql` | `personal_questions` | 个人错题的分析状态、分析结果、掌握状态和用户答案 |
| `004_create_mistake_outbox.sql` | `mistake_outbox_events` | 可靠发布错题分析任务的事务 Outbox |
| `005_create_payments.sql` | `payment_plans`、`payment_orders` | 服务端套餐配置、幂等支付订单及金额/额度快照 |

已有数据卷不会再次执行 Docker 初始化脚本，可手动导入：

```bash
docker compose exec -T postgres psql -U postgres -d learn < sql_table/001_create_users.sql
docker compose exec -T postgres psql -U postgres -d learn < sql_table/002_create_questions.sql
docker compose exec -T postgres psql -U postgres -d learn < sql_table/003_add_mistake_analysis.sql
docker compose exec -T postgres psql -U postgres -d learn < sql_table/004_create_mistake_outbox.sql
docker compose exec -T postgres psql -U postgres -d learn < sql_table/005_create_payments.sql
```

## AI 分析配置

`OPENAI_API_KEY` 必须通过环境变量或 Secret 注入。可选配置包括 `OPENAI_MODEL`（默认 `gpt-5.4`）、`OPENAI_BASE_URL`、`OPENAI_CONNECT_TIMEOUT`、`OPENAI_READ_TIMEOUT` 和 `OPENAI_MAX_OUTPUT_TOKENS`。请求使用 Responses API 的图片输入与严格 JSON Schema 输出；响应原文不会写入数据库。

本仓库提供可完整运行的本地模拟支付流程。接入微信支付、支付宝等正式渠道前，必须先选定渠道并按其服务端回调签名协议实现，不能复用模拟支付入口作为生产支付。
