# 错题实验室前端

当前仓库只维护前端演示，后端由你使用 Java + Maven + Spring Cloud 实现。

```bash
cd frontend
npm install
npm run dev
```

API 对接契约见 [API.md](./API.md)。前端登录和注册已接入真实 API；错题分析、支付等尚未实现的服务仍使用 `frontend/src/mock.ts`。

## 本地端口

| 端口 | 服务 | 说明 |
| --- | --- | --- |
| `80` / `18080` | Nginx | `learnweb.test` 无端口入口及 `localhost:18080` 备用入口 |
| `18081` | Gateway | Nginx 将 `/api/**` 转发到此端口 |
| `18082` | Auth Service | 登录、注册服务，由 Gateway 通过 Nacos 转发 |
| `18083` | Nacos Console | 本地 Nacos 管理页面 |
| `8848` | Nacos Server | Java 客户端注册与发现端口 |
| `9848` | Nacos gRPC | Nacos 客户端通信端口，由 Nacos 协议固定使用 |

`8848` 和 `9848` 保留 Nacos 标准端口，是因为 Nacos 客户端会根据主端口自动计算 gRPC 端口，不能与应用端口一起简单顺延。

## 本地启动顺序

```bash
# 1. 启动 Nacos、PostgreSQL、Redis、RabbitMQ 和 Nginx
docker compose up -d

# 2. 启动 Auth Service（完成启动类和登录注册代码后）
mvn -f backend/pom.xml -pl auth-service spring-boot:run

# 3. 启动 Gateway
mvn -f backend/pom.xml -pl gateway spring-boot:run
```

在 `/etc/hosts` 中加入 `127.0.0.1 learnweb.test` 后，浏览器访问 `http://learnweb.test`；也可以继续使用备用地址 `http://localhost:18080`。`.test` 是保留给测试用途的顶级域名，不会与公网网站冲突。Nacos 控制台位于 `http://localhost:18083`。本地 Compose 中关闭了 Nacos 鉴权，仅用于开发环境。

## API 在线调试

同时启动 Auth Service、Gateway 和 Nginx 后，可在浏览器打开统一入口 [Swagger UI](http://learnweb.test/swagger.html)。Gateway 页面聚合各微服务的 OpenAPI 文档，目前包含 Auth Service；其原始聚合地址为 `http://learnweb.test/v3/api-docs/auth-service`。

需要调试受 JWT 保护的接口时，先调用登录接口取得 token，再点击 Swagger UI 右上角的 **Authorize**，填入 token 本身即可。生产环境可设置 `SWAGGER_ENABLED=false` 关闭页面和文档端点。

Gateway 本地默认连接 `localhost:8848` 的 Nacos 和 `localhost:6379` 的 Redis。Auth Service 本地默认使用 `postgres/postgres` 连接 `localhost:5432` 的 `learn` 数据库，可通过 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME` 和 `DB_PASSWORD` 覆盖。非本地环境还需注入至少 32 字节的 `JWT_SECRET`；Auth Service 签发 JWT 时必须使用与 Gateway 相同的 `JWT_SECRET`。

## 数据库表结构

可复现的 PostgreSQL 建表脚本统一存放在 `sql_table/`。全新 PostgreSQL 数据卷首次启动时，Compose 会自动执行该目录中的 SQL。

已有数据卷不会再次执行 Docker 初始化脚本，可手动导入：

```bash
docker compose exec -T postgres psql -U postgres -d learn < sql_table/001_create_users.sql
```
