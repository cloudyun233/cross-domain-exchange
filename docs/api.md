# API 接口文档

跨域数据交换系统 REST API 完整接口文档。

> **基础路径**: `/api`
>
> **认证方式**: JWT Bearer Token（除 `/api/auth/login` 和 `/api/webhook/**` 外均需携带 `Authorization: Bearer <token>`）

---

## 一、认证接口 `/api/auth`

### POST `/api/auth/login`

用户登录，获取 JWT 令牌。

**请求体**:
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**响应**:
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "...",
    "refreshToken": "...",
    "expiresIn": 3600
  }
}
```

---

### POST `/api/auth/refresh`

刷新过期的 JWT 令牌。

**请求头**: `Authorization: Bearer <refresh_token>`

**响应**: 同登录接口

---

### GET `/api/auth/me`

获取当前登录用户的信息。

**请求头**: `Authorization: Bearer <access_token>`

---

## 二、状态检查 `/api/status`

### GET `/api/status/backend`

检查后端服务状态。

**响应**:
```json
{
  "status": "ok"
}
```

---

### GET `/api/status/emqx`

检查 EMQX Broker 连通性。

**响应**:
```json
{
  "status": "online"
}
```
或 `{"status": "offline"}`

---

## 三、安全域管理 `/api/domains`

### GET `/api/domains`

获取所有安全域列表。

---

### GET `/api/domains/tree`

获取安全域树形结构。

---

### GET `/api/domains/{id}`

获取指定安全域详情。

---

### POST `/api/domains`

创建新安全域。（需 ADMIN 角色）

**请求体**:
```json
{
  "parentId": null,
  "domainCode": "gov",
  "domainName": "政务域",
  "status": 1
}
```

---

### PUT `/api/domains/{id}`

更新安全域。（需 ADMIN 角色）

---

### DELETE `/api/domains/{id}`

删除安全域。（需 ADMIN 角色）

---

## 四、用户/客户端管理 `/api/clients`

> **权限要求**: ADMIN 角色

### GET `/api/clients`

获取所有用户列表（密码脱敏为 `***`）。

---

### GET `/api/clients/{id}`

获取指定用户详情（密码脱敏）。

---

### POST `/api/clients`

创建新用户。

**请求体**:
```json
{
  "username": "new_user",
  "passwordHash": "plaintext_password",
  "roleType": "producer",
  "domainId": 2
}
```

> 系统自动设置 `client_id = username + "_001"`，密码自动 BCrypt 加密。

---

### PUT `/api/clients/{id}`

更新用户信息。

---

### DELETE `/api/clients/{id}`

删除用户。

---

## 五、主题管理 `/api/topics`

### GET `/api/topics/tree`

获取基于安全域生成的主题树。

---

### POST `/api/topics/publish`

发布消息到指定主题。

**请求参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `topic` | String | 是 | - | 主题名（如 `cross_domain/medical/swh`） |
| `qos` | int | 否 | 1 | QoS 级别（0/1/2） |
| `retain` | boolean | 否 | false | 是否保留消息 |
| `format` | String | 否 | "structured" | 数据格式：json / xml / text / structured |

**请求体**: 消息内容（JSON/XML/TEXT 字符串）

**请求头**: `Authorization: Bearer <token>`

**响应**:
```json
{
  "code": 200,
  "message": "消息发布成功"
}
```

> **格式转换**: 当 `format=structured` 时自动检测内容（以 `<` 开头识别为 XML），XML 数据会被转换为 JSON 并附加 `_meta` 包装。

---

## 六、ACL 权限管理 `/api/acl-rules`

> **权限要求**: ADMIN 角色

### GET `/api/acl-rules`

获取所有 ACL 规则列表。

---

### GET `/api/acl-rules/username/{username}`

按用户名查询 ACL 规则。

---

### POST `/api/acl-rules`

创建 ACL 规则（创建后自动同步到 EMQX Broker）。

**请求体**:
```json
{
  "username": "producer_medical_swh",
  "topicFilter": "cross_domain/medical/swh",
  "action": "publish",
  "accessType": "allow"
}
```

---

### PUT `/api/acl-rules/{id}`

更新 ACL 规则（自动同步到 Broker）。

---

### DELETE `/api/acl-rules/{id}`

删除 ACL 规则（自动同步到 Broker）。

---

### POST `/api/acl-rules/sync`

全量同步所有 ACL 规则到 EMQX Broker。

---

## 七、审计日志 `/api/audit-logs`

> **权限要求**: ADMIN 角色

### GET `/api/audit-logs`

分页查询审计日志。

**请求参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | 否 | 1 | 页码 |
| `size` | int | 否 | 20 | 每页条数 |
| `clientId` | String | 否 | - | 客户端 ID 过滤 |
| `actionType` | String | 否 | - | 操作类型过滤 |

---

### GET `/api/audit-logs/export/pdf`

导出审计日志为 PDF 文件。

**请求参数**: 同列表查询（`clientId`、`actionType` 可选过滤）

**响应**: `application/pdf` 二进制文件，以 `audit-logs-yyyyMMddHHmmss.pdf` 命名下载。

---

## 八、系统监控 `/api/monitor`

> **权限要求**: ADMIN 角色

### GET `/api/monitor/metrics`

获取系统综合指标（TPS、连接数等）。

---

### GET `/api/monitor/message-stats`

获取消息流量统计。

---

### GET `/api/monitor/client-stats`

获取客户端连接统计。

---

### GET `/api/monitor/topic-stats`

获取主题流量统计。

---

### GET `/api/monitor/connection-status`

获取后端 MQTT 连接状态。

**响应**:
```json
{
  "connected": true,
  "protocol": "tls"
}
```

---

## 九、消息订阅 `/api/subscribe`

### GET `/api/subscribe/sse`

建立 Server-Sent Events 长连接。

**响应**: `text/event-stream` 事件流。

---

### POST `/api/subscribe/connect`

建立 MQTT 持久会话（cleanStart=false，支持离线消息补发）。

**请求头**: `Authorization: Bearer <token>`

---

### POST `/api/subscribe/topic`

订阅指定主题。

**请求参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `topic` | String | 是 | - | 主题名（支持通配符 + / #） |
| `qos` | int | 否 | 1 | QoS 级别 |

---

### POST `/api/subscribe/cancel`

取消订阅指定主题。

**请求参数**: `topic`（主题名）

---

### POST `/api/subscribe/disconnect`

仅断开 MQTT 连接，保留 SSE 和订阅记忆（用于触发离线消息缓存）。

---

### GET `/api/subscribe/session-status`

查询当前会话连接状态。

---

### POST `/api/subscribe/close`

完全关闭订阅会话（取消所有订阅 + 断开 MQTT + 关闭 SSE），通常在退出登录时调用。

---

## 十、弱网模拟 `/api/network`

> **权限要求**: ADMIN 角色

### GET `/api/network/presets`

获取弱网模拟预设方案列表。

**响应**:
```json
[
  {"name": "无限制", "delay": 0, "loss": 0, "bandwidth": 0, "description": "默认状态"},
  {"name": "标准网络", "delay": 10, "loss": 0, "bandwidth": 0, "description": "正常网络环境"},
  {"name": "政务跨域波动", "delay": 100, "loss": 5, "bandwidth": 10, "description": "跨域网络波动场景"},
  {"name": "普通弱网", "delay": 250, "loss": 15, "bandwidth": 2, "description": "中等弱网环境"},
  {"name": "极端弱网", "delay": 500, "loss": 30, "bandwidth": 1, "description": "极端网络条件"}
]
```

---

### POST `/api/network/simulate`

设置弱网模拟参数（基于 Linux tc 工具）。

**请求参数**:

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `delayMs` | int | 否 | 0 | 延迟（毫秒） |
| `lossPercent` | int | 否 | 0 | 丢包率（百分比） |
| `bandwidthMbps` | int | 否 | 0 | 带宽上限（Mbps） |

> 所有参数为 0 时重置网络。需在 Linux Docker 环境中运行。

---

## 十一、Webhook `/api/webhook`

### POST `/api/webhook/emqx`

接收 EMQX Broker 的 Webhook 回调事件。

> **免认证**: 此端点无需 JWT，由 EMQX 内部调用。

**支持的事件类型**: `client.connected`, `client.disconnected`, `message.publish`, `client.authorize`, `session.subscribed`, `message.delivered`

---

## 附录：HTTP 状态码

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 参数错误 / 格式不支持 |
| 401 | 未认证（Token 无效或过期） |
| 403 | 无权限（非 ADMIN 角色） |
| 500 | 服务器内部错误 |
| 502 | 消息发布失败（Broker 不可达） |
