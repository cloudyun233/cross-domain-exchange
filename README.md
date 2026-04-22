# 跨域数据交换系统

基于发布-订阅（Publish/Subscribe）机制的数据跨域交换原型系统，利用 MQTT 协议实现不同安全域之间的异步解耦数据流通。

## 项目背景

本系统是本科毕业论文《基于发布订阅机制的数据跨域交换方法》的原型实现，针对跨域数据交换场景中的"数据孤岛"问题，采用 MQTT 主题订阅机制实现"一处发布、多处订阅"的灵活跨域数据共享。

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        表现层                                    │
│              React + Ant Design + ECharts                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      业务逻辑层                                  │
│         Spring Boot 3 + Spring Security + JWT                            │
│         MyBatis-Plus + HiveMQ Client                                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      消息引擎层                                  │
│                  EMQX MQTT Broker                               │
│           (ACL + QoS + TLS/TCP 传输)                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      基础设施层                                  │
│                   Docker 容器化部署                              │
│              Linux Traffic Control (弱网模拟)                    │
└─────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层次       | 技术选型                         | 说明                       |
| -------- | ---------------------------- | ------------------------ |
| 消息引擎     | EMQX Enterprise 5.10.3       | MQTT Broker，支持 ACL 权限热更新 |
| 后端框架     | Spring Boot 3.5.13 + Java 17 | 业务逻辑处理、REST API          |
| 安全认证     | Spring Security + JWT        | 无状态身份认证                  |
| 数据持久化    | MyBatis-Plus 3.5.5           | 用户、权限、审计数据管理             |
| MQTT 客户端 | HiveMQ MQTT Client 1.3       | MQTT 5.0 客户端（发布/订阅）      |
| 前端框架     | React 18 + TypeScript        | 单页应用                     |
| UI 组件    | Ant Design v5                | 企业级 UI 组件库               |
| 可视化      | ECharts 5                    | 监控数据可视化                  |
| 数据库      | H2 (开发) / MySQL (生产)         | 元数据存储                    |
| 容器化      | Docker + Docker Compose      | 一键部署                     |

<br />

## 数据模型

### 安全域结构

系统支持多层父子域结构：

- `medical` 医疗域
  - `medical/swh` 西南医院
- `gov` 政务域
- `enterprise` 企业域

## 演示账号

| 用户名                    | 密码         | 角色  | 所属域      | 权限                               |
| ---------------------- | ---------- | --- | -------- | -------------------------------- |
| `admin`                | `admin123` | 管理员 | 全域       | 订阅 `/cross_domain/#`             |
| `producer_medical_swh` | `123456`   | 生产者 | 医疗域/西南医院 | 发布 `/cross_domain/medical/swh/#` |
| `consumer_social`      | `123456`   | 消费者 | 政务域      | 订阅 `/cross_domain/medical/#`     |
| `consumer_medical_swh` | `123456`   | 消费者 | 医疗域/西南医院 | 订阅 `/cross_domain/medical/swh/#` |

## 功能模块

| 模块       | 说明                                    |
| -------- | ------------------------------------- |
| **连接管理** | 系统状态检查（后端/EMQX）、连接状态监控                |
| **消息发布** | 授权生产者向指定主题发布消息，支持 JSON/XML/TEXT 格式    |
| **消息订阅** | SSE + MQTT 双通道订阅，支持持久会话（离线消息补发）、通配符匹配 |
| **权限管理** | ACL 规则配置与同步，细粒度控制发布/订阅权限              |
| **审计日志** | 记录所有连接、发布、订阅、权限校验行为                   |
| **系统监控** | 消息统计、客户统计、主题统计、可视化图表                  |
| **域管理**  | 安全域的增删改查，支持父子域配置                      |
| **弱网模拟** | Traffic Control 弱网环境配置（延迟、丢包、带宽）      |

## 部署说明

### 环境要求

- Docker Engine 24.x + Docker Compose v2.x
- Node.js 18+ (前端本地开发)
- Java 17+ (后端本地开发)

### 方式一：Docker 一键部署（推荐）

从 [GitHub Releases](https://github.com/) 下载编译产物，放入 `dockerrun` 目录后一键启动。

```bash
# 1. 下载编译产物并放置到指定位置
#    - 后端 jar → dockerrun/backend/cross-domain-exchange-1.0.0.jar
#    - 前端 dist → dockerrun/frontend/dist/

# 2. 启动全部服务（EMQX + 后端 + 前端）
cd dockerrun
docker-compose up -d --build

# 访问地址
# - 前端管理控制台：http://localhost:80
# - EMQX Dashboard：http://localhost:18083
#
# 默认账号信息：
# - EMQX Dashboard: admin / admin123
#
# EMQX API 认证信息（用于后端连接）：
# - API Key: c7ad44cbad762a5d
# - Secret Key: DWNy1WHuNZh9BdxKBCCRfMBZktzawB4T5bFPUC3h8UHL
```

> **说明：** 编译产物需手动放置到 `dockerrun` 目录，目录结构如下：
> ```
> dockerrun/
> ├── backend/
> │   ├── Dockerfile
> │   └── cross-domain-exchange-1.0.0.jar  ← 从 Release 下载放置
> ├── frontend/
> │   ├── Dockerfile
> │   ├── nginx.conf
> │   └── dist/            ← 从 Release 下载放置
> ├── emqx/
> │   └── emqx.conf
> └── docker-compose.yml
> ```

### 方式二：本地开发模式

#### 1. 启动 EMQX

```bash
cd dockerrun
docker-compose up -d emqx
```

#### 2. 编译并启动后端

```bash
cd backend
# 编译打包
./mvnw.cmd package -DskipTests
# 启动打包后的 jar 文件
java -jar target/cross-domain-exchange-1.0.0.jar
```

#### 3. 编译并启动前端

```bash
cd frontend
# 安装依赖并编译打包
npm install
npm run build
# 使用 npx serve 启动打包后的 dist 目录
npx serve dist -p 5173
```

### 数据库初始化

系统使用 H2 内存数据库，初始化脚本：

- 初始化脚本：[backend/src/main/resources/db/schema.sql](backend/src/main/resources/db/schema.sql)
- 演示数据：[backend/src/main/resources/db/data.sql](backend/src/main/resources/db/data.sql)

如需切换至 MySQL，参考：[docs/migration-to-mysql.md](docs/migration-to-mysql.md)

## 系统演示

### 典型跨域数据交换流程

1. **生产者发布数据**
   - 使用 `producer_medical_swh` 账号登录
   - 选择目标域和主题
   - 填写消息内容（支持 JSON/XML/TEXT）
   - 选择 QoS 级别并发布
2. **消费者订阅数据**
   - 使用 `consumer_social` 或 `consumer_medical_swh` 账号登录
   - 选择感兴趣的主题（支持通配符订阅）
   - 实时接收跨域消息
   - 可断开重连测试持久会话离线消息补发
3. **管理员监控**
   - 使用 `admin` 账号登录
   - 查看全域消息流量统计
   - 管理 ACL 权限规则
   - 审计操作日志
   - 配置弱网模拟参数

## 项目结构

```
cross-domain-exchange/
├── backend/                    # Spring Boot 后端
│   └── src/main/
│       ├── java/com/cde/
│       │   ├── config/        # 配置类
│       │   ├── controller/    # REST API 控制器
│       │   ├── dto/           # 数据传输对象
│       │   ├── entity/        # 数据库实体
│       │   ├── mapper/        # MyBatis Mapper
│       │   ├── mqtt/          # MQTT 客户端服务
│       │   ├── security/      # JWT 安全认证
│       │   └── service/       # 业务逻辑服务
│       └── resources/
│           ├── db/            # H2 数据库脚本
│           └── application*.yml  # 多环境配置
├── frontend/                  # React 前端
│   └── src/
│       ├── pages/            # 页面组件
│       ├── contexts/         # React Context
│       ├── components/       # 公共组件
│       └── services/         # API 服务
├── dockerrun/                 # Docker 部署目录
│   ├── backend/              # 后端 Dockerfile + 编译产物
│   ├── frontend/             # 前端 Dockerfile + nginx + 编译产物
│   ├── emqx/                 # EMQX 配置
│   └── docker-compose.yml    # 容器编排
├── docs/                     # 文档
└── README.md
```

<br />

## 功能实现核对表

以下表格对照论文 3.2 节功能需求，逐项核对原型系统实现情况：

### 一、核心功能模块

#### 1. 连接与身份管理

| 功能点                                                  | 状态    | 说明                                                                                                                                                                                                             |
| ---------------------------------------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 多模式身份认证（用户名/密码+JWT）                                  | ✅ 已实现 | [JwtUtil.java](backend/src/main/java/com/cde/security/JwtUtil.java)、[AuthController.java](backend/src/main/java/com/cde/controller/AuthController.java)                                                        |
| JWT 无状态令牌签发（含 client\_id, domainCode, roleType, exp） | ✅ 已实现 | [JwtUtil.java:39-53](backend/src/main/java/com/cde/security/JwtUtil.java#L39-L53)                                                                                                                              |
| 心跳与状态维持（PINGREQ/PINGRESP，心跳周期可配）                     | ✅ 已实现 | [MqttClientService.java:307](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L307) keepAlive(60)，HiveMQ 客户端自动处理                                                                                      |
| 持久会话支持（cleanStart=false，保留订阅与离线消息）                   | ✅ 已实现 | [MqttClientService.java:305](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L305) cleanStart(false) + sessionExpiryInterval(3600)；[emqx.conf:146](emqx/emqx.conf#L146) session\_expiry\_interval=2h |
| 遗嘱消息（Will Message）                                   | ✅ 已实现 | [MqttClientService.java:308-313](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L308-L313) 异常断连时自动发布 `will/{clientId}`                                                                              |

#### 2. 消息交换与路由

| 功能点                                           | 状态      | 说明                                                                                                                                                                                                                                                                                                             |
| --------------------------------------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 跨域主题管理（/cross\_domain/{域}/{子域}/{业务}/{数据}）     | ⚠️ 部分实现 | 种子数据遵循规范（[data.sql](backend/src/main/resources/db/data.sql)），发布接口未强制校验 `/cross_domain/` 前缀                                                                                                                                                                                                                     |
| 消息发布（Topic、Payload、QoS、retain）                | ✅ 已实现   | [TopicController.java](backend/src/main/java/com/cde/controller/TopicController.java)、[MqttClientService.java:369-386](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L369-L386) 支持 QoS 0/1/2 + retain                                                                                              |
| MQTT 5.0 特性：消息 TTL（message\_expiry\_interval） | ✅ 已实现   | [emqx.conf:155](emqx/emqx.conf#L155) message\_expiry\_interval=48h，EMQX 自动清理过期消息                                                                                                                                                                                                                               |
| MQTT 5.0 特性：共享订阅 / 用户属性                       | ⚠️ 协议支持 | EMQX Broker 层面已支持 MQTT 5.0 共享订阅（$share/{group}/topic）和用户属性，后端 HiveMQ Client 具备协议层支持，但因原型系统所以无实现计划                                                                                                                                                                                                              |
| 主题订阅/取消订阅（精确、+单层、#多层通配）                       | ✅ 已实现   | [MqttTopicUtil.java](backend/src/main/java/com/cde/util/MqttTopicUtil.java) 实现通配符匹配                                                                                                                                                                                                                            |
| 消息生命周期管理（持久化、TTL、死信队列）                        | ⚠️ 部分实现 | EMQX 原生不支持 死信队列：EMQX 是 MQTT Broker，不是消息队列（如 RabbitMQ/Kafka）。消息过期或投递失败时，EMQX 只会丢弃，不会自动转移。                                                                                                                                                                                                                       |
| SSE 实时推送（前端监控链路）                              | ✅ 已实现   | [SubscribeServiceImpl.java:38-41](backend/src/main/java/com/cde/service/impl/SubscribeServiceImpl.java#L38-L41) openSse 创建 SseEmitter；[SubscribeController.java](backend/src/main/java/com/cde/controller/SubscribeController.java) /api/subscribe/sse 端点，前端 EventSource 对接 5 秒快照窗口推送                          |
| 异常数据拦截与 NACK 反馈                               | ✅ 已实现   | [TopicController.java:71-99](backend/src/main/java/com/cde/controller/TopicController.java#L71-L99) convertPayload 中格式转换失败时抛出 BusinessException(HttpStatus.BAD\_REQUEST)，前端收到 400 响应；[AuditServiceImpl.java:81-86](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java#L81-L86) ACL deny 事件写入审计日志 |

#### 3. 传输保障与弱网优化

| 功能点                 | 状态      | 说明                                                                                                                                           |
| ------------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| QoS 分级保障（QoS 0/1/2） | ✅ 已实现   | [MqttClientService.java:379](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L379) MqttQos.fromCode(qos)                           |
| 智能重试与指数退避           | ⚠️ 部分实现 | [application.yml](backend/src/main/resources/application.yml) 已配置参数，但依赖 HiveMQ 默认行为，非自定义指数退避                                                 |
| 磁盘级持久化（防宕机丢失）       | ✅ 已实现   | [emqx.conf:173-176](emqx/emqx.conf#L173-L176) durable\_sessions.enable=true（EMQX 企业版内置）                                                      |
| 离线消息自动补发（重连后断点续传）   | ✅ 已实现   | [SubscribeServiceImpl.java:132-149](backend/src/main/java/com/cde/service/impl/SubscribeServiceImpl.java#L132-L149) connectSession 实现重连+重新订阅 |

#### 4. 安全控制与访问隔离

| 功能点                      | 状态      | 说明                                                                                                                                                                                                                                                                                                                                       |
| ------------------------ | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 端到端加密传输（TLS）             | ⚠️ 部分实现 | [MqttClientService.java:318-337](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L318-L337) 支持 TLS，但失败时会回退 TCP，预期设计                                                                                                                                                                                                            |
| QUIC 扩展                  | ❌ 未实现   | [emqx.conf:68-83](emqx/emqx.conf#L68-L83) 已配置监听器，后端客户端未使用 QUIC，nanosdk-java不成熟                                                                                                                                                                                                                                                           |
| 细粒度 ACL 控制（按客户端/域/操作/主题） | ✅ 已实现   | [AclServiceImpl.java](backend/src/main/java/com/cde/service/impl/AclServiceImpl.java) 完整 CRUD + 推送 EMQX                                                                                                                                                                                                                                  |
| ACL 动态热更新（无需重启）          | ✅ 已实现   | [AclServiceImpl.java:38-42](backend/src/main/java/com/cde/service/impl/AclServiceImpl.java#L38-L42) 创建后立即推送                                                                                                                                                                                                                              |
| 默认拒绝与越权拦截（Deny All）      | ✅ 已实现   | [emqx.conf:215](emqx/emqx.conf#L215) no\_match=deny；[data.sql:25](backend/src/main/resources/db/data.sql#L25) ('\*', '#', 'all', 'deny')                                                                                                                                                                                                 |
| 越权/测试数据拦截后 Webhook 审计联动  | ✅ 已实现   | [emqx.conf:215](emqx/emqx.conf#L215) no\_match=deny；[AuditServiceImpl.java:79-86](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java#L79-L86) client.authorize 事件中 result=deny 时记录 actionType=acl\_deny 并落库；[WebhookController.java](backend/src/main/java/com/cde/controller/WebhookController.java) 接收 EMQX acl\_deny 事件 |

#### 5. 监控审计与可视化

| 功能点                     | 状态      | 说明                                                                                                                                                                                                  |
| ----------------------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 实时流量监控（TPS、连接数、资源）      | ✅ 已实现   | [MonitorServiceImpl.java](backend/src/main/java/com/cde/service/impl/MonitorServiceImpl.java) @Scheduled 定时拉取；[Dashboard.tsx](frontend/src/pages/Dashboard.tsx) ECharts 折线图                         |
| 跨域拓扑展示（ECharts 力导向图）    | ✅ 已实现   | [Dashboard.tsx:159-213](frontend/src/pages/Dashboard.tsx#L159-L213) 支持拖拽/缩放/颜色区分                                                                                                                    |
| 全链路审计日志（连接/认证/发布/订阅/权限） | ✅ 已实现   | [AuditServiceImpl.java](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java) 接收 EMQX Webhook；[WebhookController.java](backend/src/main/java/com/cde/controller/WebhookController.java) |
| 日志查询与追溯（条件过滤+导出）        | ⚠️ 部分实现 | [AuditController.java](backend/src/main/java/com/cde/controller/AuditController.java) 支持分页过滤；**未实现 CSV/PDF 导出**                                                                                     |

### 二、跨模块支撑与扩展功能

| 功能点                              | 状态    | 说明                                                                                                                                                                                                                  |
| -------------------------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 异构数据格式转换（XML/JSON/TEXT）          | ✅ 已实现 | [XmlDataConverter.java](backend/src/main/java/com/cde/service/converter/XmlDataConverter.java) XML→JSON；[TopicController.java:71-99](backend/src/main/java/com/cde/controller/TopicController.java#L71-L99) 拦截器动态转换 |
| 二进制自定义协议转换                       | ❌ 未实现 | 未实现二进制协议解析器                                                                                                                                                                                                         |
| JSON Schema 校验                   | ❌ 未实现 | 数据格式转换后未进行 Schema 校验                                                                                                                                                                                                |
| 安全域树形管理（多级域自关联）                  | ✅ 已实现 | [SysDomain.java](backend/src/main/java/com/cde/entity/SysDomain.java) parentId 自关联；[DomainController.java](backend/src/main/java/com/cde/controller/DomainController.java) 支持 CRUD                                  |
| 用户/角色管理（role\_type + client\_id） | ✅ 已实现 | [ClientController.java](backend/src/main/java/com/cde/controller/ClientController.java) 用户 CRUD；[SysUser.java](backend/src/main/java/com/cde/entity/SysUser.java) 字段完整                                              |
| 容器化部署（Docker 一键部署）      | ✅ 已实现 | [dockerrun/docker-compose.yml](dockerrun/docker-compose.yml) 编排 EMQX+Backend+Frontend；从 GitHub Release 下载编译产物后一键启动 |
| 弱网模拟预设（Linux tc 工具，5 种场景）        | ✅ 已实现 | [NetworkController.java](backend/src/main/java/com/cde/controller/NetworkController.java) 支持无限制/标准/政务波动/普通弱网/极端弱网                                                                                                   |
| 数据库双模兼容（H2 开发/MySQL 生产）          | ✅ 已实现 | [application.yml](backend/src/main/resources/application.yml) H2 (MODE=MySQL)；[application-mysql.yml](backend/src/main/resources/application-mysql.yml) MySQL 8.0                                                   |
| EMQX 集群高可用                       | ❌ 未实现 | 非企业级仅支持单节点 EMQX                                                                                                                                                                                                     |

### 四、非功能与易用性验证

| 功能点      | 状态     | 说明                                                                                                                                                       |
| -------- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 万级并发扩展潜力 | ⚠️ 未压测 | 原型未做 JMeter 压测。EMQX 官方基准（单节点支持 10M 连接，集群支持 100M 连接）+ Docker 资源限制配置表明系统具备扩展潜力                                                                             |
| 可视化管控界面  | ✅ 已实现  | [Dashboard.tsx](frontend/src/pages/Dashboard.tsx) ECharts 折线图/力导向图；[AclManage.tsx](frontend/src/pages/AclManage.tsx) ACL 规则图形化配置；Ant Design v5 企业级 UI 组件 |

### 五、主动安全与高级特性

| 功能点          | 状态    | 说明                                                                        |
| ------------ | ----- | ------------------------------------------------------------------------- |
| 异常行为检测与自动处置  | ❌ 未实现 | 论文 3.3.1 节设计。系统仅记录审计日志，无实时异常检测（高频发布/超大消息/批量探测主题/异常心跳频率）、限流、临时封禁等自动响应能力    |
| 多协议网关适配      | ❌ 未实现 | 论文 3.3.3 节设计。当前仅支持原生 MQTT 客户端，未提供 HTTP/WebSocket/AMQP 等协议的转换网关，遗留系统无法平滑接入 |
| 审计日志防篡改与留存周期 | ❌ 未实现 | 论文 3.3.1/3.3.4 节设计。日志存储于普通数据库表，管理员可随意修改删除，无数字签名、只读存储或归档策略，无法满足监管合规与溯源要求   |

***

## API 接口

| 接口                               | 方法             | 说明               |
| -------------------------------- | -------------- | ---------------- |
| `/api/auth/login`                | POST           | 用户登录             |
| `/api/auth/refresh`              | POST           | 刷新令牌             |
| `/api/auth/me`                   | GET            | 获取当前用户信息         |
| `/api/status/backend`            | GET            | 后端服务状态           |
| `/api/status/emqx`               | GET            | EMQX 服务状态        |
| `/api/domains`                   | GET/POST       | 安全域列表/创建         |
| `/api/domains/tree`              | GET            | 安全域树             |
| `/api/domains/{id}`              | GET/PUT/DELETE | 安全域详情/更新/删除      |
| `/api/clients`                   | GET/POST       | 客户端列表/创建         |
| `/api/clients/{id}`              | GET/PUT/DELETE | 客户端详情/更新/删除      |
| `/api/topics/tree`               | GET            | 主题树（基于域树生成）      |
| `/api/topics/publish`            | POST           | 发布消息             |
| `/api/acl-rules`                 | GET/POST       | ACL 规则列表/创建      |
| `/api/acl-rules/{id}`            | PUT/DELETE     | ACL 规则更新/删除      |
| `/api/acl-rules/sync`            | POST           | ACL 规则同步到 Broker |
| `/api/audit-logs`                | GET            | 审计日志查询           |
| `/api/monitor/metrics`           | GET            | 系统指标             |
| `/api/monitor/message-stats`     | GET            | 消息统计             |
| `/api/monitor/client-stats`      | GET            | 客户统计             |
| `/api/monitor/topic-stats`       | GET            | 主题统计             |
| `/api/monitor/connection-status` | GET            | MQTT 连接状态        |
| `/api/subscribe/sse`             | GET            | SSE 事件流          |
| `/api/subscribe/stream`          | GET            | SSE + MQTT 订阅流   |
| `/api/subscribe/topic`           | POST           | 新增订阅主题           |
| `/api/subscribe/cancel`          | POST           | 取消订阅             |
| `/api/subscribe/session-status`  | GET            | 订阅会话状态           |
| `/api/subscribe/connect`         | POST           | 建立 MQTT 持久会话     |
| `/api/subscribe/disconnect`      | POST           | 断开 MQTT（保留 SSE）  |
| `/api/subscribe/close`           | POST           | 完全关闭订阅           |
| `/api/network/presets`           | GET            | 弱网模拟预设           |
| `/api/network/simulate`          | POST           | 设置弱网模拟参数         |

