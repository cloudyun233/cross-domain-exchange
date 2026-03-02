# cross-domain-exchange

《基于发布订阅机制的数据跨域交换方法》本科毕设原型系统

## 项目简介

本系统是一个基于MQTT协议的跨域数据交换系统原型，使用EMQX作为Broker，通过Docker网络隔离模拟不同安全域，支持跨域主题路由转发、安全机制、可靠性保障、弱网环境模拟和可视化监控。

## 技术栈

### 后端
- Spring Boot 3.3.4
- Spring Security 6 + JJWT
- Eclipse Paho MQTT Client（TCP/TLS）
- Spring Data JPA + H2
- MapStruct + Lombok

### 前端
- React 18.3 + TypeScript 5.5
- Vite 6
- Tailwind CSS 4
- React Router v6
- TanStack Query 5
- MQTT.js 5.x
- ECharts 5.5

### Broker
- EMQX 5.8.0

## 项目结构

```
cross-domain-exchange/
├── backend/          # Maven Spring Boot 项目
├── frontend/         # React + Vite 项目
├── docker-compose.yml
├── emqx/             # emqx.conf、acl.conf、rule-engine 规则
├── scripts/          # tc弱网脚本、QUIC对比测试脚本
└── README.md
```

## 快速开始

### 前置要求

- Java 17+
- Node.js 18+
- Docker & Docker Compose（可选，用于运行EMQX）

### 一键启动

#### 方式一：使用Docker Compose（推荐）

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

#### 方式二：手动启动

1. **启动EMQX Broker**

```bash
# 使用Docker启动EMQX
docker run -d --name emqx -p 1883:1883 -p 8883:8883 -p 14567:14567 -p 18083:18083 emqx/emqx:5.8.0
```

2. **启动后端服务**

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

后端服务将在 http://localhost:8080 启动

3. **启动前端服务**

```bash
cd frontend
npm install
npm run dev
```

前端服务将在 http://localhost:5173 启动

## 演示步骤

### 1. 访问系统

打开浏览器访问 http://localhost:5173

### 2. 登录系统

使用以下测试账号登录：

- 管理员：`admin` / `admin123`
- 用户1（域1）：`user1` / `user123`
- 用户2（域2）：`user2` / `user123`
- 用户3（域3）：`user3` / `user123`
- 用户4（域4）：`user4` / `user123`

### 3. 连接MQTT

在仪表盘页面点击"连接TCP"或"连接TLS"按钮建立MQTT连接

### 4. 发布消息

1. 选择一个主题
2. 选择QoS级别（0/1/2）
3. 输入消息内容
4. 点击"发布消息"

### 5. 查看监控

- 查看实时消息流量、延迟、成功率图表
- 查看跨域数据流转拓扑图
- 查看最近消息列表

## 功能特性

### 核心功能
- ✅ MQTT协议发布订阅
- ✅ 多安全域数据交换
- ✅ 跨域主题路由转发
- ✅ ACL权限控制
- ✅ TLS端到端加密
- ✅ MQTT over QUIC支持
- ✅ 消息持久化
- ✅ QoS 0/1/2支持
- ✅ 智能重试机制
- ✅ 可视化监控仪表盘
- ✅ REST API接口
- ✅ JWT登录认证
- ✅ QUIC/TCP协议切换

### 弱网模拟（需要Docker）
使用tc/netem模拟高延迟、高丢包、抖动弱网环境

### QUIC vs TCP性能对比
- 延迟对比
- 吞吐量对比
- 丢包率对比

## API文档

### 认证接口

#### 登录
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

### 主题接口

#### 获取所有主题
```
GET /api/topics
Authorization: Bearer <token>
```

#### 创建主题
```
POST /api/topics
Authorization: Bearer <token>
Content-Type: application/json

{
  "topicName": "domain-1/test",
  "sourceDomain": "domain-1",
  "description": "测试主题",
  "isCrossDomain": false,
  "qos": 1,
  "retained": false
}
```

### MQTT接口

#### 连接MQTT
```
POST /api/mqtt/connect?protocol=TCP
Authorization: Bearer <token>
```

#### 发布消息
```
POST /api/mqtt/publish?topic=domain-1/test&payload=hello&qos=1
Authorization: Bearer <token>
```

#### 订阅主题
```
POST /api/mqtt/subscribe?topic=domain-1/test&qos=1
Authorization: Bearer <token>
```

### 监控接口

#### 获取概览指标
```
GET /api/metrics/overview
Authorization: Bearer <token>
```

#### 获取最近消息
```
GET /api/metrics/messages?limit=20
Authorization: Bearer <token>
```

## EMQX管理界面

访问 http://localhost:18083 进入EMQX Dashboard

默认账号：`admin` / `public`

## QUIC vs TCP性能对比截图

![QUIC vs TCP性能对比](./docs/quic-vs-tcp-comparison.png)

## 开发说明

### 后端开发

```bash
cd backend

# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn clean package
```

### 前端开发

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build
```

## 常见问题

### 1. MQTT连接失败
- 检查EMQX是否正常启动
- 检查端口1883/8883/14567是否被占用
- 检查防火墙设置

### 2. 前端无法访问后端API
- 检查后端服务是否启动
- 检查CORS配置
- 检查浏览器控制台错误信息

### 3. 数据库访问问题
- H2数据库仅在内存中，重启后数据丢失
- 如需持久化，可修改配置使用MySQL

## 许可证

MIT License

## 作者

cross-domain-exchange 项目开发团队
