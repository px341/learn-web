# 错题实验室 API 契约

建议后端统一前缀：`/api`。

## 当前实现状态

以下状态以仓库当前代码为准；“契约已定义”不代表对应接口已经实现。

| 模块         | 前端现状                                          | 后端现状                                                           | 后续工作                              |
| ------------ | ------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------- |
| 登录、注册   | 已调用真实 API                                    | Auth Service 已实现，Gateway 已路由                                | 保持现状                              |
| 当前用户查询 | 启动受保护页面时调用真实 API，并刷新登录缓存      | 已实现 `GET /api/auth/me`                                          | 保持现状                              |
| 资料修改     | 保存按钮尚未发送请求                              | 已实现 `PATCH /api/auth/me`                                        | 前端接入                              |
| 用户头像     | 已实现选择、预览和上传                            | 已实现 `PUT /api/auth/me/avatar`、Garage 存储及预签名 URL           | 补充对象清理重试任务                  |
| Dashboard    | 仍从错题 Mock 计算，部分数据硬编码                | 已实现 `GET /api/dashboard/stats`                                  | 前端接入统计接口                      |
| 错题列表     | 使用 `localStorage` Mock                          | 尚未实现 `GET /api/mistakes`                                      | 实现分页、筛选和排序                  |
| 错题详情     | 仍读取 Mock；置信度已改为读取 `analysis.confidence` | 已实现 `GET /api/mistakes/{id}`、用户隔离和图片预签名              | 前端接入详情接口及轮询                |
| 错题上传     | 使用 Mock，图片以 Base64 保存                     | 已实现 `POST /api/mistakes`、额度事务、Garage 上传和 Outbox         | 前端接入                              |
| 分析进度     | 前端定时器模拟状态变化                            | Outbox 发布已实现，异步分析 Worker 尚未实现                         | 实现 Worker 和前端轮询                |
| 标记已掌握   | 按钮尚未发送请求                                  | 已实现 `PATCH /api/mistakes/{id}/mastery`                          | 前端接入 mastery 接口                 |
| 额度、支付   | 前端仍直接修改本地额度                            | 已实现套餐查询、幂等模拟支付及事务入账                              | 前端接入；正式支付接入支付平台        |

Gateway 已配置 `/api/auth/**`、`/api/dashboard/**`、`/api/mistakes/**` 和 `/api/payments/**` 路由，并聚合 Auth Service、Mistake Service 与 Payment Service 的 OpenAPI 文档。

## 通用约定

- 请求和响应使用 JSON，上传错题使用 `multipart/form-data`
- 登录成功返回 `token` 和 `user`
- 后续请求携带 `Authorization: Bearer <token>`
- 所有时间使用 ISO 8601
- 图片限制：PNG/JPG/WEBP，最大 10MB
- JSON 枚举值统一使用小写，例如 `queued`、`analyzing`、`completed`、`failed`，与前端类型保持一致
- 列表页码从 `0` 开始，默认 `page=0&size=20`，单页最多 100 条
- 除登录和注册外，本文接口均要求登录，并且只能访问当前用户自己的数据

### DTO、VO 与 Entity

- `DTO`（Data Transfer Object）：接收客户端请求，只包含接口允许提交的字段。
- `VO`（View Object）：返回给客户端，只包含允许前端看到的字段。
- `Entity`：数据库实体，仅在服务内部使用，不直接作为接口请求或响应。
- 密码、密码哈希等敏感字段不能出现在任何 VO 中。

成功响应统一使用 `ApiResponse<T>`：

| 字段   | Java 类型 | JSON 类型 | 必填 | 说明         |
| ------ | --------- | --------- | ---- | ------------ |
| `data` | `T`       | object    | 是   | 实际响应数据 |

```json
{
  "data": {}
}
```

失败响应统一使用 `ErrorVO`：

| 字段          | Java 类型             | JSON 类型 | 必填 | 说明                                 |
| ------------- | --------------------- | --------- | ---- | ------------------------------------ |
| `code`        | `String`              | string    | 是   | 稳定的业务错误码，供前端判断错误类型 |
| `message`     | `String`              | string    | 是   | 可直接展示或记录的错误信息           |
| `fieldErrors` | `Map<String, String>` | object    | 否   | 参数校验失败时，各字段对应的错误信息 |

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求参数校验失败",
  "fieldErrors": {
    "email": "邮箱格式不正确"
  }
}
```

## 认证

认证模块只使用以下 DTO 和 VO：

```text
AuthDTO            ─┐
                    ├─> AuthUserController ─> ApiResponse<AuthVO>
RegisterRequestDTO ─┘                         ├─ token
                                             └─ UserVO
```

### `POST /api/auth/login`

- 请求 DTO：`AuthDTO`（功能上等同于登录请求 DTO）
- 成功响应：`ApiResponse<AuthVO>`

#### `AuthDTO`

| 字段       | Java 类型 | JSON 类型 | 必填 | 校验规则                      | 说明                                   |
| ---------- | --------- | --------- | ---- | ----------------------------- | -------------------------------------- |
| `email`    | `String`  | string    | 是   | 非空、合法邮箱、最长 254 字符 | 登录邮箱；后端先去除首尾空格并转为小写 |
| `password` | `String`  | string    | 是   | 非空、6～64 字符              | 用户明文密码，仅用于本次认证           |

请求：

```json
{
  "email": "demo@mistake.lab",
  "password": "123456"
}
```

响应：

```json
{
  "data": {
    "token": "jwt-token",
    "user": {
      "id": "u1",
      "name": "演示同学",
      "email": "demo@mistake.lab",
      "credits": 3
    }
  }
}
```

登录失败：账号或密码错误统一返回 HTTP `401` 和错误码 `INVALID_CREDENTIALS`，不能告诉客户端具体是账号不存在还是密码错误。

### `POST /api/auth/register`

- 请求 DTO：`RegisterRequestDTO`
- 成功响应：`ApiResponse<AuthVO>`

#### `RegisterRequestDTO`

| 字段                   | Java 类型 | JSON 类型 | 必填 | 校验规则                      | 说明                                   |
| ---------------------- | --------- | --------- | ---- | ----------------------------- | -------------------------------------- |
| `name`                 | `String`  | string    | 是   | 去除首尾空格后 1～30 字符     | 用户显示名称                           |
| `email`                | `String`  | string    | 是   | 非空、合法邮箱、最长 254 字符 | 注册邮箱；存储前去除首尾空格并转为小写 |
| `password`             | `String`  | string    | 是   | 6～64 字符                    | 明文密码；后端必须使用 BCrypt 后再保存 |
| `passwordConfirmation` | `String`  | string    | 是   | 必须与 `password` 完全一致    | 仅用于请求校验，不能写入数据库         |

请求：

```json
{
  "name": "小林",
  "email": "xiaolin@example.com",
  "password": "123456",
  "passwordConfirmation": "123456"
}
```

后端需要校验邮箱格式、密码长度、两次密码一致，以及邮箱是否已注册。成功后返回与登录接口相同的 `token` 和 `user` 数据结构，并默认赠送 3 次分析额度。

邮箱已注册时返回 HTTP `409` 和错误码 `EMAIL_ALREADY_REGISTERED`；其他字段校验失败时返回 HTTP `400` 和错误码 `VALIDATION_ERROR`。

### 认证响应 VO

#### `AuthVO`

登录和注册成功后，`ApiResponse.data` 使用此结构。

| 字段    | Java 类型 | JSON 类型 | 必填 | 说明                                                        |
| ------- | --------- | --------- | ---- | ----------------------------------------------------------- |
| `token` | `String`  | string    | 是   | JWT Access Token；请求其他接口时放入 `Authorization` 请求头 |
| `user`  | `UserVO`  | object    | 是   | 当前登录用户的公开信息                                      |

#### `UserVO`

| 字段        | Java 类型 | JSON 类型   | 必填 | 说明                                                     |
| ----------- | --------- | ----------- | ---- | -------------------------------------------------------- |
| `id`        | `String`  | string      | 是   | 用户唯一标识；UUID 在 JSON 中也序列化为字符串            |
| `name`      | `String`  | string      | 是   | 用户显示名称                                             |
| `email`     | `String`  | string      | 是   | 用户邮箱                                                 |
| `credits`   | `Integer` | number      | 是   | 剩余可用分析次数；新用户默认为 3                         |
| `avatarUrl` | `String`  | string/null | 否   | Garage 私有对象生成的短期预签名 URL；没有头像时为 `null` |

公共响应对象放在 `common-model`，认证业务自己的 DTO、VO 仍放在 `auth-service`：

```text
backend/
├── common-model/src/main/java/com/learn/common/
│   ├── dto/PageQueryDTO.java
│   ├── entity/BaseEntity.java
│   └── vo/
│       ├── ApiResponse.java
│       ├── ErrorVO.java
│       └── PageVO.java
└── auth-service/src/main/java/com/learn/auth/
    ├── dto/
    │   ├── AuthDTO.java
    │   └── RegisterRequestDTO.java
    └── vo/
        ├── AuthVO.java
        └── UserVO.java
```

## 当前用户

### `GET /api/auth/me`

返回最新用户资料和额度。前端刷新页面后应调用该接口刷新登录缓存，不能长期以登录时返回的 `credits` 为准。

响应：`ApiResponse<UserVO>`。

```json
{
  "data": {
    "id": "95ee826a-6c69-4544-b30c-94f7fe49df24",
    "name": "小林",
    "email": "xiaolin@example.com",
    "credits": 3,
    "avatarUrl": "http://localhost:3900/mistake-images/...?X-Amz-Signature=..."
  }
}
```

### `PATCH /api/auth/me`

个人设置页保存用户资料。

请求：

```json
{
  "name": "小林同学",
  "email": "xiaolin@example.com"
}
```

- `name`：可选；去除首尾空格后 1～30 字符。
- `email`：可选；合法邮箱且最长 254 字符，保存前转为小写。
- 两个字段都没有提供时返回 `400 VALIDATION_ERROR`。
- 邮箱已被其他账号使用时返回 `409 EMAIL_ALREADY_REGISTERED`。
- 成功响应为更新后的 `ApiResponse<UserVO>`。

当前阶段不再单独设计 `/api/entitlements`：`GET /api/auth/me` 已返回 `credits`，创建错题和支付响应还会返回最新的 `creditsRemaining`。额度以服务端为唯一事实来源，前端不得自行计算最终额度。

### `PUT /api/auth/me/avatar`

替换当前用户头像。请求类型为 `multipart/form-data`，文件字段名为 `avatar`。

- 用户 UUID 从 JWT subject 获取，客户端不提交用户 ID。
- 支持 PNG、JPEG、WEBP，最大 5MB；后端必须检查文件真实内容。
- 图片写入 Garage 私有 Bucket，推荐 Object Key：`users/{userId}/avatars/{uuid}.{ext}`。
- 成功响应为更新后的 `ApiResponse<UserVO>`，其中 `avatarUrl` 是短期预签名地址。
- Garage 写入成功后再更新用户头像元数据；数据库更新失败时应删除刚上传的新对象。
- 新头像提交成功后异步或尽力删除旧对象，删除失败记录日志并由清理任务重试。

`users` 表只保存以下对象元数据：

| 字段                   | 说明                                                             |
| ---------------------- | ---------------------------------------------------------------- |
| `avatar_bucket`        | Garage Bucket 名称；当前 Demo 可复用私有 `mistake-images` Bucket |
| `avatar_object_key`    | 对象键，不保存完整 URL                                           |
| `avatar_original_name` | 用户上传时的原始文件名，仅用于审计                               |
| `avatar_content_type`  | 服务端识别出的真实 MIME 类型                                     |
| `avatar_size`          | 文件字节数                                                       |
| `avatar_sha256`        | 小写十六进制 SHA-256，用于完整性校验和去重判断                   |
| `avatar_updated_at`    | 当前头像最后更新时间                                             |

数据库不得保存头像二进制、预签名 URL、Garage Access Key 或 Secret Key。前端上传交互、Auth Service 的 Garage 写入和短期预签名 URL 均已实现。

## Dashboard

### `GET /api/dashboard/stats`

实现状态：后端及 Gateway 路由已实现；前端 Dashboard 尚未接入，当前仍读取 Mock 数据。

响应：

```json
{
  "data": {
    "total": 28,
    "weeklyNew": 5,
    "totalChangePercent": 12,
    "questionTypeCounts": [
      { "questionType": "选择题", "count": 10 },
      { "questionType": "解答题", "count": 7 },
      { "questionType": "未分类", "count": 2 }
    ]
  }
}
```

- `weeklyNew` 为近 7 天新增的未归档错题数。
- `questionTypeCounts` 按 `personal_questions.question_type` 统计，空值归为“未分类”。
- `totalChangePercent` 为近 7 天新增相对于 7 天前累计数的变化百分比，不带 `%`。
- 没有数据时计数返回 `0`，数组返回 `[]`，不要返回 `null`。
- Dashboard 的最近错题复用 `GET /api/mistakes?page=0&size=4`，无需在统计响应中重复返回。

## 错题

### 错题数据结构

列表项使用 `MistakeSummaryVO`：

```json
{
  "id": "cf9307dc-e284-410b-9372-e84a768633f8",
  "title": "二次函数图像与最值",
  "subject": "数学",
  "chapter": "函数",
  "type": "概念不清",
  "status": "completed",
  "mastered": false,
  "createdAt": "2026-08-22T09:42:00+08:00"
}
```

状态及含义：

| 状态        | 含义                              |
| ----------- | --------------------------------- |
| `queued`    | 已创建并进入分析队列              |
| `analyzing` | OCR 或 AI 分析中                  |
| `completed` | 分析成功，可展示 `analysis`       |
| `failed`    | 分析失败，可展示 `failureMessage` |

### `GET /api/mistakes`

实现状态：尚未实现。以下内容是后续实现必须遵循的接口契约。

返回当前用户的错题分页列表。

| 查询参数   | 必填 | 说明                             |
| ---------- | ---- | -------------------------------- |
| `keyword`  | 否   | 匹配题目名称、学科、章节或知识点 |
| `subject`  | 否   | 精确筛选学科                     |
| `status`   | 否   | 按分析状态筛选                   |
| `mastered` | 否   | `true` 或 `false`                |
| `page`     | 否   | 从 0 开始，默认 0                |
| `size`     | 否   | 默认 20，最大 100                |
| `sort`     | 否   | 默认 `createdAt,desc`            |

响应：`ApiResponse<PageVO<MistakeSummaryVO>>`。

```json
{
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

### `GET /api/mistakes/{id}`

实现状态：后端及 Gateway 路由已实现；前端详情页尚未调用该接口。

返回错题详情。不存在或不属于当前用户时统一返回 `404 MISTAKE_NOT_FOUND`，避免泄露其他用户的数据。

```json
{
  "data": {
    "id": "cf9307dc-e284-410b-9372-e84a768633f8",
    "title": "二次函数图像与最值",
    "subject": "数学",
    "chapter": "函数",
    "type": "概念不清",
    "questionText": "已知二次函数……",
    "userAnswer": "x = 2",
    "status": "completed",
    "mastered": false,
    "createdAt": "2026-08-22T09:42:00+08:00",
    "image": {
      "url": "http://localhost:3900/mistake-images/...?X-Amz-Signature=...",
      "expiresAt": "2026-08-22T10:02:00+08:00"
    },
    "analysis": {
      "summary": "你混淆了开口方向与顶点坐标的关系。",
      "knowledge": ["二次函数顶点式", "开口方向与最值"],
      "steps": ["将函数化为顶点式", "根据系数判断开口方向"],
      "suggestion": "建议重新练习三道顶点式变形题。",
      "answer": "当 a > 0 时有最小值。",
      "confidence": 94
    },
    "failureMessage": null
  }
}
```

- `image`、`analysis` 和 `failureMessage` 根据实际状态允许为 `null`。
- Garage Bucket 保持私有，数据库只保存 Object Key，不保存签名 URL。
- 因前端使用 Bearer Token，普通 `<img>` 标签无法附加 `Authorization` 请求头，所以详情返回短期有效的预签名图片 URL。
- `confidence` 是 `0～100` 的整数；前端展示已改为读取该字段，但当前数据源仍是 Mock。
- Mapper 使用 `id + currentUserId + ACTIVE` 一次性限定查询范围；不存在、已归档和属于其他用户三种情况使用同一个 `404 MISTAKE_NOT_FOUND`。
- `personal_questions.status` 表示记录生命周期（`ACTIVE/ARCHIVED`），`analysis_status` 才映射为接口中的分析状态。
- 数据库字段和约束由 `sql_table/003_add_mistake_analysis.sql` 管理；分析完成、分析失败和掌握状态之间的一致性同时受数据库约束保护。

### `POST /api/mistakes`

实现状态：后端及 Gateway 路由已实现，前端尚未接入。接口会完成文件校验、Garage 上传、额度扣减、错题创建和 Outbox 写入；Outbox 发布器会使用 RabbitMQ Publisher Confirm 重试发布。异步分析 Worker 尚未实现，因此消息发布后暂时不会生成分析结果。

创建错题、扣减 1 次额度并开始异步分析。请求类型为 `multipart/form-data`。

| 字段         | 类型   | 必填     | 校验规则                                         |
| ------------ | ------ | -------- | ------------------------------------------------ |
| `title`      | string | 否       | 最长 100；为空时由题目文字生成或使用“未命名错题” |
| `subject`    | string | 是       | 1～30 字符                                       |
| `chapter`    | string | 否       | 最长 100                                         |
| `type`       | string | 是       | 1～30 字符                                       |
| `text`       | string | 条件必填 | 最长 10000；`text` 和 `image` 至少提供一个       |
| `userAnswer` | string | 否       | 最长 10000                                       |
| `image`      | file   | 条件必填 | PNG/JPEG/WEBP，最大 10MB；与 `text` 至少提供一个 |

后端必须检查文件真实内容，不能只信扩展名或客户端提供的 MIME。图片上传 Garage 成功后，错题记录、额度扣减和待发布事件在同一数据库事务中提交，再由 Outbox 发布器发送 RabbitMQ 分析消息；消息只传 `mistakeId`，不传图片二进制。这样 RabbitMQ 暂时不可用时不会出现已扣额度但任务永久丢失的问题。

当前实现细节：

- 用户额度行使用 `SELECT ... FOR UPDATE` 锁定，并在同一事务中扣减，避免并发请求透支额度。
- Garage 上传发生在数据库写入前；上传成功后注册事务同步回调，数据库回滚或提交失败时尽力删除刚上传的对象。
- 数据库事务原子提交额度扣减、`personal_questions` 和 `mistake_outbox_events`，任一步失败都会整体回滚。
- Outbox 发布失败时保留 `PENDING` 状态并指数退避重试，最长退避 300 秒。
- RabbitMQ 消息体只包含 `mistakeId`；Outbox ID 放在消息 `messageId` 中，供后续消费者实现幂等处理。

成功返回 HTTP `202 Accepted`：

```json
{
  "data": {
    "mistake": {
      "id": "cf9307dc-e284-410b-9372-e84a768633f8",
      "title": "二次函数图像与最值",
      "subject": "数学",
      "chapter": "函数",
      "type": "概念不清",
      "status": "queued",
      "mastered": false,
      "createdAt": "2026-08-22T09:42:00+08:00"
    },
    "creditsRemaining": 2
  }
}
```

常见失败：

| HTTP | 错误码                     | 场景                            |
| ---- | -------------------------- | ------------------------------- |
| 400  | `MISTAKE_CONTENT_REQUIRED` | 图片和题目文字均为空            |
| 402  | `INSUFFICIENT_CREDITS`     | 分析额度不足                    |
| 413  | `IMAGE_TOO_LARGE`          | 图片超过 10MB                   |
| 415  | `UNSUPPORTED_IMAGE_TYPE`   | 图片格式不支持或文件签名不匹配  |
| 503  | `STORAGE_UNAVAILABLE`      | Garage 暂时不可用，不能扣减额度 |

前端进入详情页后，每 2 秒调用一次 `GET /api/mistakes/{id}`；状态变为 `completed` 或 `failed` 后停止轮询。后续如需降低轮询开销，再增加 SSE，不作为第一版必需项。

### `PATCH /api/mistakes/{id}/mastery`

实现状态：`UpdateMasteryDTO` 已定义，接口尚未实现。

标记或取消标记“已掌握”。

请求：

```json
{
  "mastered": true
}
```

成功返回更新后的 `ApiResponse<MistakeSummaryVO>`。只有 `completed` 状态允许标记，否则返回 `409 ANALYSIS_NOT_COMPLETED`。

### 图片存储说明

详情查询的图片预签名读取和 `POST /api/mistakes` 的原图上传均已实现。

Garage 配置：

```text
Endpoint: http://localhost:3900
Region: garage
Bucket: mistake-images
Path-style access: true
```

建议 Object Key：

```text
users/{userId}/mistakes/{mistakeId}/original.{ext}
```

Object Key、原始文件名、内容类型、大小和 SHA-256 写入数据库；图片二进制只保存在 Garage，不写入 PostgreSQL、Redis、RabbitMQ 或日志。

## 支付与额度演示

实现状态：数据库、Payment Service API 和 Gateway 路由已实现；前端接入与正式支付平台尚未实现。模拟支付接口默认关闭，本地或演示环境需显式设置 `PAYMENT_MOCK_ENABLED=true`。

### `GET /api/payments/plans`

返回服务端配置的套餐，前端不应写死金额和次数。

```json
{
  "data": [
    {
      "id": "trial",
      "name": "单次体验",
      "credits": 1,
      "priceFen": 100,
      "description": "适合先试试看",
      "recommended": false
    },
    {
      "id": "starter",
      "name": "进阶学习包",
      "credits": 10,
      "priceFen": 800,
      "description": "平均每次 ¥0.8",
      "recommended": true
    }
  ]
}
```

金额统一使用整数分，避免浮点误差。

### `POST /api/payments/mock`

仅限本地和演示环境；生产环境必须关闭该接口。

请求：

```json
{
  "planId": "starter"
}
```

套餐次数和金额由后端根据 `planId` 查询，客户端不得提交可信的 `count` 或金额。请求携带唯一的 `Idempotency-Key`，相同 Key 重试不得重复增加额度。

响应：

```json
{
  "data": {
    "orderId": "8b6d52bf-95f7-4226-bb1f-2063f4e70a1b",
    "status": "paid",
    "creditsAdded": 10,
    "creditsRemaining": 13,
    "paidAt": "2026-08-22T10:10:00+08:00"
  }
}
```

正式支付必须由 Java 服务端验证支付平台回调签名并更新额度，不能根据前端跳转到“支付成功”页判断到账。

## 前端接入时需要修正的问题

1. `frontend/src/mock.ts` 中除认证外的逻辑全部替换为真实 HTTP 请求。
2. 上传页保存原始 `File`，预览使用 `URL.createObjectURL(file)`；不要再用 Base64 写入 `localStorage`。
3. 当前上传表单虽然收集了 `text`，但没有传给 `api.submit`；“你的答案”也没有绑定状态，需要补上 `text` 和 `userAnswer`。
4. 列表搜索、学科筛选、状态筛选和排序目前只做了部分本地效果，需要改为上述查询参数。
5. Dashboard 的日期、平均正确率、变化比例、趋势图和待复习数量目前存在硬编码，需要读取统计接口。
6. 详情页置信度已读取 `analysis.confidence`，但详情数据仍来自 Mock；需要接入详情接口，“标记为已掌握”按钮也需要接入 mastery 接口。
7. `failed` 状态当前会被错误显示为“排队中”，接入真实状态前需要补充失败样式和重试提示。
8. 个人资料保存按钮目前无请求，需要接入 `PATCH /api/auth/me`。
9. 支付页套餐和额度增加目前完全在浏览器内完成，需要改为服务端接口。
10. 前端目前保存的是登录瞬间的用户额度，创建错题或支付后应使用接口返回的 `creditsRemaining` 更新，并在应用初始化时调用 `GET /api/auth/me` 校准。

## 推荐服务拆分

```text
Gateway
├── Auth Service       /api/auth/**（登录、注册、当前用户）
├── Mistake Service    /api/mistakes/**, /api/dashboard/**
└── Payment Service    /api/payments/**

Mistake Service
├── PostgreSQL：错题、图片元数据、分析结果、掌握状态
├── Garage：原始图片二进制
└── RabbitMQ：异步分析任务，只传 mistakeId
```

Demo 阶段将用户资料放在 Auth Service、Dashboard 放在 Mistake Service，避免为了服务拆分过早增加复杂度。以后用户域扩展出头像、学校、年级、偏好设置等独立业务时，再考虑拆出 User Service。
