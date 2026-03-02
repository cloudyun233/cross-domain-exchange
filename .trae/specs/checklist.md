# 基于发布订阅机制的跨域数据交换系统 - Verification Checklist

## 基础设施检查
- [ ] Checkpoint 1: 项目目录结构完整（backend、frontend、emqx、scripts目录存在）
- [ ] Checkpoint 2: Docker Compose配置文件语法正确
- [ ] Checkpoint 3: EMQX配置文件存在且配置正确
- [ ] Checkpoint 4: .gitignore文件配置合理

## EMQX Broker检查
- [ ] Checkpoint 5: EMQX 5.8.0容器成功启动
- [ ] Checkpoint 6: 1883端口（TCP）正常监听
- [ ] Checkpoint 7: 8883端口（TLS）正常监听
- [ ] Checkpoint 8: 14567端口（QUIC）正常监听
- [ ] Checkpoint 9: ACL权限控制规则生效
- [ ] Checkpoint 10: TLS证书配置正确

## Docker网络检查
- [ ] Checkpoint 11: 4个隔离的Docker网络已创建
- [ ] Checkpoint 12: 各域容器正确连接到对应网络
- [ ] Checkpoint 13: 网络隔离生效，不同域不能直接通信

## 后端检查
- [ ] Checkpoint 14: Spring Boot项目可成功编译
- [ ] Checkpoint 15: 后端服务可成功启动
- [ ] Checkpoint 16: JWT认证功能正常（登录接口返回token）
- [ ] Checkpoint 17: REST API端点可正常访问
- [ ] Checkpoint 18: MQTT TCP连接成功
- [ ] Checkpoint 19: MQTT TLS连接成功
- [ ] Checkpoint 20: MQTT QUIC连接成功
- [ ] Checkpoint 21: 智能重试机制正常工作（指数退避，最多5次）
- [ ] Checkpoint 22: 消息持久化功能正常
- [ ] Checkpoint 23: QoS 0/1/2级别支持正常
- [ ] Checkpoint 24: 监控指标正确收集

## 前端检查
- [ ] Checkpoint 25: React项目可成功启动
- [ ] Checkpoint 26: 依赖安装成功
- [ ] Checkpoint 27: JWT登录页面正常显示和功能正常
- [ ] Checkpoint 28: MQTT实时连接功能正常
- [ ] Checkpoint 29: QUIC/TCP切换按钮功能正常
- [ ] Checkpoint 30: 可视化监控仪表盘正常显示（流量、延迟、成功率图表）
- [ ] Checkpoint 31: Sankey/Graph跨域数据流转拓扑图正常显示
- [ ] Checkpoint 32: 主题订阅管理界面正常工作

## 跨域功能检查
- [ ] Checkpoint 33: 跨域主题路由功能正常（一处发布，多处订阅）
- [ ] Checkpoint 34: 跨域消息正确转发
- [ ] Checkpoint 35: EMQX规则引擎配置正确

## 弱网模拟检查
- [ ] Checkpoint 36: tc/netem脚本可执行
- [ ] Checkpoint 37: 高延迟模拟生效
- [ ] Checkpoint 38: 高丢包模拟生效
- [ ] Checkpoint 39: 网络抖动模拟生效
- [ ] Checkpoint 40: QUIC vs TCP性能对比脚本可执行
- [ ] Checkpoint 41: 性能对比数据正确生成

## 文档检查
- [ ] Checkpoint 42: README文档完整
- [ ] Checkpoint 43: 一键启动命令清晰
- [ ] Checkpoint 44: 演示步骤详细
- [ ] Checkpoint 45: QUIC对比截图占位已添加
- [ ] Checkpoint 46: 所有代码包含完整中文注释

## 集成测试检查
- [ ] Checkpoint 47: 系统可一键启动成功
- [ ] Checkpoint 48: 端到端测试通过
- [ ] Checkpoint 49: 所有功能正常工作
- [ ] Checkpoint 50: 代码已成功推送到GitHub
