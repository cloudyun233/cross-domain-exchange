# 基于发布订阅机制的跨域数据交换系统

## 1. 系统概述

本系统是一个基于 MQTT 发布/订阅架构的跨域数据安全交换平台，后端通过 **HiveMQ MQTT Client** 直连 EMQX，采用 TLS 优先、TCP 回退的传输策略；Spring Boot 3.5.13 负责业务逻辑，React 18 + Ant Design v5 + Vite 作为管理前端。

> **注意**: QUIC 协议因依赖 NNG/MsQuic 原生库部署难度大，后端客户端暂未实现，但 EMQX Broker 已配置支持 QUIC 协议监听。

### 技术栈

| 层级    | 技术选型                                                                             |
| ----- | -------------------------------------------------------------------------------- |
| 表现层   | React 18.3.1 + Ant Design 5.20.0 + ECharts 5.5.1 + Vite 6.0.1 + TypeScript 5.5.4 |
| 业务逻辑层 | Spring Boot 3.5.13 + MyBatis-Plus 3.5.5 + JWT 0.12.3                             |
| 消息引擎层 | EMQX Enterprise 5.10.3 (TCP/TLS/QUIC)                                            |
| 数据存储  | H2 (默认) / MySQL 8.0                                                              |
| 基础设施  | Docker + Docker Compose                                                          |

### 核心功能

- ✅ 多协议支持: TLS / TCP 自动回退
- ✅ JWT登录态与Broker鉴权分离: 后端签发登录态 JWT，Broker 侧使用服务账号 JWT 连接
- ✅ 动态ACL权限: 后端校验后同步到 EMQX Broker
- ✅ 全链路审计: 后端审计日志 + EMQX Webhook 统一采集
- ✅ 数据格式转换: XML↔JSON自动转换
- ✅ 弱网模拟: Linux TC预设场景
- ✅ 实时监控: 拓扑图 + 流量统计

## 2. 快速启动

### 本地开发

```bash
# 1. 启动EMQX
docker-compose up -d

# 2. 启动后端 (需要JDK 17，maven已内置)
cd backend
.\mvnw.cmd spring-boot:run

# 3. 启动前端 (需要Node.js 18+)
cd frontend
npm install
npm run dev
```

- 前端访问: <http://localhost:5173>
- 后端API: <http://localhost:8080>
- EMQX管理控制台: <http://localhost:18083>

## 3. 演示账号

| 账号               | 密码       | 角色  | 所属域 |
| ---------------- | -------- | --- | --- |
| admin            | admin123 | 管理员 | 管理域 |
| producer\_swu    | 123456   | 生产者 | 医疗域 |
| consumer\_social | 123456   | 消费者 | 政务域 |
| consumer\_c      | 123456   | 消费者 | 企业域 |

## 4. 演示流程

### 阶段一: 数据发布

1. 以 `producer_swu` 登录
2. 进入"数据发布"页面
3. 选择主题 `/cross_domain/medical/hosp_swu/patient/update`
4. 填写JSON消息，选择QoS 1，点击"发布"

### 阶段二: 管理端监控

1. 以 `admin` 登录
2. 查看"监控大盘"：拓扑图、流量统计、协议标识
3. 查看"审计日志"：发布记录

### 阶段三: 数据订阅

1. 以 `consumer_social` 登录
2. 进入"数据订阅"页面
3. 选择 `/cross_domain/medical/#`，点击"开始监听"
4. 实时接收消息

### 阶段四: 安全拦截 + 动态ACL

1. `consumer_social` 尝试发布消息 → 被ACL拦截 → 审计日志标红
2. `admin` 在"ACL规则管理"为 `consumer_c` 添加订阅权限
3. `consumer_c` 即可订阅接收消息

## 5. H2 → MySQL 迁移

参见 [docs/migration-to-mysql.md](docs/migration-to-mysql.md)

## 6. 项目结构

```
cross-domain-exchange/
├── backend/              # Spring Boot 后端
│   ├── src/main/java/com/cde/
│   │   ├── config/       # 配置类
│   │   ├── controller/   # REST API控制器
│   │   ├── dto/          # 数据传输对象
│   │   ├── entity/       # 数据实体
│   │   ├── mapper/       # MyBatis-Plus映射
│   │   ├── mqtt/         # HiveMQ MQTT Client + EMQX HTTP API
│   │   ├── security/     # JWT认证
│   │   └── service/      # 业务服务(接口+实现 + 数据转换)
│   └── src/main/resources/
│       ├── db/           # 数据库初始化脚本
│       └── application*.yml # 配置文件
├── frontend/             # React 前端
│   └── src/
│       ├── components/   # 通用组件
│       ├── contexts/     # 认证上下文
│       ├── layouts/      # Ant Design布局
│       ├── pages/        # 9个功能页面 (登录、监控大盘、数据发布、数据订阅、ACL管理、审计日志、域管理、客户端管理、弱网模拟)
│       └── services/     # API封装
├── emqx/                 # EMQX配置
├── scripts/              # 启动脚本、弱网模拟
├── docs/                 # 文档
└── docker-compose.yml    # 容器编排 (仅EMQX)
```

