# 错题实验室前端

当前仓库只维护前端演示，后端由你使用 Java + Maven + Spring Cloud 实现。

```bash
cd frontend
npm install
npm run dev
```

API 对接契约见 [API.md](./API.md)。前端当前默认使用 `frontend/src/mock.ts`，后续把服务层替换为真实 API 即可。

## 本地端口

| 端口 | 服务 | 说明 |
| --- | --- | --- |
| `18080` | Nginx | 前端网页及对外 `/api` 入口 |
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

浏览器访问 `http://localhost:18080`，Nacos 控制台访问 `http://localhost:18083`。本地 Compose 中关闭了 Nacos 鉴权，仅用于开发环境。
