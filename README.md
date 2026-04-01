# 基于发布订阅机制的跨域数据交换系统

## 1. 系统概述

本系统是一个基于 MQTT 发布/订阅架构的跨域数据安全交换平台，核心由 EMQX 5.x 作为消息引擎，Spring Boot 3.x 作为业务逻辑层，React 18 + Ant Design v5 作为管理前端。

### 技术栈

| 层级 | 技术选型 |
|------|---------|
| 表现层 | React 18 + Ant Design v5 + ECharts 5.x |
| 业务逻辑层 | Spring Boot 3.x + MyBatis-Plus + JWT |
| 消息引擎层 | EMQX 5.8.0 (TCP/TLS/QUIC) |
| 数据存储 | H2 (MySQL兼容模式) / MySQL 8.0 |
| 基础设施 | Docker + Docker Compose + Linux TC |

### 核心功能

- ✅ 多协议支持: TCP / TLS / QUIC，自动降级回退
- ✅ JWT无状态认证: EMQX本地验签，无需回调后端
- ✅ 动态ACL权限: 实时推送到EMQX Broker
- ✅ 全链路审计: 通过EMQX Webhook统一采集
- ✅ 数据格式转换: XML→JSON自动转换
- ✅ 弱网模拟: Linux TC预设场景
- ✅ 实时监控: 拓扑图 + 流量统计

## 2. 快速启动

### 本地开发 (推荐)

```bash
# 1. 启动EMQX
docker-compose up -d emqx

# 2. 启动后端 (需要JDK 17)
cd backend
./mvnw spring-boot:run

# 3. 启动前端 (需要Node.js 18+)
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173

### Docker一键启动

```bash
# Windows
scripts\start.bat

# Linux/Mac
docker-compose up -d --build
```

访问 http://localhost:3000

## 3. 演示账号

| 账号 | 密码 | 角色 | 所属域 |
|------|------|------|--------|
| admin | admin123 | 管理员 | 管理域 |
| producer_swu | 123456 | 生产者 | 医疗域 |
| consumer_social | 123456 | 消费者 | 政务域 |
| consumer_c | 123456 | 消费者 | 企业域 |

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
├── backend/           # Spring Boot 后端
│   └── src/main/java/com/cde/
│       ├── controller/    # REST API控制器
│       ├── service/       # 业务服务(接口+实现)
│       ├── mapper/        # MyBatis-Plus映射
│       ├── entity/        # 数据实体
│       ├── mqtt/          # MQTT客户端 + EMQX API
│       └── security/      # JWT认证
├── frontend/          # React 前端
│   └── src/
│       ├── pages/         # 9个功能页面
│       ├── layouts/       # Ant Design布局
│       ├── services/      # API封装
│       └── contexts/      # 认证上下文
├── emqx/              # EMQX配置
├── scripts/           # 启动脚本、弱网模拟
├── docs/              # 文档
└── docker-compose.yml # 容器编排
```
