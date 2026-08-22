# 错题实验室 API 契约

建议后端统一前缀：`/api`。

## 当前实现状态

| 模块 | 前端现状 | 后端现状 | 下一步 |
| --- | --- | --- | --- |
| 登录、注册 | 已调用真实 API | Auth Service 已实现，Gateway 已路由 | 保持现状 |
| 当前用户查询 | 启动受保护页面时调用真实 API，并刷新登录缓存 | Auth Service 已实现 `GET /api/auth/me` | 保持现状 |
| 资料修改 | 保存按钮无请求 | 未实现 | 后续实现 `PATCH /api/auth/me` |
| Dashboard | 从错题 Mock 计算，趋势和部分统计为硬编码 | 未实现 | 实现统计接口 |
| 错题列表、详情、上传 | 使用 `localStorage` Mock，图片保存为 Base64 | 未实现 | 新建 Mistake Service 并接入 Garage |
| 分析进度 | 前端定时器模拟状态变化 | 未实现 | 前端轮询详情接口，后端异步分析 |
| 标记已掌握 | 按钮无请求 | 未实现 | 实现掌握状态接口 |
| 额度、模拟支付 | 前端直接修改本地额度 | 未实现 | 额度只能由服务端变更 |

目前 Gateway 只配置了 `/api/auth/**` 路由。新增用户、错题、Dashboard 和支付服务后，必须同步增加 Gateway 路由和 OpenAPI 聚合配置。

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

| 字段 | Java 类型 | JSON 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `data` | `T` | object | 是 | 实际响应数据 |

```json
{
  "data": {}
}
```

失败响应统一使用 `ErrorVO`：

| 字段 | Java 类型 | JSON 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `code` | `String` | string | 是 | 稳定的业务错误码，供前端判断错误类型 |
| `message` | `String` | string | 是 | 可直接展示或记录的错误信息 |
| `fieldErrors` | `Map<String, String>` | object | 否 | 参数校验失败时，各字段对应的错误信息 |

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

| 字段 | Java 类型 | JSON 类型 | 必填 | 校验规则 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `email` | `String` | string | 是 | 非空、合法邮箱、最长 254 字符 | 登录邮箱；后端先去除首尾空格并转为小写 |
| `password` | `String` | string | 是 | 非空、6～64 字符 | 用户明文密码，仅用于本次认证 |

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

| 字段 | Java 类型 | JSON 类型 | 必填 | 校验规则 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `name` | `String` | string | 是 | 去除首尾空格后 1～30 字符 | 用户显示名称 |
| `email` | `String` | string | 是 | 非空、合法邮箱、最长 254 字符 | 注册邮箱；存储前去除首尾空格并转为小写 |
| `password` | `String` | string | 是 | 6～64 字符 | 明文密码；后端必须使用 BCrypt 后再保存 |
| `passwordConfirmation` | `String` | string | 是 | 必须与 `password` 完全一致 | 仅用于请求校验，不能写入数据库 |

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

| 字段 | Java 类型 | JSON 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `token` | `String` | string | 是 | JWT Access Token；请求其他接口时放入 `Authorization` 请求头 |
| `user` | `UserVO` | object | 是 | 当前登录用户的公开信息 |

#### `UserVO`

| 字段 | Java 类型 | JSON 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `String` | string | 是 | 用户唯一标识；UUID 在 JSON 中也序列化为字符串 |
| `name` | `String` | string | 是 | 用户显示名称 |
| `email` | `String` | string | 是 | 用户邮箱 |
| `credits` | `Integer` | number | 是 | 剩余可用分析次数；新用户默认为 3 |

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
    "credits": 3
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

## Dashboard

### `GET /api/dashboard/stats`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `days` | 否 | `7` | 正确率趋势天数，可选 7、14、30 |

响应：

```json
{
  "data": {
    "total": 28,
    "weeklyNew": 5,
    "averageAccuracy": 72,
    "pendingReview": 4,
    "totalChangePercent": 12,
    "accuracyChangePercent": 8,
    "typeCounts": [
      { "type": "概念不清", "count": 10 },
      { "type": "审题错误", "count": 7 }
    ],
    "accuracyTrend": [
      { "date": "2026-08-16", "accuracy": 61 },
      { "date": "2026-08-17", "accuracy": 68 }
    ]
  }
}
```

- 百分比字段是 `0～100` 的整数，不带 `%`。
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

| 状态 | 含义 |
| --- | --- |
| `queued` | 已创建并进入分析队列 |
| `analyzing` | OCR 或 AI 分析中 |
| `completed` | 分析成功，可展示 `analysis` |
| `failed` | 分析失败，可展示 `failureMessage` |

### `GET /api/mistakes`

返回当前用户的错题分页列表。

| 查询参数 | 必填 | 说明 |
| --- | --- | --- |
| `keyword` | 否 | 匹配题目名称、学科、章节或知识点 |
| `subject` | 否 | 精确筛选学科 |
| `status` | 否 | 按分析状态筛选 |
| `mastered` | 否 | `true` 或 `false` |
| `page` | 否 | 从 0 开始，默认 0 |
| `size` | 否 | 默认 20，最大 100 |
| `sort` | 否 | 默认 `createdAt,desc` |

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
- `confidence` 是 `0～100` 的整数；当前前端写死的 94% 应改为读取该字段。

### `POST /api/mistakes`

创建错题、扣减 1 次额度并开始异步分析。请求类型为 `multipart/form-data`。

| 字段 | 类型 | 必填 | 校验规则 |
| --- | --- | --- | --- |
| `title` | string | 否 | 最长 100；为空时由题目文字生成或使用“未命名错题” |
| `subject` | string | 是 | 1～30 字符 |
| `chapter` | string | 否 | 最长 100 |
| `type` | string | 是 | 1～30 字符 |
| `text` | string | 条件必填 | 最长 10000；`text` 和 `image` 至少提供一个 |
| `userAnswer` | string | 否 | 最长 10000 |
| `image` | file | 条件必填 | PNG/JPEG/WEBP，最大 10MB；与 `text` 至少提供一个 |

后端必须检查文件真实内容，不能只信扩展名或客户端提供的 MIME。图片上传 Garage 成功后，错题记录、额度扣减和待发布事件在同一数据库事务中提交，再由 Outbox 发布器发送 RabbitMQ 分析消息；消息只传 `mistakeId`，不传图片二进制。这样 RabbitMQ 暂时不可用时不会出现已扣额度但任务永久丢失的问题。

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

| HTTP | 错误码 | 场景 |
| --- | --- | --- |
| 400 | `MISTAKE_CONTENT_REQUIRED` | 图片和题目文字均为空 |
| 402 | `INSUFFICIENT_CREDITS` | 分析额度不足 |
| 413 | `IMAGE_TOO_LARGE` | 图片超过 10MB |
| 415 | `UNSUPPORTED_IMAGE_TYPE` | 图片格式不支持或文件签名不匹配 |
| 503 | `STORAGE_UNAVAILABLE` | Garage 暂时不可用，不能扣减额度 |

前端进入详情页后，每 2 秒调用一次 `GET /api/mistakes/{id}`；状态变为 `completed` 或 `failed` 后停止轮询。后续如需降低轮询开销，再增加 SSE，不作为第一版必需项。

### `PATCH /api/mistakes/{id}/mastery`

标记或取消标记“已掌握”。

请求：

```json
{
  "mastered": true
}
```

成功返回更新后的 `ApiResponse<MistakeSummaryVO>`。只有 `completed` 状态允许标记，否则返回 `409 ANALYSIS_NOT_COMPLETED`。

### 图片存储说明

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
6. 详情页的“置信度 94%”是硬编码，“标记为已掌握”按钮没有请求，需要接入详情字段和 mastery 接口。
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
