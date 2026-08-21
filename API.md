# 错题实验室 API 契约

建议后端统一前缀：`/api`。

## 通用约定

- 请求和响应使用 JSON，上传错题使用 `multipart/form-data`
- 登录成功返回 `token` 和 `user`
- 后续请求携带 `Authorization: Bearer <token>`
- 所有时间使用 ISO 8601
- 图片限制：PNG/JPG/WEBP，最大 10MB

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
LoginRequestDTO    ─┐
                    ├─> AuthController ─> ApiResponse<AuthVO>
RegisterRequestDTO ─┘                         ├─ token
                                             └─ UserVO
```

### `POST /api/auth/login`

- 请求 DTO：`LoginRequestDTO`
- 成功响应：`ApiResponse<AuthVO>`

#### `LoginRequestDTO`

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
    │   ├── LoginRequestDTO.java
    │   └── RegisterRequestDTO.java
    └── vo/
        ├── AuthVO.java
        └── UserVO.java
```

## 用户与额度

- `GET /api/me`
- `GET /api/entitlements`

额度响应：`{"data":{"credits":3}}`

## Dashboard

### `GET /api/dashboard/stats`

响应字段：`total`、`weeklyNew`、`averageAccuracy`、`pendingReview`、`typeCounts`、`accuracyTrend`。

## 错题

- `GET /api/mistakes`：当前用户错题列表
- `GET /api/mistakes/{id}`：错题详情
- `POST /api/mistakes`：创建错题并开始分析

`POST /api/mistakes` 的 multipart 字段：`title`、`subject`、`chapter`、`type`、`text`、`image`。

错题状态：`QUEUED`、`ANALYZING`、`COMPLETED`、`FAILED`。

详情中的 `analysis` 字段包含：`summary`、`knowledge[]`、`steps[]`、`suggestion`、`answer`。

## 支付演示

- `GET /api/payments/plans`
- `POST /api/payments/mock`

模拟支付请求：`{"planId":"starter","count":10}`。

正式支付时，支付成功必须由 Java 服务端接收支付平台回调后更新额度，前端只展示支付状态。
