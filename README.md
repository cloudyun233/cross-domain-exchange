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
- ✅ 用户级 MQTT 连接池: 每个用户独立连接 EMQX，支持离线消息补发
- ✅ EMQX 全权 ACL: ACL 规则同步到 EMQX，EMQX 独立负责鉴权
- ✅ JWT 登录态与 Broker 鉴权分离: 后端签发登录态 JWT，EMQX 校验用户 JWT
- ✅ 动态 ACL 权限: 后端管理 ACL 规则并同步到 EMQX
- ✅ 全链路审计: 后端审计日志 + EMQX Webhook 统一采集
- ✅ 数据格式转换: XML↔JSON 自动转换
- ✅ 弱网模拟: Linux TC 预设场景
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

### 前置准备

1. 确保EMQX已启动：`docker-compose up -d`
2. 确保后端服务已启动（端口8080）
3. 确保前端服务已启动（端口5173）

---

## 第一阶段：数据发布

### 步骤1.1：生产者登录
- **操作**：以 `producer_swu` 登录，密码 `123456`

### 步骤1.2：进入数据发布页面
- **操作**：点击导航栏"数据发布"

### 步骤1.3：选择主题并配置消息
- **操作**：
  - 选择主题 `/cross_domain/medical/hosp_swu/patient/update`
  - 填写JSON消息内容
  - 选择 QoS 1

### 步骤1.4：发布消息
- **操作**：点击"发布消息"按钮
- **技术流程**：
  1. 前端调用 `/api/topics/publish` 接口（携带JWT）
  2. 后端TopicController接收请求
  3. 数据格式转换（非JSON自动转为JSON，附加元数据）
  4. MqttClientService为该用户建立独立的MQTT连接（clientId=用户名）
  5. 通过HiveMQ MQTT Client（TLS优先，TCP回退）向EMQX发布消息
  6. EMQX进行身份认证（校验JWT）和ACL权限校验
  7. 鉴权通过后，EMQX持久化消息到durable_storage
  8. AuditService.log()记录审计日志到H2数据库
  9. EMQX通过Webhook回调通知后端
- **预期**：前端显示"消息发布成功！"提示

---

## 第二阶段：管理端监控

### 步骤2.1：管理员登录
- **操作**：以 `admin` 登录，密码 `admin123`

### 步骤2.2：查看监控大盘
- **操作**：点击"监控大盘"
- **技术流程**：
  - MonitorController调用EMQX REST API获取实时状态数据
  - 从H2数据库查询历史与审计数据
  - 前端通过ECharts渲染
- **预期**：
  - 显示拓扑图
  - 显示流量统计图
  - 显示实时统计数据（连接数、消息接收/发送数等）
  - 显示协议标识（TLS/TCP）

### 步骤2.3：查看审计日志
- **操作**：点击"审计日志"
- **预期**：显示producer_swu的发布记录

---

## 第三阶段：数据订阅

### 步骤3.1：消费者登录
- **操作**：以 `consumer_social` 登录，密码 `123456`

### 步骤3.2：进入数据订阅页面
- **操作**：点击"数据订阅"

### 步骤3.3：开始监听
- **操作**：
  - 选择 `/cross_domain/medical/#` 主题
  - 点击"开始监听"
- **技术流程**：
  1. 前端调用 `/api/subscribe/stream` 接口（SSE）
  2. SubscribeController接收请求
  3. MqttClientService为该用户建立独立的MQTT连接（cleanSession=false，支持离线消息补发）
  4. EMQX完成身份认证和主题订阅权限校验
  5. 校验通过后，EMQX将订阅关系加入订阅列表
- **预期**：前端显示"SSE连接已建立, 等待消息..."

### 步骤3.4：实时接收消息
- **操作**：切换回producer_swu账号，再次发布一条消息
- **技术流程**：
  1. EMQX收到新消息，自动转发给consumer_social
  2. 后端通过SseEmitter将消息推送到前端
- **预期**：consumer_social页面实时显示收到的消息

---

## 第四阶段：安全拦截 + 动态ACL

### 步骤4.1：无权限发布被拦截
- **操作**：
  - 保持consumer_social登录状态
  - 尝试进入"数据发布"页面并发布消息
- **技术流程**：
  - EMQX进行ACL校验，发现consumer_social没有publish权限
  - 直接拦截请求
  - 审计日志标红记录
- **预期**：
  - 发布失败
  - 审计日志中显示红色的失败记录

### 步骤4.2：管理员添加ACL权限
- **操作**：
  - 切换到admin账号
  - 进入"ACL规则管理"
  - 点击"新增ACL规则"
  - 填写：
    - 客户端ID：`consumer_c`
    - 主题过滤器：`/cross_domain/medical/#`
    - 动作：`subscribe`
    - 权限：`allow`
  - 点击确定
- **技术流程**：
  - AclController接收请求
  - AclServiceImpl创建规则并调用emqxApiClient.pushAclRule()
  - EMQX API实时同步规则
- **预期**：提示"ACL规则创建成功（已同步到Broker）"

### 步骤4.3：新用户订阅接收消息
- **操作**：
  - 以 `consumer_c` 登录，密码 `123456`
  - 进入"数据订阅"页面
  - 选择 `/cross_domain/medical/#` 主题
  - 点击"开始监听"
  - 切换回producer_swu发布一条消息
- **预期**：consumer_c能够成功接收消息

---

## 第五阶段：弱网模拟

> **注意**：弱网模拟功能仅限管理员使用，且需要在 Linux Docker 环境中运行。

### 步骤5.1：管理员登录
- **操作**：以 `admin` 登录，密码 `admin123`

### 步骤5.2：进入弱网模拟页面
- **操作**：点击导航栏"弱网模拟"
- **预期**：显示5个预设场景卡片

### 步骤5.3：选择预设场景
- **操作**：点击选择一个预设场景卡片
- **预设场景说明**：

| 场景名称 | 延迟 | 丢包率 | 带宽 | 适用场景 |
|---------|------|--------|------|---------|
| 无限制 | 0ms | 0% | 无限制 | 默认状态，恢复正常网络 |
| 标准网络 | 10ms | 0% | 无限制 | 模拟正常网络环境 |
| 政务跨域波动 | 100ms | 5% | 10Mbps | 模拟跨域网络波动 |
| 普通弱网 | 250ms | 15% | 2Mbps | 模拟中等弱网环境 |
| 极端弱网 | 500ms | 30% | 1Mbps | 模拟极端网络条件 |

### 步骤5.4：应用弱网设置
- **操作**：点击"应用弱网设置"按钮
- **技术流程**：
  1. 前端调用 `/api/network/simulate` 接口（需要ADMIN权限）
  2. 后端NetworkController接收请求
  3. 执行Linux TC命令配置网络参数
  4. 返回设置结果
- **预期**：提示"弱网模拟已设置: {场景名称}"

### 步骤5.5：验证弱网效果
- **操作**：
  - 切换到producer_swu账号发布消息
  - 观察消息传输延迟和丢包情况
- **预期**：消息传输受到弱网参数影响

### 步骤5.6：恢复网络
- **操作**：选择"无限制"预设并应用
- **预期**：网络恢复正常

---

## 功能覆盖清单

| 功能模块 | 覆盖情况 |
|---------|---------|
| 用户级MQTT连接 | ✅ |
| TLS/TCP自动回退 | ✅ |
| EMQX全权ACL鉴权 | ✅ |
| JWT登录与Broker鉴权分离 | ✅ |
| 动态ACL权限管理 | ✅ |
| 数据格式转换（XML↔JSON） | ✅ |
| SSE实时推送 | ✅ |
| 全链路审计 | ✅ |
| 实时监控大盘 | ✅ |
| 拓扑图可视化 | ✅ |
| 离线消息补发 | ✅ |
| 弱网模拟（Linux TC） | ✅ |
| 管理员权限控制 | ✅ |

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

