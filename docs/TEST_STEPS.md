# 跨域数据交换系统测试步骤文档

## 目录
1. [环境准备](#1-环境准备)
2. [EMQX Broker部署](#2-emqx-broker部署)
3. [后端服务启动](#3-后端服务启动)
4. [前端服务启动](#4-前端服务启动)
5. [功能测试](#5-功能测试)
6. [性能测试](#6-性能测试)

---

## 1. 环境准备

### 1.1 必需软件检查

确保以下软件已安装：

```bash
# 检查Java版本
java -version
# 要求: Java 17+

# 检查Maven版本
mvn -version
# 要求: Maven 3.6+

# 检查Node.js版本
node -v
# 要求: Node.js 18+

# 检查npm版本
npm -v

# 检查Docker（可选，用于EMQX）
docker -v
docker-compose -v
```

### 1.2 端口检查

确保以下端口未被占用：
- 1883: EMQX MQTT TCP端口
- 8883: EMQX MQTT TLS端口
- 14567: EMQX MQTT QUIC端口
- 18083: EMQX Dashboard端口
- 8080: 后端服务端口
- 5173: 前端开发服务器端口

---

## 2. EMQX Broker部署

### 2.1 方式一：使用Docker Compose（推荐）

```bash
# 进入项目根目录
cd cross-domain-exchange

# 启动EMQX
docker-compose up -d

# 查看EMQX状态
docker-compose ps

# 查看EMQX日志
docker-compose logs -f emqx
```

等待30-60秒，直到EMQX完全启动。

### 2.2 方式二：使用Docker单独启动

```bash
# 拉取EMQX镜像
docker pull emqx/emqx:5.8.0

# 启动EMQX容器
docker run -d --name emqx \
  -p 1883:1883 \
  -p 8883:8883 \
  -p 14567:14567 \
  -p 18083:18083 \
  emqx/emqx:5.8.0

# 查看容器状态
docker ps

# 查看EMQX日志
docker logs -f emqx
```

### 2.3 验证EMQX部署

1. **访问EMQX Dashboard**
   
   打开浏览器访问：http://localhost:18083
   
   - 默认用户名：`admin`
   - 默认密码：`public`
   
   首次登录后会要求修改密码。

2. **验证监听器**
   
   在EMQX Dashboard中进入"监听"页面，确认以下监听器正在运行：
   - TCP: 0.0.0.0:1883
   - SSL: 0.0.0.0:8883
   - QUIC: 0.0.0.0:14567

3. **使用MQTT客户端测试连接**
   
   可以使用MQTTX或mosquitto_sub/mosquitto_pub测试：

   ```bash
   # 如果安装了mosquitto
   mosquitto_sub -h localhost -p 1883 -t "test" -v
   
   # 在另一个终端发布消息
   mosquitto_pub -h localhost -p 1883 -t "test" -m "hello"
   ```

---

## 3. 后端服务启动

### 3.1 编译后端项目

```bash
cd backend

# 清理并编译项目
mvn clean compile

# 如果需要跳过测试
mvn clean compile -DskipTests
```

### 3.2 运行后端服务

```bash
# 方式一：使用Maven插件启动
mvn spring-boot:run

# 方式二：先打包再运行
mvn clean package -DskipTests
java -jar target/exchange-1.0.0.jar
```

### 3.3 验证后端服务启动

1. **检查启动日志**
   
   确认看到类似以下日志：
   ```
   Started CrossDomainExchangeApplication in X.XXX seconds
   ```

2. **访问H2数据库控制台（可选）**
   
   打开浏览器访问：http://localhost:8080/h2-console
   
   - JDBC URL: `jdbc:h2:mem:crossdomaindb`
   - 用户名: `sa`
   - 密码: (留空)
   
   点击"连接"查看数据库表和数据。

3. **测试健康检查端点**
   
   ```bash
   curl http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
   ```
   
   应该返回包含token的成功响应。

---

## 4. 前端服务启动

### 4.1 安装前端依赖

```bash
cd frontend

# 安装依赖
npm install

# 如果网络较慢，可以使用淘宝镜像
npm install --registry=https://registry.npmmirror.com
```

### 4.2 启动前端开发服务器

```bash
# 启动开发服务器
npm run dev
```

### 4.3 验证前端服务

1. **访问前端页面**
   
   打开浏览器访问：http://localhost:5173
   
   应该能看到登录页面。

2. **检查控制台**
   
   打开浏览器开发者工具（F12），检查Console和Network标签页，确认没有错误。

---

## 5. 功能测试

### 5.1 用户登录测试

1. **使用管理员账号登录**
   - 用户名: `admin`
   - 密码: `admin123`
   
   **预期结果**: 成功登录，跳转到仪表盘页面。

2. **使用普通用户账号登录**
   - 用户名: `user1`
   - 密码: `user123`
   
   **预期结果**: 成功登录，显示当前域为domain-1。

3. **使用错误密码登录**
   
   **预期结果**: 显示登录失败错误信息。

### 5.2 MQTT连接测试

1. **TCP连接测试**
   - 在仪表盘点击"连接TCP"按钮
   
   **预期结果**: 
   - 显示"MQTT连接成功！"提示
   - 连接状态显示"已连接 (TCP)"
   - 后端日志显示MQTT连接成功

2. **TLS连接测试**
   - 先点击"断开连接"
   - 再点击"连接TLS"按钮
   
   **预期结果**: 
   - 显示"MQTT连接成功！"提示
   - 连接状态显示"已连接 (TLS)"

3. **断开连接测试**
   - 点击"断开连接"按钮
   
   **预期结果**: 
   - 显示"已断开连接"提示
   - 连接状态显示"未连接"

### 5.3 主题管理测试

1. **查看主题列表**
   
   **预期结果**: 
   - 显示预置的6个主题
   - 包含域内主题和跨域主题

2. **验证主题信息**
   
   检查以下主题是否存在：
   - `domain-1/sensor/temperature` (域1, 非跨域)
   - `cross-domain/alerts` (域1, 跨域)
   - `domain-2/device/status` (域2, 非跨域)
   - `cross-domain/data-exchange` (域2, 跨域)
   - `domain-3/sensor/humidity` (域3, 非跨域)
   - `domain-4/system/logs` (域4, 非跨域)

### 5.4 消息发布测试

1. **发布QoS 0消息**
   - 选择主题: `domain-1/sensor/temperature`
   - QoS: 选择"0 - 最多一次"
   - 消息内容: `{"temperature": 25.5, "timestamp": 1234567890}`
   - 点击"发布消息"
   
   **预期结果**: 
   - 显示"消息发布成功！"提示
   - 消息内容清空
   - 最近消息列表显示新消息
   - 监控指标总消息数+1

2. **发布QoS 1消息**
   - 选择主题: `cross-domain/alerts`
   - QoS: 选择"1 - 至少一次"
   - 消息内容: `{"alert": "high temperature", "level": "warning"}`
   - 点击"发布消息"
   
   **预期结果**: 同QoS 0测试。

3. **发布QoS 2消息**
   - 选择主题: `cross-domain/data-exchange`
   - QoS: 选择"2 - 精确一次"
   - 消息内容: `{"data": "important information", "priority": "high"}`
   - 点击"发布消息"
   
   **预期结果**: 同QoS 0测试。

### 5.5 监控指标测试

1. **查看概览指标**
   
   **预期结果**:
   - 总消息数: 显示发布的消息总数
   - 成功数: 显示成功消息数
   - 成功率: 显示成功率百分比
   - 平均延迟: 显示平均延迟

2. **查看可视化图表**
   
   检查以下图表是否正常显示：
   - 消息流量图表 (折线图)
   - 消息延迟图表 (柱状图)
   - 成功率图表 (饼图)
   - 跨域数据流转拓扑图 (桑基图)

3. **查看最近消息**
   
   **预期结果**:
   - 显示最近20条消息
   - 每条消息包含主题、内容、状态、协议、时间
   - 成功消息显示绿色"成功"标签
   - 失败消息显示红色"失败"标签

### 5.6 跨域功能测试

1. **发布跨域主题消息**
   - 选择跨域主题: `cross-domain/alerts`
   - 发布测试消息
   
   **预期结果**:
   - 消息成功发布
   - 消息在最近消息列表中显示
   - 拓扑图中可以看到数据流

### 5.7 用户切换测试

1. **退出当前用户**
   - 点击右上角"退出登录"按钮
   
   **预期结果**: 返回到登录页面。

2. **使用不同域的用户登录**
   - 用户名: `user2`
   - 密码: `user123`
   
   **预期结果**: 
   - 成功登录
   - 显示当前域为domain-2

---

## 6. 性能测试（可选）

### 6.1 高吞吐量测试

1. **准备测试环境**
   - 确保后端和EMQX运行正常
   - 准备批量消息发布脚本或工具

2. **执行测试**
   - 连续发布1000条消息
   - 监控消息处理时间和成功率

3. **预期指标**
   - 成功率: >99%
   - 平均延迟: <100ms
   - 吞吐量: >100 msg/s

### 6.2 弱网环境下的性能对比（需要Docker环境）

由于用户未配置Docker环境，此部分可选。如果配置了Docker和tc/netem：

1. **模拟高延迟环境**
2. **模拟高丢包环境**
3. **分别使用TCP和QUIC协议进行测试**
4. **对比延迟、吞吐量、成功率**

---

## 7. 故障排查

### 7.1 EMQX无法启动

- 检查端口是否被占用: `netstat -ano | findstr "1883"`
- 检查Docker资源是否足够
- 查看EMQX日志: `docker logs emqx`

### 7.2 后端启动失败

- 检查Java版本: `java -version`
- 检查端口8080是否被占用
- 查看后端日志中的错误信息
- 确认EMQX是否已启动

### 7.3 MQTT连接失败

- 确认EMQX正在运行
- 检查防火墙设置
- 尝试使用MQTTX等工具测试连接
- 查看后端和EMQX日志

### 7.4 前端无法访问后端API

- 确认后端服务正在运行
- 检查浏览器控制台的错误信息
- 检查CORS配置
- 尝试直接用curl测试API

---

## 8. 测试记录模板

### 8.1 功能测试记录表

| 测试项 | 测试步骤 | 预期结果 | 实际结果 | 状态 | 备注 |
|--------|----------|----------|----------|------|------|
| 管理员登录 | 输入admin/admin123 | 成功登录 | | ☐通过/☐失败 | |
| TCP连接 | 点击连接TCP | 连接成功 | | ☐通过/☐失败 | |
| 消息发布 | 发布测试消息 | 发布成功 | | ☐通过/☐失败 | |
| ... | ... | ... | ... | ... | ... |

### 8.2 性能测试记录表

| 测试场景 | 协议 | 消息数 | 成功率 | 平均延迟 | 吞吐量 | 备注 |
|----------|------|--------|--------|----------|--------|------|
| 正常网络 | TCP | 1000 | | | | |
| 正常网络 | QUIC | 1000 | | | | |
| ... | ... | ... | ... | ... | ... | ... |

---

## 9. 清理环境

测试完成后，可以按以下步骤清理：

```bash
# 停止前端服务 (Ctrl+C)

# 停止后端服务 (Ctrl+C)

# 停止EMQX
docker-compose down

# 或停止并删除容器
docker stop emqx
docker rm emqx

# 清理Docker镜像（可选）
docker rmi emqx/emqx:5.8.0
```

---

## 附录

### A. 测试账号列表

| 用户名 | 密码 | 角色 | 当前域 |
|--------|------|------|--------|
| admin | admin123 | ADMIN | domain-1 |
| user1 | user123 | USER | domain-1 |
| user2 | user123 | USER | domain-2 |
| user3 | user123 | USER | domain-3 |
| user4 | user123 | USER | domain-4 |

### B. 预置主题列表

| 主题名 | 源域 | 是否跨域 | QoS | 说明 |
|--------|------|----------|-----|------|
| domain-1/sensor/temperature | domain-1 | 否 | 1 | 域1温度传感器数据 |
| cross-domain/alerts | domain-1 | 是 | 2 | 跨域告警信息 |
| domain-2/device/status | domain-2 | 否 | 1 | 域2设备状态 |
| cross-domain/data-exchange | domain-2 | 是 | 2 | 跨域数据交换 |
| domain-3/sensor/humidity | domain-3 | 否 | 0 | 域3湿度传感器数据 |
| domain-4/system/logs | domain-4 | 否 | 1 | 域4系统日志 |

### C. 快速命令参考

```bash
# 启动所有服务（Docker Compose）
docker-compose up -d

# 启动后端
cd backend && mvn spring-boot:run

# 启动前端
cd frontend && npm run dev

# 查看EMQX日志
docker-compose logs -f emqx

# 停止所有服务
docker-compose down
```

---

**文档版本**: 1.0  
**最后更新**: 2026-03-02  
**维护者**: 跨域数据交换系统开发团队
