# Chat2Response API 文档

本文档面向前端，描述当前后端已实现的全部接口（含后台登录鉴权）。

- 基础地址：`http://<host>:<port>`
- 默认端口：`8080`
- 数据格式：`application/json`
- 字符集：`UTF-8`

## 1. 总体说明

系统分为两类接口：

1. 管理后台接口：管理“转换项目”（配置上游 BaseURL/APIKEY/模型，生成可对外使用的 targetModel）。
2. 网关接口：对外暴露兼容 OpenAI Responses API 的 `/v1/responses`。

核心约束：

- 每个转换项目创建后会自动生成一个全局唯一的 `targetModel`。
- 网关调用必须同时满足：
  - `Authorization: Bearer <apiKey>`
  - 请求体 `model=<targetModel>`
- 只有启用状态（`enabled=true`）的项目可被调用。
- **管理后台接口（`/admin/projects/**`）必须先登录**，并携带后台登录 Token。

## 2. 统一错误响应

除非上游透传错误（见 5.4），本服务错误统一结构如下：

```json
{
  "error": {
    "type": "invalid_request_error",
    "message": "错误描述"
  },
  "timestamp": 1714180000
}
```

### 2.1 错误类型与 HTTP 状态

- `400 Bad Request`
  - `type=invalid_request_error`
  - 常见场景：参数校验失败、Authorization 格式错误、model 为空、项目不存在。
- `401 Unauthorized`
  - `type=unauthorized_error`
  - 常见场景：未登录、Token 无效、Token 过期、登录用户名密码错误。
- `409 Conflict`
  - `type=conflict_error`
  - 常见场景：项目已停用、调用上游失败（服务内部抛出状态异常）。
- `500 Internal Server Error`
  - `type=internal_error`
  - 常见场景：未捕获异常。

## 3. 管理后台登录认证接口

前缀：`/admin/auth`

> 登录成功后，前端请保存 `token`，并在后续后台请求头中携带：
> `Authorization: Bearer <token>`

### 3.1 登录

- 方法：`POST`
- 路径：`/admin/auth/login`

请求体：

```json
{
  "username": "admin",
  "password": "admin123456"
}
```

字段：

- `username` string 必填，最大 64。
- `password` string 必填，最大 128。

成功响应（200）：

```json
{
  "token": "<LOGIN_TOKEN>",
  "tokenType": "Bearer",
  "expiresAt": 1714266400,
  "username": "admin"
}
```

说明：

- `expiresAt` 为 Unix 时间戳（秒）。
- 当前 Token 有效期 24 小时。

失败（401）示例：

```json
{
  "error": {
    "type": "unauthorized_error",
    "message": "用户名或密码错误"
  },
  "timestamp": 1714180000
}
```

### 3.2 当前登录信息

- 方法：`GET`
- 路径：`/admin/auth/me`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

成功响应（200）：

```json
{
  "username": "admin"
}
```

### 3.3 登出

- 方法：`POST`
- 路径：`/admin/auth/logout`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

成功响应（200）：空响应体。

## 4. 管理后台项目接口

前缀：`/admin/projects`

> 除 `POST /admin/auth/login` 外，所有 `/admin/**` 均需登录 Token。

### 4.1 新增转换项目

- 方法：`POST`
- 路径：`/admin/projects`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

请求体：

```json
{
  "baseUrl": "https://api.openai.com",
  "apiKey": "sk-xxxx",
  "sourceModel": "gpt-4o-mini"
}
```

字段说明：

- `baseUrl` string 必填，最大 500。
- `apiKey` string 必填，最大 512。
- `sourceModel` string 必填，最大 255。

成功响应（200）：

```json
{
  "id": 1,
  "baseUrl": "https://api.openai.com",
  "apiKey": "***xxxx",
  "sourceModel": "gpt-4o-mini",
  "targetModel": "rsp-gpt-4o-mini-a1b2c3d4",
  "enabled": true
}
```

说明：

- `targetModel` 自动生成且唯一。
- 返回中的 `apiKey` 为掩码形式（仅显示末尾最多4位）。

### 4.2 查询项目列表

- 方法：`GET`
- 路径：`/admin/projects`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

成功响应（200）：

```json
[
  {
    "id": 1,
    "baseUrl": "https://api.openai.com",
    "apiKey": "***xxxx",
    "sourceModel": "gpt-4o-mini",
    "targetModel": "rsp-gpt-4o-mini-a1b2c3d4",
    "enabled": true
  }
]
```

### 4.3 查询单个项目

- 方法：`GET`
- 路径：`/admin/projects/{id}`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

路径参数：

- `id` long 必填

成功响应（200）：同 4.1。

失败（400）示例：

```json
{
  "error": {
    "type": "invalid_request_error",
    "message": "项目不存在: 999"
  },
  "timestamp": 1714180000
}
```

### 4.4 启停项目

- 方法：`PATCH`
- 路径：`/admin/projects/{id}/enabled?enabled=true|false`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

参数：

- `id` long 必填（path）
- `enabled` boolean 必填（query）

示例：

`PATCH /admin/projects/1/enabled?enabled=false`

成功响应（200）：同 4.1。

### 4.5 删除项目

- 方法：`DELETE`
- 路径：`/admin/projects/{id}`
- Header：`Authorization: Bearer <LOGIN_TOKEN>`

参数：

- `id` long 必填

成功响应（200）：空响应体。

## 5. 网关接口（Responses API）

前缀：`/v1`

### 5.1 创建响应

- 方法：`POST`
- 路径：`/v1/responses`
- Header：`Authorization: Bearer <apiKey>`（这里是项目 apiKey，不是后台登录 token）

请求体（示例）：

```json
{
  "model": "rsp-gpt-4o-mini-a1b2c3d4",
  "input": "你好，介绍一下你自己",
  "instructions": "你是一个简洁的助手",
  "temperature": 0.7,
  "top_p": 1,
  "presence_penalty": 0,
  "frequency_penalty": 0,
  "max_output_tokens": 512,
  "stream": false
}
```

请求字段支持情况：

- `model` string 必填，必须是转换项目生成的 `targetModel`。
- `input` 支持：
  - string
  - array（元素可为 string 或对象）
- `instructions` string 可选，会映射为 Chat Completions 的 system message。
- 透传到上游 Chat Completions：
  - `temperature`
  - `top_p`
  - `presence_penalty`
  - `frequency_penalty`
  - `max_output_tokens` -> 映射为 `max_tokens`
- `stream`
  - `false`（默认）：返回完整 JSON 响应
  - `true`：返回 `text/event-stream` 格式的 SSE 事件流

### 5.2 `input` 兼容格式说明

#### 格式A：字符串

```json
{
  "model": "rsp-xxx",
  "input": "写一句广告语"
}
```

会转为上游 messages：

```json
[
  {"role": "user", "content": "写一句广告语"}
]
```

#### 格式B：数组

```json
{
  "model": "rsp-xxx",
  "input": [
    "先看这段上下文",
    {
      "role": "user",
      "content": [
        {"type": "input_text", "text": "再总结一下"}
      ]
    }
  ]
}
```

说明：

- 数组里的 `string` 会转成 `role=user` 的消息。
- 对象项：
  - `role` 为空时默认 `user`
  - `content` 为数组时，会拼接每个 segment 的 `text`

### 5.3 成功响应（Responses 风格）

```json
{
  "id": "resp_chatcmpl_abc123",
  "object": "response",
  "created_at": 1714180000,
  "status": "completed",
  "model": "rsp-gpt-4o-mini-a1b2c3d4",
  "output": [
    {
      "id": "msg_chatcmpl_abc123",
      "type": "message",
      "status": "completed",
      "role": "assistant",
      "content": [
        {
          "type": "output_text",
          "text": "你好，我是一个AI助手。"
        }
      ]
    }
  ],
  "output_text": "你好，我是一个AI助手。",
  "usage": {
    "input_tokens": 10,
    "output_tokens": 20,
    "total_tokens": 30
  }
}
```

### 5.4 上游错误透传

当上游 `/v1/chat/completions` 返回 HTTP >= 400 时，当前服务返回：

```json
{
  "error": {
    "type": "upstream_error",
    "code": 401,
    "message": "<上游原始响应体字符串>"
  }
}
```

注意：

- 该结构是网关内部封装，不包含统一错误中的 `timestamp`。
- HTTP 状态码当前由控制器返回 200（响应体中标识了上游错误码）。前端需根据 `error` 字段做失败分支。

### 5.5 流式响应（SSE）

当请求 `"stream": true` 时，响应头为 `Content-Type: text/event-stream`，返回标准 SSE 事件流。

事件顺序：

```
event: response.created
data: {"id":"resp_xxx","object":"response","created_at":1714180000,"status":"in_progress","model":"rsp-xxx","output":[]}

event: response.output_item.added
data: {"id":"msg_xxx","type":"message","status":"in_progress","role":"assistant","content":[]}

event: response.content_part.added
data: {"type":"output_text","text":""}

event: response.output_text.delta
data: {"delta":"你好"}

event: response.output_text.delta
data: {"delta":"，我是AI助手。"}

event: response.content_part.done
data: {"type":"output_text","text":"你好，我是AI助手。"}

event: response.output_item.done
data: {"id":"msg_xxx","type":"message","status":"completed","role":"assistant","content":[{"type":"output_text","text":"你好，我是AI助手。"}]}

event: response.completed
data: {"id":"resp_xxx","object":"response","created_at":1714180000,"status":"completed","model":"rsp-xxx","output":[{"id":"msg_xxx","type":"message","status":"completed","role":"assistant","content":[{"type":"output_text","text":"你好，我是AI助手。"}]}],"output_text":"你好，我是AI助手。"}
```

前端处理建议：

- 使用 `EventSource` 或 `fetch` + `ReadableStream` 读取 SSE 流。
- 监听 `response.output_text.delta` 事件，将 `delta` 字段追加到已输出文本。
- 监听 `response.completed` 事件，表示流式输出结束。

## 6. 前端对接建议

1. 后台登录后保存 `LOGIN_TOKEN`（建议本地存储并设置过期处理）。
2. 请求 `/admin/**` 时使用 `Authorization: Bearer <LOGIN_TOKEN>`。
3. 请求 `/v1/responses` 时使用 `Authorization: Bearer <PROJECT_API_KEY>`，不要混用登录 token。
4. 对 `/v1/responses` 响应统一判断：
   - `stream=false`：有 `error` 字段 -> 失败，无 `error` 字段 -> 按成功结构读取 `output_text`
   - `stream=true`：返回 SSE 事件流，监听 `response.output_text.delta` 获取增量文本

## 7. 快速联调示例

### 7.1 后台登录

```bash
curl -X POST 'http://localhost:8080/admin/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "admin123456"
  }'
```

### 7.2 新增项目（需要登录 token）

```bash
curl -X POST 'http://localhost:8080/admin/projects' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <LOGIN_TOKEN>' \
  -d '{
    "baseUrl": "https://api.openai.com",
    "apiKey": "sk-xxxx",
    "sourceModel": "gpt-4o-mini"
  }'
```

### 7.3 调用 responses（非流式）

```bash
curl -X POST 'http://localhost:8080/v1/responses' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer sk-xxxx' \
  -d '{
    "model": "rsp-gpt-4o-mini-a1b2c3d4",
    "input": "写一段欢迎词",
    "stream": false
  }'
```

### 7.4 调用 responses（流式 SSE）

```bash
curl -N -X POST 'http://localhost:8080/v1/responses' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer sk-xxxx' \
  -d '{
    "model": "rsp-gpt-4o-mini-a1b2c3d4",
    "input": "写一段欢迎词",
    "stream": true
  }'
```
