# 基于发布-订阅机制的数据跨域共享原型系统

面向跨部门、跨安全域数据共享中的接口互相依赖、权限分散配置和弱网传输中断等问题，本项目实现了一套基于发布-订阅（Publish/Subscribe）机制的数据跨域交换原型系统。系统以 MQTT Broker 作为中转节点，生产端按主题发布数据，消费端按授权范围订阅主题，从而降低点对点接口耦合，并在消息通道层落实身份认证、访问控制、可靠传输和审计追踪。

## 项目背景

本系统是本科毕业论文《基于发布订阅机制的数据跨域交换方法》的原型实现。论文的核心思路是：以发布-订阅机制打破传统点对点接口对接带来的高耦合，将生产域、消费域和跨域中转层解耦；以 JWT 无状态令牌和主题级 ACL 规则限定不同安全域、不同客户端的发布与订阅边界；以 MQTT QoS、持久会话和离线消息能力缓解弱网或短时离线造成的消息丢失；以审计日志和可视化监控提升跨域数据流转过程的可观测性。

## 研究定位与实现边界

本项目定位为论文方法的工程原型，重点验证以下能力：

- 基于层级主题的跨域消息路由：`cross_domain/{安全域}/{子域}/{业务}/{数据}`。
- 管理面与数据面分离：后端负责用户、域、ACL 和监控管理，EMQX Broker 负责连接认证、主题路由和权限校验。
- 主题级细粒度访问控制：通过 JWT 身份上下文和 EMQX ACL 规则控制发布/订阅范围。
- 弱网可靠传输验证：支持 QoS 0/1/2、持久会话、离线消息补发和弱网参数模拟。
- 可观测性验证：记录连接、发布、订阅、权限校验等审计日志，并展示监控图表与跨域拓扑。

当前仍属于功能验证型原型，不等同于生产级跨域交换平台。大规模并发压测、主动异常检测、多协议网关、审计日志防篡改、EMQX 集群高可用和 MQTT over QUIC 客户端接入均作为后续扩展方向保留。

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

| 用户名                    | 密码         | 角色  | 所属域      | 权限                            |
| ---------------------- | ---------- | --- | -------- | ----------------------------- |
| `admin`                | `admin123` | 管理员 | 全域       | 订阅 `cross_domain/#`           |
| `producer_medical_swh` | `123456`   | 生产者 | 医疗域/西南医院 | 发布 `cross_domain/medical/swh` |
| `consumer_gov`         | `123456`   | 消费者 | 政务域      | 订阅 `cross_domain/gov/#`       |
| `consumer_medical_swh` | `123456`   | 消费者 | 医疗域/西南医院 | 订阅 `cross_domain/medical/swh` |

## 功能模块

| 模块       | 说明                                    |
| -------- | ------------------------------------- |
| **连接管理** | 系统状态检查（后端/EMQX）、连接状态监控                |
| **消息发布** | 授权生产者向指定主题发布消息，支持 JSON/XML/TEXT 格式    |
| **消息订阅** | SSE + MQTT 双通道订阅，支持持久会话（离线消息补发）、通配符匹配 |
| **权限管理** | ACL 规则配置、全量热更新、失败提示、数据库回滚与 Broker 快照恢复，细粒度控制发布/订阅权限              |
| **审计日志** | 记录所有连接、发布、订阅、权限校验行为                   |
| **系统监控** | 消息统计、客户统计、主题统计、可视化图表                  |
| **域管理**  | 安全域的增删改查，支持父子域配置                      |
| **格式转换** | 支持 JSON、XML、TEXT 等异构载荷处理与审计记录             |
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
>
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
# 安装依赖并构建生产产物
npm install
npm run build
# 本地开发时启动 Vite 开发服务
npm run dev
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
   - 选择目标安全域和主题路径
   - 填写消息内容（支持 JSON/XML/TEXT）
   - 选择 QoS 级别并发布
2. **消费者订阅数据**
   - 使用 `consumer_gov` 或 `consumer_medical_swh` 账号登录
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

以下表格按论文中的需求与原型实现边界进行核对，避免把设计展望误写成已完成能力：

### 一、核心功能模块

#### 1. 连接与身份管理

| 功能点                                                  | 状态    | 说明                                                                                                                                                                                                             |
| ---------------------------------------------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 多模式身份认证（用户名/密码+JWT）                                  | ✅ 已实现 | [JwtUtil.java](backend/src/main/java/com/cde/security/JwtUtil.java)、[AuthController.java](backend/src/main/java/com/cde/controller/AuthController.java)                                                        |
| JWT 无状态令牌签发（含 client\_id, domainCode, roleType, exp） | ✅ 已实现 | [JwtUtil.java:39-53](backend/src/main/java/com/cde/security/JwtUtil.java#L39-L53)                                                                                                                              |
| 心跳与状态维持（PINGREQ/PINGRESP，心跳周期可配）                     | ✅ 已实现 | [MqttClientService.java:307](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L307) keepAlive(60)，HiveMQ 客户端自动处理                                                                                      |
| 持久会话支持（cleanStart=false，保留订阅与离线消息）                   | ✅ 已实现 | [MqttClientService.java:305](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L305) cleanStart(false) + sessionExpiryInterval(3600)；[emqx.conf:146](dockerrun/emqx/emqx.conf#L146) session\_expiry\_interval=2h |
| 遗嘱消息（Will Message）                                   | ✅ 已实现 | [MqttClientService.java:308-313](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L308-L313) 异常断连时自动发布 `will/{clientId}`                                                                              |

#### 2. 消息交换与路由

| 功能点                                           | 状态      | 说明                                                                                                                                                                                                                                                                                                             |
| --------------------------------------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 跨域主题管理（`cross_domain/{安全域}/{子域}/{业务}/{数据}`）     | ⚠️ 部分实现 | 种子数据遵循层级主题规范（[data.sql](backend/src/main/resources/db/data.sql)），发布接口未强制校验 `cross_domain/` 前缀                                                                                                                                                                                                                     |
| 消息发布（Topic、Payload、QoS、retain）                | ✅ 已实现   | [TopicController.java](backend/src/main/java/com/cde/controller/TopicController.java)、[MqttClientService.java:369-386](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L369-L386) 支持 QoS 0/1/2 + retain                                                                                              |
| MQTT 5.0 特性：消息 TTL（message\_expiry\_interval） | ✅ 已实现   | [emqx.conf:155](dockerrun/emqx/emqx.conf#L155) message\_expiry\_interval=48h，EMQX 自动清理过期消息                                                                                                                                                                                                                               |
| MQTT 5.0 特性：共享订阅 / 用户属性                       | ⚠️ 协议支持 | EMQX Broker 层面已支持 MQTT 5.0 共享订阅（$share/{group}/topic）和用户属性，后端 HiveMQ Client 具备协议层支持；原型当前未展开业务化封装                                                                                                                                                                                                              |
| 主题订阅/取消订阅（精确、+单层、#多层通配）                       | ✅ 已实现   | [MqttTopicUtil.java](backend/src/main/java/com/cde/util/MqttTopicUtil.java) 实现通配符匹配                                                                                                                                                                                                                            |
| 消息生命周期管理（持久化、TTL、死信队列）                        | ⚠️ 部分实现 | EMQX 原生不支持死信队列：EMQX 是 MQTT Broker，不是 RabbitMQ/Kafka 这类消息队列。消息过期或投递失败时，EMQX 只会丢弃，不会自动转移。                                                                                                                                                                                                                       |
| SSE 实时推送（前端监控链路）                              | ✅ 已实现   | [SubscribeServiceImpl.java:38-41](backend/src/main/java/com/cde/service/impl/SubscribeServiceImpl.java#L38-L41) openSse 创建 SseEmitter；[SubscribeController.java](backend/src/main/java/com/cde/controller/SubscribeController.java) /api/subscribe/sse 端点，前端 EventSource 对接 5 秒快照窗口推送                          |
| 异常数据拦截与 NACK 反馈                               | ✅ 已实现   | [TopicController.java](backend/src/main/java/com/cde/controller/TopicController.java) convertPayload 中格式转换失败时抛出 BusinessException(HttpStatus.BAD\_REQUEST)，前端收到 400 响应；JSON Schema 校验失败只记录 `json_schema_validate_fail` 审计日志，不阻断 MQTT 发布；[AuditServiceImpl.java](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java) 写入审计日志 |

#### 3. 传输保障与弱网优化

| 功能点                 | 状态      | 说明                                                                                                                                           |
| ------------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| QoS 分级保障（QoS 0/1/2） | ✅ 已实现   | [MqttClientService.java:379](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L379) MqttQos.fromCode(qos)                           |
| 智能重试与指数退避           | ⚠️ 部分实现 | [application.yml](backend/src/main/resources/application.yml) 已配置参数，但依赖 HiveMQ 默认行为，非自定义指数退避                                                 |
| 磁盘级持久化（防宕机丢失）       | ✅ 已实现   | [emqx.conf:173-176](dockerrun/emqx/emqx.conf#L173-L176) durable\_sessions.enable=true（EMQX 企业版内置）                                                      |
| 离线消息自动补发（重连后断点续传）   | ✅ 已实现   | [SubscribeServiceImpl.java:132-149](backend/src/main/java/com/cde/service/impl/SubscribeServiceImpl.java#L132-L149) connectSession 实现重连+重新订阅 |

#### 4. 安全控制与访问隔离

| 功能点                      | 状态      | 说明                                                                                                                                                                                                                                                                                                                                       |
| ------------------------ | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 传输链路加密（TLS）             | ⚠️ 部分实现 | [MqttClientService.java:318-337](backend/src/main/java/com/cde/mqtt/MqttClientService.java#L318-L337) 支持 MQTT over TLS，但失败时会回退 TCP；该能力保护客户端到 Broker 的传输链路，不等同于应用层端到端载荷加密                                                                                                                                                                                                            |
| QUIC 扩展                  | ❌ 未实现   | [emqx.conf:68-83](dockerrun/emqx/emqx.conf#L68-L83) 已保留监听配置，后端客户端仍基于 MQTT over TCP/TLS，QUIC 接入属于后续弱网优化方向                                                                                                                                                                                                                                                           |
| 细粒度 ACL 控制（按客户端/域/操作/主题） | ✅ 已实现   | [AclServiceImpl.java](backend/src/main/java/com/cde/service/impl/AclServiceImpl.java) 完整 CRUD + 全量同步 EMQX                                                                                                                                                                                                                                  |
| ACL 动态热更新（无需重启）          | ✅ 已实现   | ACL 创建、修改、删除后自动全量同步到 EMQX；同步失败时返回业务错误，通过事务边界回滚本次数据库变更，并尽力将 Broker 恢复到变更前 ACL 快照                                                                                                                                                                                                                              |
| 默认拒绝与越权拦截（Deny All）      | ✅ 已实现   | [emqx.conf:215](dockerrun/emqx/emqx.conf#L215) no\_match=deny；[data.sql:25](backend/src/main/resources/db/data.sql#L25) (`*`, `#`, `all`, `deny`)                                                                                                                                                                                                 |
| 越权/测试数据拦截后 Webhook 审计联动  | ✅ 已实现   | [emqx.conf:215](dockerrun/emqx/emqx.conf#L215) no\_match=deny；[AuditServiceImpl.java:79-86](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java#L79-L86) client.authorize 事件中 result=deny 时记录 actionType=acl\_deny 并落库；[WebhookController.java](backend/src/main/java/com/cde/controller/WebhookController.java) 接收 EMQX acl\_deny 事件 |

#### 5. 监控审计与可视化

| 功能点                     | 状态      | 说明                                                                                                                                                                                                  |
| ----------------------- | ------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 实时流量监控（TPS、连接数、资源）      | ✅ 已实现   | [MonitorServiceImpl.java](backend/src/main/java/com/cde/service/impl/MonitorServiceImpl.java) @Scheduled 定时拉取；[Dashboard.tsx](frontend/src/pages/Dashboard.tsx) ECharts 折线图                         |
| 跨域拓扑展示（ECharts 力导向图）    | ✅ 已实现   | [Dashboard.tsx:159-213](frontend/src/pages/Dashboard.tsx#L159-L213) 支持拖拽/缩放/颜色区分                                                                                                                    |
| 全链路审计日志（连接/认证/发布/订阅/权限） | ✅ 已实现   | [AuditServiceImpl.java](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java) 接收 EMQX Webhook；[WebhookController.java](backend/src/main/java/com/cde/controller/WebhookController.java) |
| 日志查询与追溯（条件过滤+导出）        | ⚠️ 部分实现 | [AuditController.java](backend/src/main/java/com/cde/controller/AuditController.java) 支持分页过滤与 PDF 导出；[AuditServiceImpl.java](backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java) 基于 OpenPDF 生成审计报表；**未实现 CSV 导出** |

### 二、跨模块支撑与扩展功能

| 功能点                              | 状态    | 说明                                                                                                                                                                                                                  |
| -------------------------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 异构数据格式转换（XML/JSON/TEXT）          | ✅ 已实现 | [XmlDataConverter.java](backend/src/main/java/com/cde/service/converter/XmlDataConverter.java) XML→JSON；[TopicController.java:71-99](backend/src/main/java/com/cde/controller/TopicController.java#L71-L99) 拦截器动态转换 |
| 二进制自定义协议转换                       | ❌ 未实现 | 未实现二进制协议解析器                                                                                                                                                                                                         |
| JSON Schema 校验                   | ⚠️ 部分实现 | [JsonSchemaValidationServiceImpl.java](backend/src/main/java/com/cde/service/impl/JsonSchemaValidationServiceImpl.java) 对已配置主题执行必填字段校验；校验失败会记录 `json_schema_validate_fail` 审计日志，但发布流程继续成功 |
| 安全域树形管理（多级域自关联）                  | ✅ 已实现 | [SysDomain.java](backend/src/main/java/com/cde/entity/SysDomain.java) parentId 自关联；[DomainController.java](backend/src/main/java/com/cde/controller/DomainController.java) 支持 CRUD                                  |
| 用户/角色管理（role\_type + client\_id） | ✅ 已实现 | [ClientController.java](backend/src/main/java/com/cde/controller/ClientController.java) 用户 CRUD；[SysUser.java](backend/src/main/java/com/cde/entity/SysUser.java) 字段完整                                              |
| 容器化部署（Docker 一键部署）               | ✅ 已实现 | [dockerrun/docker-compose.yml](dockerrun/docker-compose.yml) 编排 EMQX+Backend+Frontend；从 GitHub Release 下载编译产物后一键启动                                                                                                  |
| 弱网模拟预设（Linux tc 工具，5 种场景）        | ✅ 已实现 | [NetworkController.java](backend/src/main/java/com/cde/controller/NetworkController.java) 支持无限制/标准/政务波动/普通弱网/极端弱网                                                                                                   |
| 数据库双模兼容（H2 开发/MySQL 生产）          | ✅ 已实现 | [application.yml](backend/src/main/resources/application.yml) H2 (MODE=MySQL)；[application-mysql.yml](backend/src/main/resources/application-mysql.yml) MySQL 8.0                                                   |
| EMQX 集群高可用                       | ❌ 未实现 | 当前部署为单节点 EMQX，未配置多节点集群、故障转移或跨 Broker 级联                                                                                                                                                                                                     |

### 三、非功能与易用性验证

| 功能点      | 状态     | 说明                                                                                                                                                       |
| -------- | ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 万级并发扩展潜力 | ⚠️ 未压测 | 原型未做 JMeter 压测。EMQX 官方基准（单节点支持 10M 连接，集群支持 100M 连接）+ Docker 资源限制配置表明系统具备扩展潜力                                                                             |
| 可视化管控界面  | ✅ 已实现  | [Dashboard.tsx](frontend/src/pages/Dashboard.tsx) ECharts 折线图/力导向图；[AclManage.tsx](frontend/src/pages/AclManage.tsx) ACL 规则图形化配置；Ant Design v5 企业级 UI 组件 |

### 四、主动安全与高级特性

| 功能点          | 状态    | 说明                                                                        |
| ------------ | ----- | ------------------------------------------------------------------------- |
| 异常行为检测与自动处置  | ❌ 未实现 | 目前仅记录审计日志，无实时异常检测（高频发布、超大消息、批量探测主题、异常心跳频率）、限流、临时封禁等自动响应能力    |
| 多协议网关适配      | ❌ 未实现 | 当前仅支持原生 MQTT 客户端，未提供 HTTP、WebSocket、AMQP 等协议的转换网关，遗留系统无法平滑接入 |
| 审计日志防篡改与留存周期 | ❌ 未实现 | 日志存储于普通数据库表，未引入哈希链、数字签名、只读存储或归档策略，无法满足高合规场景下的防篡改与长期留存要求   |

***

## 文档

- [API 接口文档](docs/api.md)
- [数据库迁移：H2 → MySQL](docs/migration-to-mysql.md)
