# 基于发布订阅机制的跨域数据交换系统 - Product Requirement Document

## Overview
- **Summary**: 构建一个完整的、可直接运行的基于MQTT协议的跨域数据交换系统原型，使用EMQX作为Broker，通过Docker网络隔离模拟不同安全域，支持跨域主题路由转发、安全机制、可靠性保障、弱网环境模拟和可视化监控。
- **Purpose**: 提供一个可演示的跨域数据交换解决方案，验证MQTT在跨域场景下的可行性，对比QUIC与TCP在弱网环境下的性能差异。
- **Target Users**: 开发人员、系统架构师、本科毕设展示。

## Goals
- 实现基于MQTT协议的发布订阅机制
- 支持多个安全域之间的跨域数据交换
- 提供完整的安全机制（ACL、TLS、QUIC）
- 实现消息持久化和QoS保障
- 模拟弱网环境并对比QUIC vs TCP性能
- 提供可视化监控仪表盘
- 提供REST API和前端界面

## Non-Goals (Out of Scope)
- 生产级高可用部署
- 大规模水平扩展
- 复杂的多租户系统
- 实时协作编辑功能

## Background & Context
- 项目为本科毕设原型系统
- 已有Java 17环境，其他环境需配置
- 技术栈已明确指定，需严格遵循

## Functional Requirements
- **FR-1**: MQTT Broker部署与配置（EMQX 5.8.0）
- **FR-2**: 多安全域Docker网络隔离
- **FR-3**: 跨域主题路由与转发
- **FR-4**: ACL权限控制
- **FR-5**: TLS端到端加密
- **FR-6**: MQTT over QUIC支持
- **FR-7**: 消息持久化
- **FR-8**: QoS 0/1/2支持
- **FR-9**: 智能重试机制
- **FR-10**: 弱网环境模拟（tc+netem）
- **FR-11**: QUIC vs TCP性能对比
- **FR-12**: 可视化监控仪表盘
- **FR-13**: 后端REST API
- **FR-14**: 前端JWT登录
- **FR-15**: 前端MQTT实时连接
- **FR-16**: 前端QUIC/TCP切换

## Non-Functional Requirements
- **NFR-1**: 系统可一键启动（Docker Compose）
- **NFR-2**: 代码包含完整中文注释
- **NFR-3**: 前端界面响应及时
- **NFR-4**: 监控数据实时更新
- **NFR-5**: 弱网模拟可配置

## Constraints
- **Technical**: Java 17, Spring Boot 3.3.4, React 18.3, Docker Compose
- **Business**: 本科毕设项目，需完整演示
- **Dependencies**: EMQX 5.8.0, Eclipse Paho, NanoSDK-Java, MQTT.js, ECharts

## Assumptions
- 用户已安装Docker和Docker Compose
- 用户拥有GitHub仓库权限
- 网络环境可访问必要的依赖仓库
- Windows 11环境可正常运行Docker

## Acceptance Criteria

### AC-1: EMQX Broker启动成功
- **Given**: Docker Compose配置正确
- **When**: 执行docker-compose up
- **Then**: EMQX 5.8.0成功启动，监听1883(TCP)、8883(TLS)、14567(QUIC)端口
- **Verification**: `programmatic`

### AC-2: 4个安全域网络隔离
- **Given**: Docker Compose已启动
- **When**: 检查Docker网络
- **Then**: 4个隔离的Docker网络已创建，对应4个安全域
- **Verification**: `programmatic`

### AC-3: 跨域主题路由功能
- **Given**: 系统已启动，多个域连接
- **When**: 域A发布消息到跨域主题
- **Then**: 其他订阅该主题的域收到消息
- **Verification**: `programmatic`

### AC-4: ACL权限控制生效
- **Given**: ACL规则已配置
- **When**: 未授权客户端尝试发布/订阅
- **Then**: 操作被拒绝
- **Verification**: `programmatic`

### AC-5: TLS加密连接成功
- **Given**: 证书已配置
- **When**: 客户端使用TLS连接8883端口
- **Then**: 连接成功建立，数据加密传输
- **Verification**: `programmatic`

### AC-6: QUIC连接支持
- **Given**: EMQX QUIC已启用
- **When**: 客户端使用QUIC连接14567端口
- **Then**: 连接成功建立
- **Verification**: `programmatic`

### AC-7: 消息持久化
- **Given**: Broker配置持久化
- **When**: 发布QoS 1/2消息后重启Broker
- **Then**: 消息不丢失，重新发送
- **Verification**: `programmatic`

### AC-8: QoS级别支持
- **Given**: 客户端连接成功
- **When**: 发布不同QoS级别的消息
- **Then**: 消息按对应QoS级别处理
- **Verification**: `programmatic`

### AC-9: 智能重试机制
- **Given**: 网络中断
- **When**: 客户端尝试重连
- **Then**: 指数退避重试，最多5次
- **Verification**: `programmatic`

### AC-10: 弱网环境模拟
- **Given**: 系统已启动
- **When**: 执行tc/netem脚本
- **Then**: 网络出现高延迟、高丢包、抖动
- **Verification**: `programmatic`

### AC-11: QUIC vs TCP性能对比
- **Given**: 弱网环境已模拟
- **When**: 分别使用QUIC和TCP发送消息
- **Then**: 生成性能对比数据
- **Verification**: `programmatic`

### AC-12: 可视化监控显示
- **Given**: 前端已启动
- **When**: 访问监控页面
- **Then**: 显示实时流量、延迟、成功率、拓扑图
- **Verification**: `human-judgment`

### AC-13: REST API可用
- **Given**: 后端已启动
- **When**: 调用REST API
- **Then**: 返回正确响应
- **Verification**: `programmatic`

### AC-14: JWT登录成功
- **Given**: 用户账号已存在
- **When**: 输入正确凭证登录
- **Then**: 返回JWT token，访问受保护资源成功
- **Verification**: `programmatic`

### AC-15: 前端MQTT实时连接
- **Given**: 前端已登录
- **When**: 建立MQTT连接
- **Then**: 实时接收消息
- **Verification**: `human-judgment`

### AC-16: QUIC/TCP切换
- **Given**: 前端已连接
- **When**: 点击切换按钮
- **Then**: 协议切换成功
- **Verification**: `human-judgment`

## Open Questions
- [ ] 生产环境使用H2还是MySQL？
- [ ] 是否需要集成Redis？
