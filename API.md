# 错题实验室 API 契约

建议后端统一前缀：`/api`。

## 通用约定

- 请求和响应使用 JSON，上传错题使用 `multipart/form-data`
- 登录成功返回 `token` 和 `user`
- 后续请求携带 `Authorization: Bearer <token>`
- 所有时间使用 ISO 8601
- 图片限制：PNG/JPG/WEBP，最大 10MB

## 认证

### `POST /api/auth/login`

请求：

```json
{"email":"demo@mistake.lab","password":"123456"}
```

响应：

```json
{"data":{"token":"jwt-token","user":{"id":"u1","name":"演示同学","email":"demo@mistake.lab","credits":3}}}
```

### `POST /api/auth/register`

注册页面提交字段：`name`、`email`、`password`、`passwordConfirmation`。

请求：

```json
{"name":"小林","email":"xiaolin@example.com","password":"123456","passwordConfirmation":"123456"}
```

后端需要校验邮箱格式、密码长度、两次密码一致，以及邮箱是否已注册。成功后返回与登录接口相同的 `token` 和 `user` 数据结构，并默认赠送 3 次分析额度。

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
