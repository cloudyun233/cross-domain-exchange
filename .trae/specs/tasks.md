# 基于发布订阅机制的跨域数据交换系统 - The Implementation Plan (Decomposed and Prioritized Task List)

## [ ] Task 1: 创建项目目录结构
- **Priority**: P0
- **Depends On**: None
- **Description**: 
  - 创建backend、frontend、emqx、scripts等目录
  - 配置.gitignore
- **Acceptance Criteria Addressed**: [NFR-1]
- **Test Requirements**:
  - `programmatic` TR-1.1: 所有必需目录已创建
- **Notes**: 按照用户指定的项目结构创建

## [ ] Task 2: 配置Docker Compose和EMQX
- **Priority**: P0
- **Depends On**: Task 1
- **Description**: 
  - 创建docker-compose.yml，配置4个隔离域+EMQX
  - 配置emqx.conf、acl.conf
  - 生成自签名TLS证书
- **Acceptance Criteria Addressed**: [AC-1, AC-2, AC-4, AC-5, AC-6]
- **Test Requirements**:
  - `programmatic` TR-2.1: docker-compose config验证通过
  - `programmatic` TR-2.2: EMQX配置文件语法正确
- **Notes**: EMQX 5.8.0，开启1883、8883、14567端口

## [ ] Task 3: 创建弱网模拟脚本
- **Priority**: P0
- **Depends On**: Task 2
- **Description**: 
  - 创建tc/netem脚本模拟高延迟、高丢包、抖动
  - 创建QUIC vs TCP性能对比测试脚本
- **Acceptance Criteria Addressed**: [AC-10, AC-11]
- **Test Requirements**:
  - `programmatic` TR-3.1: 脚本可执行，无语法错误
- **Notes**: 脚本放在scripts目录

## [ ] Task 4: 初始化Spring Boot后端项目
- **Priority**: P0
- **Depends On**: Task 1
- **Description**: 
  - 创建Maven项目，配置pom.xml
  - 配置Spring Boot 3.3.4、Spring Security 6、JJWT等依赖
- **Acceptance Criteria Addressed**: [NFR-1]
- **Test Requirements**:
  - `programmatic` TR-4.1: Maven项目可编译
- **Notes**: 使用指定技术栈

## [ ] Task 5: 实现后端核心实体和Repository
- **Priority**: P0
- **Depends On**: Task 4
- **Description**: 
  - 创建User、Topic、Subscription、Message等实体
  - 创建Spring Data JPA Repository
  - 配置H2数据库
- **Acceptance Criteria Addressed**: [AC-13]
- **Test Requirements**:
  - `programmatic` TR-5.1: 实体类定义正确
  - `programmatic` TR-5.2: Repository接口可编译
- **Notes**: 使用Lombok、MapStruct

## [ ] Task 6: 实现JWT认证和Spring Security
- **Priority**: P0
- **Depends On**: Task 5
- **Description**: 
  - 实现JWT工具类
  - 配置Spring Security
  - 实现登录/注册接口
- **Acceptance Criteria Addressed**: [AC-14]
- **Test Requirements**:
  - `programmatic` TR-6.1: JWT token可生成和验证
  - `programmatic` TR-6.2: 登录接口返回200和token
- **Notes**: 参考Spring Security 6最佳实践

## [ ] Task 7: 实现MQTT客户端（TCP/TLS/QUIC）
- **Priority**: P0
- **Depends On**: Task 4
- **Description**: 
  - 集成Eclipse Paho MQTT Client（TCP/TLS）
  - 集成NanoSDK-Java（QUIC）
  - 实现智能重试机制（指数退避，最多5次）
- **Acceptance Criteria Addressed**: [AC-5, AC-6, AC-9]
- **Test Requirements**:
  - `programmatic` TR-7.1: TCP连接成功
  - `programmatic` TR-7.2: TLS连接成功
  - `programmatic` TR-7.3: QUIC连接成功
  - `programmatic` TR-7.4: 重试机制按指数退避执行
- **Notes**: 支持QoS 0/1/2

## [ ] Task 8: 实现后端REST API
- **Priority**: P0
- **Depends On**: Task 6, Task 7
- **Description**: 
  - 主题管理API（创建、删除、查询）
  - 订阅管理API（订阅、取消订阅）
  - 域切换API
  - 指标查询API
- **Acceptance Criteria Addressed**: [AC-13]
- **Test Requirements**:
  - `programmatic` TR-8.1: 所有API端点返回正确状态码
  - `programmatic` TR-8.2: API响应符合预期格式
- **Notes**: 使用RESTful风格

## [ ] Task 9: 实现消息持久化和监控指标收集
- **Priority**: P0
- **Depends On**: Task 5, Task 8
- **Description**: 
  - 实现消息持久化
  - 收集流量、延迟、成功率等指标
  - 提供指标查询接口
- **Acceptance Criteria Addressed**: [AC-7, AC-8, AC-12]
- **Test Requirements**:
  - `programmatic` TR-9.1: 消息持久化到数据库
  - `programmatic` TR-9.2: 监控指标正确收集
- **Notes**: 支持QoS 1/2消息持久化

## [ ] Task 10: 初始化React前端项目
- **Priority**: P0
- **Depends On**: Task 1
- **Description**: 
  - 使用Vite 6创建React 18.3 + TypeScript 5.5项目
  - 配置Tailwind CSS 4、shadcn/ui
  - 配置React Router v6、TanStack Query 5
- **Acceptance Criteria Addressed**: [NFR-1]
- **Test Requirements**:
  - `programmatic` TR-10.1: 项目可启动
  - `programmatic` TR-10.2: 依赖安装成功
- **Notes**: 使用指定技术栈

## [ ] Task 11: 实现前端JWT登录页面
- **Priority**: P0
- **Depends On**: Task 10
- **Description**: 
  - 实现登录页面UI
  - 集成JWT认证
  - 保存token到localStorage
- **Acceptance Criteria Addressed**: [AC-14]
- **Test Requirements**:
  - `human-judgement` TR-11.1: 登录页面UI美观
  - `programmatic` TR-11.2: 登录成功后token正确保存
- **Notes**: 使用shadcn/ui组件

## [ ] Task 12: 实现前端MQTT连接和QUIC/TCP切换
- **Priority**: P0
- **Depends On**: Task 11
- **Description**: 
  - 集成MQTT.js 5.x
  - 实现MQTT实时连接
  - 实现QUIC/TCP切换按钮
- **Acceptance Criteria Addressed**: [AC-15, AC-16]
- **Test Requirements**:
  - `human-judgement` TR-12.1: MQTT连接状态显示正确
  - `human-judgement` TR-12.2: 切换按钮功能正常
- **Notes**: 支持实时消息接收

## [ ] Task 13: 实现前端可视化监控仪表盘
- **Priority**: P0
- **Depends On**: Task 12
- **Description**: 
  - 集成ECharts 5.5
  - 实现实时消息流量、延迟、成功率图表
  - 实现Sankey/Graph跨域数据流转拓扑图
  - 实现主题订阅管理界面
- **Acceptance Criteria Addressed**: [AC-12]
- **Test Requirements**:
  - `human-judgement` TR-13.1: 监控图表正确显示
  - `human-judgement` TR-13.2: 拓扑图正确展示跨域数据流转
- **Notes**: 使用ECharts实现可视化

## [ ] Task 14: 实现跨域主题路由功能
- **Priority**: P0
- **Depends On**: Task 8, Task 13
- **Description**: 
  - 配置EMQX规则引擎实现跨域路由
  - 后端支持跨域主题管理
  - 前端支持跨域消息发送和接收
- **Acceptance Criteria Addressed**: [AC-3]
- **Test Requirements**:
  - `programmatic` TR-14.1: 一处发布，多处订阅成功
  - `programmatic` TR-14.2: 跨域消息正确路由
- **Notes**: 使用EMQX规则引擎

## [ ] Task 15: 编写README文档
- **Priority**: P1
- **Depends On**: Task 14
- **Description**: 
  - 编写一键启动命令
  - 编写演示步骤
  - 添加QUIC对比截图占位
- **Acceptance Criteria Addressed**: [NFR-1]
- **Test Requirements**:
  - `human-judgement` TR-15.1: README文档完整清晰
- **Notes**: 所有中文注释

## [ ] Task 16: 系统集成测试和推送GitHub
- **Priority**: P0
- **Depends On**: Task 15
- **Description**: 
  - 端到端测试整个系统
  - 确保所有功能正常
  - 推送到GitHub仓库
- **Acceptance Criteria Addressed**: [AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16]
- **Test Requirements**:
  - `programmatic` TR-16.1: 系统一键启动成功
  - `programmatic` TR-16.2: 所有功能测试通过
  - `programmatic` TR-16.3: 代码成功推送到GitHub
- **Notes**: 每完成一个部分确认正确后推送
