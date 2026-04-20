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

| 层次 | 技术选型 | 说明 |
|------|---------|------|
| 消息引擎 | EMQX Enterprise 5.10.3 | MQTT Broker，支持 ACL 权限热更新 |
| 后端框架 | Spring Boot 3.5.13 + Java 17 | 业务逻辑处理、REST API |
| 安全认证 | Spring Security + JWT | 无状态身份认证 |
| 数据持久化 | MyBatis-Plus 3.5.5 | 用户、权限、审计数据管理 |
| MQTT 客户端 | HiveMQ MQTT Client 1.3 | MQTT 5.0 客户端（发布/订阅） |
| 前端框架 | React 18 + TypeScript | 单页应用 |
| UI 组件 | Ant Design v5 | 企业级 UI 组件库 |
| 可视化 | ECharts 5 | 监控数据可视化 |
| 数据库 | H2 (开发) / MySQL (生产) | 元数据存储 |
| 容器化 | Docker + Docker Compose | 一键部署 |

## 核心特性

### 1. 异步解耦
- 生产者与消费者无需知晓对方存在
- 通过 MQTT Topic 实现间接消息传递
- 支持"一处发布、多处订阅"的多对多通信

### 2. 细粒度权限控制
- 基于 ACL 的主题级访问控制
- 支持按角色（生产者/消费者）配置发布/订阅权限
- 权限规则动态热更新，无需重启 Broker

### 3. 多级服务质量（QoS）
- **QoS 0**（至多一次）：高频状态数据，低开销
- **QoS 1**（至少一次）：通用业务数据，确认重传
- **QoS 2**（恰好一次）：关键敏感数据，四次握手去重

### 4. 安全传输
- TLS 端到端加密（MQTT over TLS）
- JWT 无状态身份认证
- 主题级 ACL 权限校验

### 5. 弱网可靠性
- MQTT 持久会话支持离线消息补发
- 消息持久化存储（RocksDB）
- Traffic Control 弱网环境模拟（延迟、丢包、带宽限制）

## 功能说明

- 域树直接映射主题树，主题结构：`/cross_domain/{域编码}/{子域}/{业务模块}/{数据类型}`

## 数据模型

### 安全域结构
系统支持多层父子域结构：
- `medical` 医疗域
  - `medical/swh` 西南医院
- `gov` 政务域
- `enterprise` 企业域

### 主题命名规范
```
/cross_domain/{域编码}/{子域}/{业务模块}/{数据类型}
```
示例：
- `/cross_domain/medical/swh/record` 西南医院病历数据
- `/cross_domain/gov/minzheng/population` 民政人口数据

## 演示账号

| 用户名 | 密码 | 角色 | 所属域 | 权限 |
|--------|------|------|--------|------|
| `admin` | `admin123` | 管理员 | 全域 | 订阅 `/cross_domain/#` |
| `producer_medical_swh` | `123456` | 生产者 | 医疗域/西南医院 | 发布 `/cross_domain/medical/swh/#` |
| `consumer_social` | `123456` | 消费者 | 政务域 | 订阅 `/cross_domain/medical/#` |
| `consumer_medical_swh` | `123456` | 消费者 | 医疗域/西南医院 | 订阅 `/cross_domain/medical/swh/#` |

## 功能模块

| 模块 | 说明 |
|------|------|
| **连接管理** | 系统状态检查（后端/EMQX）、连接状态监控 |
| **消息发布** | 授权生产者向指定主题发布消息，支持 JSON/XML/TEXT 格式 |
| **消息订阅** | SSE + MQTT 双通道订阅，支持持久会话（离线消息补发）、通配符匹配 |
| **权限管理** | ACL 规则配置与同步，细粒度控制发布/订阅权限 |
| **审计日志** | 记录所有连接、发布、订阅、权限校验行为 |
| **系统监控** | 消息统计、客户统计、主题统计、可视化图表 |
| **域管理** | 安全域的增删改查，支持父子域配置 |
| **弱网模拟** | Traffic Control 弱网环境配置（延迟、丢包、带宽） |

## 部署说明

### 环境要求
- Docker Engine 24.x + Docker Compose v2.x
- Node.js 18+ (前端本地开发)
- Java 17+ (后端本地开发)

### 方式一：Docker Compose 一键部署

```bash
# 启动全部服务（EMQX + 后端 + 前端）
docker-compose up -d

# 访问地址
# - 前端管理控制台：http://localhost:80
# - EMQX Dashboard：http://localhost:18083
```

### 方式二：本地开发模式

#### 1. 启动 EMQX
```bash
docker-compose up -d emqx
```

#### 2. 启动后端
```bash
cd backend
./mvnw.cmd spring-boot:run
```

#### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
```

### 数据库初始化

系统使用 H2 内存数据库，初始化脚本：
- 初始化脚本：[backend/src/main/resources/db/schema.sql](backend/src/main/resources/db/schema.sql)
- 演示数据：[backend/src/main/resources/db/data.sql](backend/src/main/resources/db/data.sql)

如需切换至 MySQL，参考：[docs/migration-to-mysql.md](docs/migration-to-mysql.md)

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/auth/login` | POST | 用户登录 |
| `/api/auth/refresh` | POST | 刷新令牌 |
| `/api/auth/me` | GET | 获取当前用户信息 |
| `/api/status/backend` | GET | 后端服务状态 |
| `/api/status/emqx` | GET | EMQX 服务状态 |
| `/api/domains` | GET/POST | 安全域列表/创建 |
| `/api/domains/tree` | GET | 安全域树 |
| `/api/domains/{id}` | GET/PUT/DELETE | 安全域详情/更新/删除 |
| `/api/clients` | GET/POST | 客户端列表/创建 |
| `/api/clients/{id}` | GET/PUT/DELETE | 客户端详情/更新/删除 |
| `/api/topics/tree` | GET | 主题树（基于域树生成） |
| `/api/topics/publish` | POST | 发布消息 |
| `/api/acl-rules` | GET/POST | ACL 规则列表/创建 |
| `/api/acl-rules/{id}` | PUT/DELETE | ACL 规则更新/删除 |
| `/api/acl-rules/sync` | POST | ACL 规则同步到 Broker |
| `/api/audit-logs` | GET | 审计日志查询 |
| `/api/monitor/metrics` | GET | 系统指标 |
| `/api/monitor/message-stats` | GET | 消息统计 |
| `/api/monitor/client-stats` | GET | 客户统计 |
| `/api/monitor/topic-stats` | GET | 主题统计 |
| `/api/monitor/connection-status` | GET | MQTT 连接状态 |
| `/api/subscribe/sse` | GET | SSE 事件流 |
| `/api/subscribe/stream` | GET | SSE + MQTT 订阅流 |
| `/api/subscribe/topic` | POST | 新增订阅主题 |
| `/api/subscribe/cancel` | POST | 取消订阅 |
| `/api/subscribe/session-status` | GET | 订阅会话状态 |
| `/api/subscribe/connect` | POST | 建立 MQTT 持久会话 |
| `/api/subscribe/disconnect` | POST | 断开 MQTT（保留 SSE） |
| `/api/subscribe/close` | POST | 完全关闭订阅 |
| `/api/network/presets` | GET | 弱网模拟预设 |
| `/api/network/simulate` | POST | 设置弱网模拟参数 |

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
├── docs/                     # 文档
├── docker-compose.yml         # 容器编排
└── README.md
```

## 论文相关

本项目详细设计请参阅论文《基于发布订阅机制的数据跨域交换方法》：

- **第一章**：研究背景与意义，Pub/Sub 机制在跨域场景的天然优势
- **第二章**：MQTT 协议原理、EMQX 选型、安全机制设计
- **第三章**：系统需求分析与总体架构设计
- **第四章**：跨域主题路由、ACL 权限控制、弱网可靠性设计
- **第五章**：原型系统实现与测试验证

## 原型系统局限性

本原型系统尚存在以下不足，后续可从以下方面进行完善：

| 局限性 | 说明 |
|--------|------|
| **QUIC 传输** | EMQX Broker 配置文件中已预留 QUIC 监听端口（14567），但前端与后端当前仅使用标准 TCP 连接，QUIC 的低延迟、多路复用特性尚未实际启用 |
| **二进制自定义协议** | 系统支持 JSON、XML、TEXT 三种格式，未实现二进制自定义协议的解析与转换能力 |
| **JSON Schema 校验** | 数据格式转换后未进行业务层面的 JSON Schema 校验，无法确保消息符合目标主题的规范要求 |
| **域主题层级** | 原型系统实际实现为 **2 层域主题结构**（域编码/子域），数据库支持任意深度父子域 |
| **EMQX 集群** | 当前仅部署单节点 EMQX，未实现集群高可用部署（生产环境需配置 EMQX 集群 + 负载均衡） |

## 说明

- 这是毕业设计原型系统，主题树直接映射域树节点
- 如需支持更深层级、业务主题模板或独立主题策略，建议拆分主题模型
- 生产环境部署请务必配置 MySQL、TLS 证书、EMQX 集群
