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

### 流程 1：发布者发消息全链路（对应论文数据生产域→跨域交换层）

> **架构升级**：发布时使用用户级 MQTT 连接，EMQX 能识别真实发布者身份。

1. **前端业务登录**：发布者在前端页面登录自己的业务账号，调用 Spring Boot 的 `/api/auth/login` 接口，完成业务系统身份校验，获取 JWT；

2. **前端发起发布请求**：发布者在前端填写发布主题、消息内容，调用 Spring Boot 的 `/api/topics/publish` 接口（URL 参数携带 JWT token）；

3. **后端建立用户级连接**：Spring Boot 使用该用户的 JWT 建立独立的 MQTT 连接（clientId = 用户名）；

4. **数据格式转换**：若消息格式非 JSON，后端通过 DataConverter 拦截器自动转换为 JSON 格式，并附加来源格式元数据；

5. **HiveMQ 客户端发布消息**：Spring Boot 通过用户级 HiveMQ MQTT Client（TLS 优先、TCP 回退）向 EMQX 发布消息；

6. **EMQX 核心鉴权与准入**：EMQX 接收到请求后，独立完成身份认证（校验用户 JWT）与权限校验（匹配内置数据库 ACL 规则），无权限直接拦截；

7. **EMQX 消息路由与持久化**：鉴权通过后，EMQX 将消息持久化到内置 durable_storage，同时匹配该主题的所有订阅者列表，准备消息转发；

8. **审计日志留存**：后端在发布成功后调用 `auditService.log()` 写入 H2 数据库（记录真实用户身份）；同时 EMQX 通过 Webhook 回调通知后端。

### 流程 2：订阅者收消息全链路

> **架构升级**：采用用户级 MQTT 连接池，每个用户独立连接 EMQX，支持离线消息补发。

1. **前端业务登录**：订阅者在前端页面登录自己的业务账号，调用 Spring Boot 的登录接口，完成业务身份校验，获取 JWT；

2. **发起订阅请求**：订阅者在前端提交要订阅的主题，调用 Spring Boot 的 `/api/subscribe/stream` 接口（携带 JWT）；

3. **后端建立用户级连接**：Spring Boot 根据 JWT 中的用户身份，使用该用户的凭证建立独立的 MQTT 连接（clientId = 用户名，cleanSession = false）；

4. **EMQX 鉴权与订阅注册**：EMQX 完成身份认证（用户自己的 JWT）、主题订阅权限校验，校验通过后将该用户的订阅关系加入订阅列表；

5. **消息下行转发**：当 EMQX 收到对应主题的新消息时，自动将消息转发给该用户（离线期间的消息会在重连后补发）；

6. **SSE 推送到前端**：后端通过 SseEmitter 将消息实时推送到订阅者的前端页面；

7. **订阅者接收消息**：前端页面实时展示收到的跨域消息，完成一次完整的跨域数据交换。

### 流程 3：管理员可视化监控全链路（对应论文表现层设计）

1. **管理员登录**：管理员在管理后台登录专属的管理员账号，调用 Spring Boot 的管理员登录接口，完成管理面身份认证，获取管控权限；

2. **可视化数据获取**：Spring Boot 后端通过两种渠道获取监控数据，完全不侵入业务消息流转：
   - **实时状态数据**：调用 EMQX REST API，获取在线客户端列表、上下线事件、消息吞吐指标、集群状态等；
   - **历史与审计数据**：从 H2 数据库中查询 EMQX Webhook 写入的消息全链路记录、审计日志、权限变更记录；

3. **前端可视化渲染**：前端通过 ECharts 渲染跨域消息流向拓扑图、性能监控大盘、客户端状态列表、消息审计日志，完整展示所有业务客户端的上线状态、消息流转全流程；

4. **管控操作执行**：管理员可在后台动态配置账号的 ACL 权限规则，Spring Boot 将配置通过 EMQX REST API 同步给 EMQX（内部同步时先DELETE全量规则再POST新规则），由 EMQX 执行最终的权限管控，全程不改变业务消息的流转逻辑。

---

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

