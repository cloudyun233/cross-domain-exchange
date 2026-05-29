# 已知问题清单

本文记录 2026-05-19 基于用户使用流程完成的全仓库审查结果，并于 2026-05-22 再次复核补充，2026-05-23 第三轮并行审阅验证，2026-05-24 追加第四轮并行复核发现。审查路径覆盖登录、发布、订阅、ACL 管理、域管理、用户管理、审计、监控与 Docker/EMQX 部署配置。

> 当前状态：2026-05-30 已完成本轮修复；KI-001、KI-002、KI-005、KI-006、KI-007、KI-015、KI-027 按用户确认范围暂不处理，其余条目已在代码和测试中处理。

## 问题总览

| 编号 | 优先级 | 问题 | 主要影响 | 状态 |
| --- | --- | --- | --- | --- |
| KI-001 | 必须修复 | H2 Console 在 Docker 默认运行路径中公开可访问 | 数据库控制台可能被外部访问 | 暂不处理 |
| KI-002 | 必须修复 | JWT、EMQX API Key、集群 Cookie、MySQL 密码硬编码进仓库 | 可伪造令牌或操作 Broker 管理 API | 暂不处理 |
| KI-003 | 必须修复 | 用户删除、角色变更、域禁用后旧 JWT 仍可继续使用 | 账号撤权不会立即生效 | 已处理 |
| KI-004 | 必须修复 | 安全域可被改出父子环，登录时可能陷入循环 | 影响登录与域路径生成 | 已处理 |
| KI-005 | 建议修改 | EMQX Webhook 审计入口无来源校验 | 审计日志可被伪造或刷入大量噪声 | 暂不处理 |
| KI-006 | 建议修改 | 发布/订阅消息正文会进入服务端日志 | 敏感业务数据可能落盘 | 暂不处理 |
| KI-007 | 建议修改 | Docker 场景允许 TLS 降级到明文 TCP，且明文端口对宿主开放 | MQTT 传输链路安全性不足 | 暂不处理 |
| KI-008 | 建议修改 | 前端主包较大，首屏加载压力偏高 | 首次访问体验变慢 | 已处理 |
| KI-009 | 建议修改 | SSE 重连耗尽后无法在页面内恢复 | 订阅页实时消息通道只能靠刷新页面恢复 | 已处理 |
| KI-010 | 建议修改 | 登录后重定向直接读取 sessionStorage 用户信息 | 登录跳转依赖两套用户数据源 | 已处理 |
| KI-011 | 必须修复 | 刷新或重连后已恢复订阅无法取消 | 用户无法取消已记忆的订阅 | 已处理 |
| KI-012 | 必须修复 | sessionStorage 中用户数据损坏会导致前端崩溃 | 用户只能手动清理浏览器存储恢复 | 已处理 |
| KI-013 | 必须修复 | 非法或过期 JWT 不会稳定触发退出登录 | 用户可能卡在已登录但接口不可用状态 | 已处理 |
| KI-014 | 必须修复 | 安全域编码未校验就拼入 MQTT 主题路径 | 主题树、发布、订阅和 ACL 匹配可能异常 | 已处理 |
| KI-015 | 建议修改 | 连接状态检查把后端和 EMQX 状态耦合 | 单路失败会误报另一服务离线 | 暂不处理 |
| KI-016 | 建议修改 | 审计日志页面缺少 Schema 校验失败类型映射 | 事件显示、筛选和高亮不完整 | 已处理 |
| KI-017 | 建议修改 | 审计日志 PDF 导出一次性加载全量日志 | 日志增长后可能超时或造成内存压力 | 已处理 |
| KI-018 | 必须修复 | MybatisPlusConfig 硬编码 DbType.H2，MySQL 环境分页 SQL 方言错误 | 生产环境切换 MySQL 后分页查询可能报错 | 已处理 |
| KI-019 | 必须修复 | JwtAuthenticationFilter 中 roleType 为 null 时抛出 NPE | 缺少 roleType 声明的 JWT 导致服务端 500 | 已处理 |
| KI-020 | 必须修复 | EmqxApiClient 中 username 未编码直接拼入 URL 路径 | 路径注入风险，ACL 规则同步异常 | 已处理 |
| KI-021 | 必须修复 | ClientController.update 缺少字段校验，可设置非法 roleType 和 domainId | 权限配置错乱，MQTT 主题路径异常 | 已处理 |
| KI-022 | 必须修复 | MqttClientService.subscribeForUser 未校验 QoS 值，非法值致 NPE | 非法 QoS 导致订阅请求 500 崩溃 | 已处理 |
| KI-023 | 建议修改 | AuditServiceImpl 中 IPv6 地址解析逻辑错误 | IPv6 环境审计日志 IP 不正确 | 已处理 |
| KI-024 | 建议修改 | LoginRequest 缺少参数校验，null 用户名/密码致 NPE | 缺少输入校验导致错误信息不友好 | 已处理 |
| KI-025 | 建议修改 | MqttClientService.connectForUser 存在并发竞态，同一用户可能创建多个连接 | MQTT 连接泄漏 | 已处理 |
| KI-026 | 建议修改 | SubscribeServiceImpl.openSse 生命周期非原子，存在并发覆盖风险 | 并发打开 SSE 时消息推送可能丢失 | 已处理 |
| KI-027 | 建议修改 | ClientController.create 中 clientId 无唯一性保证 | 重复 clientId 导致 MQTT 连接互踢 | 暂不处理 |
| KI-028 | 建议修改 | AuditController 分页查询无上限约束，大 size 值可致 OOM | 恶意大分页请求耗尽 JVM 堆内存 | 已处理 |
| KI-029 | 建议修改 | SysUserMapper.selectByClientIdWithDomain 嵌套对象映射失效 | domain 字段永远为 null | 已处理 |
| KI-030 | 建议修改 | MqttClientService 中断连用户上下文永不清理 | userContexts 内存泄漏 | 已处理 |
| KI-031 | 建议修改 | AuthController.getCurrentUser 异常时返回 HTTP 200 而非 401 | 前端无法正确识别认证失败 | 已处理 |
| KI-032 | 必须修复 | ConnectionStatus checkConnection 存在竞态条件 | 旧调用的 catch 清除新调用的定时器和控制器 | 已处理 |
| KI-033 | 必须修复 | AuthContext token 刷新可能触发无限循环 | 后端 token 轮换时前端无限请求 /auth/me | 已处理 |
| KI-034 | 必须修复 | api.ts 的 401 处理绕过 AuthContext.logout，未关闭服务端订阅会话 | JWT 过期时服务端 MQTT+SSE 会话泄漏 | 已处理 |
| KI-035 | 建议修改 | Publish 和 Subscribe 页面 api.getDomainTree() 缺少异常处理 | 域树加载失败用户无反馈 | 已处理 |
| KI-036 | 建议修改 | NetworkSimulate api.getNetworkPresets() 缺少异常处理 | 预设加载失败用户无反馈 | 已处理 |
| KI-037 | 建议修改 | DomainManage 和 ClientManage 的 getDomainLabel 遇循环 parentId 会无限循环 | 循环引用时浏览器卡死 | 已处理 |
| KI-038 | 建议修改 | AuditLog 分页切换触发双重 API 请求 | 每次翻页产生一次无意义的额外请求 | 已处理 |
| KI-039 | 建议修改 | SubscribeContext doCancelTopic 取消订阅失败时无用户反馈 | 用户以为取消成功但服务端订阅仍存在 | 已处理 |
| KI-040 | 建议修改 | AuthContext refreshProfile 错误被静默吞没 | 会话验证失败用户无预警 | 已处理 |
| KI-041 | 建议修改 | PublishContext 切换消息格式时未自动填充示例载荷 | 与设计注释不符，降低使用效率 | 已处理 |
| KI-042 | 必须修复 | EMQX 初始 ACL 同步失败后不会继续重试 | 应用启动后可能使用空或旧 ACL 规则 | 已处理 |
| KI-043 | 必须修复 | 用户删除或改名不会清理、迁移 ACL | 旧权限可能被下一任同名用户继承 | 已处理 |
| KI-044 | 必须修复 | adminOnly 路由在用户资料恢复前误判管理员 | 管理员刷新管理页会被跳转到普通页面 | 已处理 |
| KI-045 | 建议修改 | Broker 取消订阅失败仍返回成功 | 前端、本地记忆与 Broker 订阅状态可能不一致 | 已处理 |
| KI-046 | 建议修改 | 多个更新/删除接口忽略受影响行数 | 不存在资源可能返回成功或 500 | 已处理 |
| KI-047 | 建议修改 | 登录失败的 401 被全局当作会话过期处理 | 密码错误等认证失败提示被吞掉 | 已处理 |
| KI-048 | 建议修改 | refresh token 契约不可用且文档、DTO、前端服务不一致 | Access token 过期后无法按文档刷新 | 已处理 |
| KI-049 | 建议修改 | NetworkController 弱网模拟命令超时保护不可靠 | 子进程卡住时请求线程可能长期阻塞 | 已处理 |

## 详细说明

### KI-001：H2 Console 在 Docker 默认运行路径中公开可访问

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/cde/config/SecurityConfig.java`
- `dockerrun/docker-compose.yml`

**现象：**

默认配置启用了 H2 Console，并设置 `web-allow-others: true`。安全配置又对 `/h2-console/**` 放行。Docker 部署时后端 `8080` 端口映射到宿主机，导致 H2 Console 可能对外暴露。

**影响：**

攻击者若能访问后端端口，可能尝试进入数据库控制台，读取或修改内存数据库中的用户、ACL 与审计数据。

**建议：**

- Docker 和生产 Profile 默认关闭 H2 Console。
- 如必须保留，仅允许本机访问并增加鉴权限制。
- 将 `/h2-console/**` 的放行规则限制在开发环境。

### KI-002：JWT、EMQX API Key、集群 Cookie、MySQL 密码硬编码进仓库

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-mysql.yml`
- `dockerrun/emqx/emqx.conf`
- `README.md`

**现象：**

JWT 签名密钥、EMQX API Key/Secret、EMQX 集群 Cookie、MySQL 示例密码等敏感值直接写入配置和文档。

**影响：**

仓库泄露或被非授权人员读取后，可能出现以下风险：

- 伪造 Web API 和 MQTT 认证用 JWT。
- 调用 EMQX 管理 API 修改授权规则。
- 获取默认数据库或管理端凭据。

**建议：**

- 使用环境变量、Docker secrets 或独立的本地配置文件注入敏感值。
- 对已经提交过的密钥执行轮换。
- 文档中只保留占位符，例如 `${JWT_SECRET}`、`${EMQX_API_KEY}`。

### KI-003：用户删除、角色变更、域禁用后旧 JWT 仍可继续使用

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/cde/security/JwtUtil.java`
- `backend/src/main/java/com/cde/controller/ClientController.java`
- `backend/src/main/java/com/cde/service/impl/AuthServiceImpl.java`

**现象：**

当前请求鉴权只校验 JWT 的签名和过期时间，不查询数据库确认用户是否仍存在、角色是否变更、所属域是否仍启用。用户删除或角色变更后，旧 Token 在过期前仍可访问接口。

**影响：**

管理员在界面上完成删除用户、调整角色或禁用域后，实际访问权限不会立即收敛，存在撤权延迟。

**建议：**

- 增加用户状态字段，例如 `enabled`。
- 在 JWT 中加入 `tokenVersion` 或 `updatedAt` 版本声明。
- 鉴权过滤器解析 JWT 后查询当前用户状态，并校验版本是否匹配。
- 删除用户或变更角色时，主动关闭该用户 MQTT 会话并同步 ACL。

### KI-004：安全域可被改出父子环，登录时可能陷入循环

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/pages/DomainManage.tsx`
- `backend/src/main/java/com/cde/service/impl/DomainServiceImpl.java`
- `backend/src/main/java/com/cde/service/impl/AuthServiceImpl.java`

**现象：**

前端只排除了当前域不能选择自己作为父域，但没有排除其子孙域。后端更新安全域时也没有校验父子关系是否成环。登录时构建域路径会沿 `parentId` 向上遍历，若存在环，可能无法退出循环。

**影响：**

错误的域配置可能导致登录、用户信息刷新、主题路径生成等流程异常。

**建议：**

- 后端在创建和更新域时拒绝自引用与子孙引用。
- 遍历域路径时记录已访问的域 ID，发现重复即抛出业务异常。
- 前端父域下拉框排除当前节点及其所有子孙节点。

### KI-005：EMQX Webhook 审计入口无来源校验

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/config/SecurityConfig.java`
- `backend/src/main/java/com/cde/controller/WebhookController.java`
- `backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java`

**现象：**

`/api/webhook/**` 被公开放行，Webhook Controller 直接接收请求体并写入审计日志，没有校验来源、签名或共享密钥。

**影响：**

任何能访问后端接口的人都可能伪造 EMQX 事件，制造虚假的连接、发布、订阅、ACL 拒绝记录，也可能通过大量请求刷爆审计表。

**建议：**

- 增加共享密钥、HMAC 签名或内网来源限制。
- 对 Webhook 接口增加速率限制。
- 审计入库前校验事件字段白名单和长度限制。

### KI-006：发布/订阅消息正文会进入服务端日志

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/mqtt/MqttClientService.java`
- `backend/src/main/java/com/cde/service/impl/SubscribeServiceImpl.java`

**现象：**

服务端日志会记录收到消息和推送消息的 payload 前 200 字符。

**影响：**

项目面向跨域医疗、政务、企业数据交换，消息体可能包含敏感业务字段。即使只记录前 200 字符，也可能造成日志侧数据泄露。

**建议：**

- 默认只记录 topic、payload 长度、QoS、traceId 或 payload hash。
- 如需调试正文，放到 DEBUG 日志并默认关闭。
- 对审计日志与应用日志区分敏感数据策略。

### KI-007：Docker 场景允许 TLS 降级到明文 TCP，且明文端口对宿主开放

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/resources/application-docker.yml`
- `backend/src/main/java/com/cde/mqtt/MqttClientService.java`
- `dockerrun/docker-compose.yml`
- `dockerrun/emqx/emqx.conf`

**现象：**

Docker Profile 设置 `insecure-trust-all: true`。MQTT 客户端 TLS 连接失败后会降级到 TCP。Docker Compose 同时将 `1883` 明文 MQTT 端口映射到宿主机。

**影响：**

在非本机或共享网络环境中，MQTT 认证与消息内容可能走明文链路，削弱跨域交换系统的传输安全边界。

**建议：**

- 生产环境禁止自动降级到 TCP。
- 关闭或限制 `1883` 端口对宿主机的暴露。
- 使用可信证书链，不在生产 Profile 中使用 `insecure-trust-all`。

### KI-008：前端主包较大，首屏加载压力偏高

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/App.tsx`
- `frontend/src/pages/Dashboard.tsx`
- `frontend/vite.config.ts`

**现象：**

前端生产构建成功，但 Vite 提示主 JS Chunk 约 2.23 MB，gzip 后约 724 KB。当前路由组件和 ECharts 相关模块在首屏一起进入主包。

**影响：**

首次访问登录页或普通发布/订阅页面时，也可能下载管理大盘、审计、网络模拟等暂时用不到的代码，影响弱网环境下的加载体验。

**建议：**

- 使用 `React.lazy` 和 `Suspense` 对页面路由做懒加载。
- 将 ECharts、Ant Design 等大依赖拆分为独立 Chunk。
- 按需加载管理端页面，普通用户流程优先保持轻量。

### KI-009：SSE 重连耗尽后无法在页面内恢复

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/contexts/SubscribeContext.tsx`

**现象：**

SSE 连接连续重试超过最大次数后会停止自动重连，并提示用户刷新页面。此时用户继续点击连接 MQTT 时，前端会抛出“组件已卸载，无法建立 SSE 连接”，但实际组件并未卸载，只是重连开关已被关闭。

**影响：**

订阅页实时消息通道中断后无法通过页面按钮恢复，用户必须整页刷新才能继续使用订阅功能，且错误文案会误导排查方向。

**建议：**

- 区分“组件卸载”和“重连耗尽”两种状态，给出准确错误提示。
- 在用户主动点击连接 MQTT 或订阅主题时允许重新开启 SSE 连接。
- 增加手动重连入口或自动恢复策略，避免必须刷新页面。

### KI-010：登录后重定向直接读取 sessionStorage 用户信息

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/pages/Login.tsx`
- `frontend/src/contexts/AuthContext.tsx`

**现象：**

登录成功后的跳转逻辑直接从 `sessionStorage` 读取并解析用户角色，而不是使用认证上下文中已经映射完成的用户信息。当前流程能正常运行，但登录页和认证上下文形成了两套用户数据来源。

**影响：**

如果后续调整用户信息映射、存储字段或登录响应结构，登录跳转可能与全局认证状态不一致，导致管理员或普通用户被跳转到错误页面。

**建议：**

- 让 `login()` 返回标准化后的用户信息，登录页根据返回值跳转。
- 或在认证上下文中提供统一的登录后目标路由计算方法。
- 避免页面直接解析 `sessionStorage` 中的用户结构。

### KI-011：刷新或重连后已恢复订阅无法取消

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/contexts/SubscribeContext.tsx`
- `frontend/src/pages/Subscribe.tsx`

**现象：**

订阅会话状态刷新时只恢复 `subscribedTopics` 和 `subscriptionCount`，不会恢复 `activeTopic`。取消订阅按钮又只依赖 `activeTopic` 判断是否可用。页面刷新、重新进入订阅页或 MQTT 重连后，界面能显示已记忆订阅，但取消按钮会保持禁用。

**影响：**

用户无法在页面上取消已经记忆或恢复的订阅，只能重新订阅覆盖当前活跃主题，或依赖后端接口、退出会话等间接方式清理订阅。

**建议：**

- 为每个 `subscribedTopics` 渲染独立取消入口，取消时传入明确 topic。
- 或在会话状态恢复时同步恢复当前可取消的 `activeTopic`。
- 后端返回订阅状态时可补充最近活跃订阅，避免前端单独推断。

### KI-012：sessionStorage 中用户数据损坏会导致前端崩溃

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/contexts/AuthContext.tsx`

**现象：**

认证上下文初始化时直接执行 `JSON.parse(sessionStorage.getItem('user'))`，没有捕获解析异常，也没有在解析失败后清理损坏的 `token` 和 `user`。

**影响：**

浏览器存储如果因为版本变更、手动修改、半写入或异常数据变成非法 JSON，React 应用会在渲染认证 Provider 时崩溃。用户无法通过界面退出，只能手动清理浏览器存储。

**建议：**

- 提供安全解析函数，解析失败时移除 `token` 和 `user`。
- 将异常存储视为未登录状态，并重定向到登录页。
- 认证状态恢复时增加数据结构校验，避免字段缺失导致后续页面异常。

### KI-013：非法或过期 JWT 不会稳定触发退出登录

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/cde/config/SecurityConfig.java`
- `frontend/src/contexts/AuthContext.tsx`
- `frontend/src/services/api.ts`

**现象：**

JWT 校验失败时后端过滤器只是跳过认证并继续进入后续过滤链，受保护接口通常由 Spring Security 拒绝。前端请求封装只在 HTTP 401 时清理会话；认证上下文刷新 `/auth/me` 失败或返回 `success=false` 时也会直接忽略。

**影响：**

Token 过期、损坏或服务端无法确认当前用户时，前端可能仍保持 `isAuthenticated=true`，用户停留在受保护页面，但后续接口连续失败，表现为“看起来已登录但无法操作”。

**建议：**

- 后端配置统一的 `authenticationEntryPoint`，未认证或无效凭证返回 401 JSON。
- 保留权限不足场景的 403，避免与凭证失效混淆。
- 前端在 `/auth/me` 失败、返回 `success=false` 或收到认证类错误时清理会话并跳转登录。
- 增加认证初始化状态，避免用户信息未确认前提前渲染受保护页面。

### KI-014：安全域编码未校验就拼入 MQTT 主题路径

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/service/impl/DomainServiceImpl.java`
- `frontend/src/pages/DomainManage.tsx`

**现象：**

安全域创建和更新时直接保存 `domainCode`，后续构建域树时又直接把它拼入 `cross_domain/{codePath}`。当前没有拒绝 `/`、`+`、`#`、空白等不适合作为单个 MQTT 主题路径段的字符。

**影响：**

管理员录入 `gov/#`、`a/b`、空格等编码后，主题树会生成异常路径。发布可能因 MQTT topic 非法失败，订阅可能被通配符扩大到错误范围，ACL 规则也难以正确匹配。

**建议：**

- 后端在 `create()` 和 `update()` 中统一 `trim` 并校验 `domainCode`。
- 建议只允许 `^[A-Za-z0-9_-]{1,64}$` 这类单段主题安全字符。
- 前端表单同步增加规则提示，但后端必须作为最终校验边界。

### KI-015：连接状态检查把后端和 EMQX 状态耦合

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/components/ConnectionStatus.tsx`

**现象：**

连接状态浮窗用同一个 `Promise.all` 和同一个 `AbortController` 同时检查后端与 EMQX。任意一个请求失败、超时或被取消，`catch` 中会同时把后端和 EMQX 都标记为离线。

**影响：**

如果 EMQX 健康检查超时，但后端服务仍可用，页面也会显示“后端离线”。这会误导用户和管理员排障，尤其是在 Broker 短暂不可达时。

**建议：**

- 使用 `Promise.allSettled` 分别处理两个结果。
- 为后端与 EMQX 使用独立的 `AbortController` 和超时控制。
- 单路失败只更新对应状态，避免互相污染。

### KI-016：审计日志页面缺少 Schema 校验失败类型映射

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/pages/AuditLog.tsx`
- `backend/src/main/java/com/cde/controller/TopicController.java`

**现象：**

后端在 JSON Schema 校验失败但继续发布时会记录 `json_schema_validate_fail` 审计事件，但审计日志页面的颜色映射、中文标签、筛选项和危险事件判断中没有包含该类型。

**影响：**

管理员在审计页面看到的是原始英文标识，无法通过操作类型下拉框筛选该事件，也不会按危险事件高亮，降低审计排查效率。

**建议：**

- 在 `actionTypeColors`、`actionTypeLabels`、筛选选项和 `isDanger()` 中补齐 `json_schema_validate_fail`。
- 中文标签可使用“Schema 校验失败”。
- 将格式转换失败和 Schema 校验失败区分展示，便于定位数据质量问题。

### KI-017：审计日志 PDF 导出一次性加载全量日志

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java`

**现象：**

PDF 导出接口直接 `selectList()` 读取所有符合条件的审计日志，并在同一个请求线程中逐行生成 PDF。当前没有时间范围、最大条数、分页读取或异步导出限制。

**影响：**

系统运行一段时间后，Webhook 和业务审计日志会持续增长。管理员点击导出时可能出现接口长时间无响应、请求超时、JVM 内存压力升高，甚至影响其他用户请求。

**建议：**

- 导出接口强制要求时间范围或最大条数。
- 大数据量导出改为分页读取、流式写入或异步任务生成文件。
- 超过阈值时返回明确提示，引导用户缩小筛选范围。

### KI-018：MybatisPlusConfig 硬编码 DbType.H2，MySQL 环境分页 SQL 方言错误

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/config/MybatisPlusConfig.java`

**现象：**

分页插件固定使用 `DbType.H2`，但项目提供了 `application-mysql.yml` 的 MySQL Profile。当使用 MySQL Profile 启动时，分页插件仍生成 H2 方言的分页 SQL。虽然 H2 (MySQL 模式) 与 MySQL 的 `LIMIT/OFFSET` 语法大部分兼容，但在复杂查询（如带 `FOR UPDATE`、子查询分页、`ORDER BY` 含函数等场景）下可能生成不合法 SQL，导致分页查询失败。

**建议：**

- 根据 `spring.datasource.driver-class-name` 或自定义配置动态选择 `DbType`，例如通过 `@Value` 注入并切换。

### KI-019：JwtAuthenticationFilter 中 roleType 为 null 时抛出 NPE

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/security/JwtAuthenticationFilter.java`

**现象：**

`getRoleTypeFromToken` 从 JWT claims 中取 `roleType` 字段，若令牌中不包含该声明（如旧版令牌、手动构造的令牌、或 claim 被删改），返回值为 `null`，随后 `roleType.toUpperCase()` 抛出 `NullPointerException`，导致整个请求认证流程崩溃，返回 500 错误。

**建议：**

- 对 `roleType` 做 null 检查，为 null 时拒绝认证或赋予默认最小权限角色。

### KI-020：EmqxApiClient 中 username 未编码直接拼入 URL 路径

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/mqtt/EmqxApiClient.java`

**现象：**

ACL 规则中的 `username` 直接拼接到 URL 路径中，未做任何 URL 编码。若 username 包含 `/`、`?`、`#`、空格等特殊字符，URL 路径会被篡改或断裂。

**建议：**

- 使用 `URLEncoder.encode(username, StandardCharsets.UTF_8)` 编码后再拼入 URL，或使用 `UriComponentsBuilder` 构建安全 URL。

### KI-021：ClientController.update 缺少字段校验，可设置非法 roleType 和 domainId

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/controller/ClientController.java`

**现象：**

直接使用实体类 `SysUser` 作为 `@RequestBody`，未对可更新字段做白名单限制或校验。管理员可以将 `roleType` 设为任意值，或将 `domainId` 设为不存在的域 ID。

**建议：**

- 创建独立的 UpdateUserDTO 做白名单限制，或对 `roleType` 和 `domainId` 增加合法值校验。

### KI-022：MqttClientService.subscribeForUser 未校验 QoS 值，非法值致 NPE

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/mqtt/MqttClientService.java`

**现象：**

`MqttQos.fromCode(int)` 仅接受 0/1/2，对其他值返回 `null`。若前端传入 `qos=3` 或负数，`MqttQos.fromCode(3)` 返回 `null`，后续 `.send()` 抛出 `NullPointerException`。

**建议：**

- 在入口处校验 `qos` 必须为 0/1/2，非法值抛出 `BusinessException(BAD_REQUEST)`。

### KI-023：AuditServiceImpl 中 IPv6 地址解析逻辑错误

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/service/impl/AuditServiceImpl.java`

**现象：**

EMQX 的 `peername` 格式为 `ip:port`，当前逻辑用 `lastIndexOf(':')` 剥离端口。但 IPv6 地址本身包含冒号（如 `[::1]:1883`），`lastIndexOf(':')` 会定位到地址内部的冒号而非端口分隔符，导致截取结果为 `[::1`（残留方括号且地址不完整）。

**建议：**

- 先判断是否为 IPv6 格式（以 `[` 开头），若是则取 `]` 前的内容；否则用 `lastIndexOf(':')` 剥离端口。

### KI-024：LoginRequest 缺少参数校验，null 用户名/密码致 NPE

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/dto/LoginRequest.java`
- `backend/src/main/java/com/cde/service/impl/AuthServiceImpl.java`

**现象：**

`LoginRequest` 的 `username` 和 `password` 字段没有 `@NotBlank` 等校验注解，`AuthController.login` 也没有 `@Valid` 注解。若请求体中 `username` 为 `null`，`getRequiredUser(null)` 会执行 `getUserByUsername(null)`，MyBatis-Plus 会生成 `WHERE username IS NULL` 的 SQL。

**建议：**

- 在 `LoginRequest` 上添加 `@NotBlank` 注解，在 `AuthController.login` 参数上添加 `@Valid`。

### KI-025：MqttClientService.connectForUser 存在并发竞态，同一用户可能创建多个连接

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/mqtt/MqttClientService.java`

**现象：**

`connectForUser` 的"检查-然后-操作"不是原子的。两个线程同时为同一用户调用 `connectForUser`，都看到 `existing=null`，各自创建新的 `UserMqttContext` 并 `put`，后写入的覆盖先写入的，导致先创建的 MQTT 连接泄漏。

**建议：**

- 使用 `userContexts.compute()` 原子操作替代 get+put 组合，或在方法入口对 username 加锁。

### KI-026：SubscribeServiceImpl.openSse 生命周期非原子，存在并发覆盖风险

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/service/impl/SubscribeServiceImpl.java`

**现象：**

`openSse` 中关闭旧 emitter、创建新 emitter、注册回调并写入 `userEmitters` 的流程不是一个原子操作。当前回调使用 `userEmitters.remove(username, emitter)`，不会支持“旧回调误删新 emitter”的精确说法；但并发打开 SSE 时，不同调用仍可能交叉完成，造成连接覆盖、生命周期回调顺序混乱或短时间内消息推送目标不稳定。

**建议：**

- 使用 `ConcurrentHashMap.compute()` 或按 username 加锁，原子地完成“关闭旧连接、写入新连接、注册生命周期回调”的流程。

### KI-027：ClientController.create 中 clientId 无唯一性保证

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/controller/ClientController.java`

**现象：**

clientId 按 `username_001` 模式生成，但数据库 `sys_user` 表的 `client_id` 列没有 UNIQUE 约束。若删除用户后以相同用户名重建，会导致 MQTT Broker 上相同 clientId 的客户端互踢。

**建议：**

- 在 `sys_user` 表上为 `client_id` 添加 UNIQUE 约束，或在创建时检查唯一性后生成递增后缀。

### KI-028：AuditController 分页查询无上限约束，大 size 值可致 OOM

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/controller/AuditController.java`

**现象：**

`size` 参数仅设了默认值 20，没有最大值限制。管理员可传入 `size=999999`，MyBatis-Plus 会生成 `LIMIT 999999` 的 SQL，将大量审计日志加载到内存。

**建议：**

- 添加 `@Max` 校验注解或在代码中 `size = Math.min(size, 500)` 限制上限。

### KI-029：SysUserMapper.selectByClientIdWithDomain 嵌套对象映射失效

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/mapper/SysUserMapper.java`

**现象：**

SQL 查询了 `d.domain_code` 和 `d.domain_name`，但 `SysUser` 实体中没有 `domainCode`/`domainName` 字段（仅有 `SysDomain domain` 嵌套对象且标注 `@TableField(exist = false)`）。MyBatis-Plus 的 `@Select` 注解不会自动将列映射到嵌套对象，这两个列会被静默丢弃，`user.getDomain()` 始终为 `null`。

**建议：**

- 使用 `@Results` + `@Result` 注解配置嵌套映射，或改用 XML Mapper 定义 resultMap，或删除此方法改用 Service 层关联查询。

### KI-030：MqttClientService 中断连用户上下文永不清理

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/mqtt/MqttClientService.java`

**现象：**

`disconnectForUser` 将 `connected` 设为 false 但不从 `userContexts` 中移除 entry。若用户断开后不再调用 `closeAll`，该用户的 `UserMqttContext` 会永远留在 `userContexts` 中，造成内存泄漏。

**建议：**

- 添加定时清理任务，扫描 `userContexts` 中 `connected=false` 且超过一定时间未重连的条目并移除；或在 SSE 断开时自动触发 `closeAll`。

### KI-031：AuthController.getCurrentUser 异常时返回 HTTP 200 而非 401

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/controller/AuthController.java`

**现象：**

当 JWT 解析失败或用户不存在时，catch 块返回 `ApiResponse.fail()`，HTTP 状态码仍为 200。前端若仅通过 HTTP 状态码判断请求成败，会误认为请求成功。

**建议：**

- 移除 try-catch，让异常自然传播到 `GlobalExceptionHandler`；或在 catch 中根据异常类型返回正确的 HTTP 状态码。

### KI-032：ConnectionStatus checkConnection 存在竞态条件

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/components/ConnectionStatus.tsx`

**现象：**

`checkConnection` 通过闭包变量 `activeTimeoutId` / `activeController` 管理请求生命周期。新调用会 abort 旧控制器，但旧调用的 `catch` 块执行时会清除新调用的定时器和控制器引用，导致新请求的超时保护失效，且状态被错误地置为离线。

**建议：**

- 用请求序号（如 `requestId`）标识每次调用，catch 块中检查当前 requestId 是否仍为自己发起的，只清理属于自己的资源。

### KI-033：AuthContext token 刷新可能触发无限循环

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/contexts/AuthContext.tsx`

**现象：**

`useEffect` 依赖 `[token]`，当 `/auth/me` 返回新 token 时，`persistSession` 调用 `setToken(nextToken)` 触发状态更新，导致 effect 重新执行，再次调用 `refreshProfile`，形成循环。

**建议：**

- 在 `refreshProfile` 中，仅当 `nextToken !== token` 时才调用 `persistSession` 更新 token；或将 token 更新逻辑从该 effect 中剥离，改用独立机制避免 effect 自我触发。

### KI-034：api.ts 的 401 处理绕过 AuthContext.logout，未关闭服务端订阅会话

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/services/api.ts`
- `frontend/src/contexts/AuthContext.tsx`

**现象：**

`request` 函数的 401 处理直接操作 `sessionStorage` 并用 `window.location.href` 跳转，完全绕过了 `AuthContext.logout()`。`logout()` 会调用 `api.closeSubscribeSession()` 关闭服务端 MQTT+SSE 会话，但 401 路径不会。

**建议：**

- 将 401 处理重构为调用统一的登出逻辑（如通过全局事件通知 AuthContext 执行 logout），而非在 api.ts 中重复清理逻辑。

### KI-035：Publish 和 Subscribe 页面 api.getDomainTree() 缺少异常处理

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/pages/Publish.tsx`
- `frontend/src/pages/Subscribe.tsx`

**现象：**

两处 `api.getDomainTree()` 调用只链了 `.then()` 没有 `.catch()`，若请求失败会产生 unhandled promise rejection，用户无任何反馈。

**建议：**

- 添加 `.catch()` 处理，给用户提示"域树加载失败"。

### KI-036：NetworkSimulate api.getNetworkPresets() 缺少异常处理

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/pages/NetworkSimulate.tsx`

**现象：**

`api.getNetworkPresets()` 只有 `.finally()` 没有 `.catch()`，请求失败时产生 unhandled promise rejection。

**建议：**

- 添加 `.catch()` 并给用户提示加载失败。

### KI-037：DomainManage 和 ClientManage 的 getDomainLabel 遇循环 parentId 会无限循环

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/pages/DomainManage.tsx`
- `frontend/src/pages/ClientManage.tsx`

**现象：**

`getDomainLabel` 通过 `while` 循环沿 `parentId` 链向上遍历，但没有环检测。若数据存在循环引用，循环永不终止，导致浏览器卡死。

**建议：**

- 添加已访问 ID 集合（`Set<number>`），遍历时检测到重复 ID 即终止循环。

### KI-038：AuditLog 分页切换触发双重 API 请求

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/pages/AuditLog.tsx`

**现象：**

分页 `onChange` 同时调用 `setPage(p)` 和 `fetchLogs(p)`，而 `useEffect` 也依赖 `[page]` 并调用 `fetchLogs()`。这导致切换页码时同一页数据被请求两次。

**建议：**

- 移除 `onChange` 中的 `fetchLogs(p)` 调用，仅依赖 `useEffect` 响应 `page` 变化来触发请求。

### KI-039：SubscribeContext doCancelTopic 取消订阅失败时无用户反馈

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/contexts/SubscribeContext.tsx`

**现象：**

`doCancelTopic` 的 catch 块仅 `console.error`，没有向用户展示任何错误提示。用户点击"取消订阅"后若请求失败，界面无任何反馈。

**建议：**

- 在 catch 块中添加 `message.error('取消订阅失败，请重试')` 或类似提示。

### KI-040：AuthContext refreshProfile 错误被静默吞没

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/contexts/AuthContext.tsx`

**现象：**

`refreshProfile` 的 catch 块为空，非 401 类错误（如网络断开）被完全忽略，用户无法得知会话验证失败。

**建议：**

- 在非 cancelled 的 catch 中添加日志记录或轻量提示，让开发者/用户感知到会话验证异常。

### KI-041：PublishContext 切换消息格式时未自动填充示例载荷

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/contexts/PublishContext.tsx`

**现象：**

文件顶部设计注释明确写道"切换消息格式时自动填充对应的示例载荷模板"，但 `handleSetFormat` 实现只更新了 `format` 状态，没有同步更新 `payload`。

**建议：**

- 在 `handleSetFormat` 中根据 `newFormat` 自动设置对应的示例载荷。

### KI-042：EMQX 初始 ACL 同步失败后不会继续重试

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/config/EmqxAclSyncInitializer.java`
- `backend/src/main/java/com/cde/service/impl/AclServiceImpl.java`

**现象：**

启动初始化逻辑只在 EMQX HTTP API 未就绪时继续循环重试。一旦 `isApiReady()` 返回 true，随后 `aclService.syncToEmqx()` 如果因为授权源清空、启用或规则推送的临时异常而抛错，异常会被外层 catch 捕获并直接结束初始化流程，不再尝试剩余次数。

**影响：**

后端应用会继续启动，但数据库中的 ACL 规则没有同步到 EMQX。发布和订阅可能使用空规则或旧规则，直到管理员手动调用 ACL 同步接口。

**建议：**

- 将 `syncToEmqx()` 纳入重试循环，区分“API 未就绪”和“同步失败”两类失败。
- 每次同步失败后继续等待并重试，全部失败后记录清晰告警。

### KI-043：用户删除或改名不会清理、迁移 ACL

**优先级：** 必须修复

**涉及位置：**

- `backend/src/main/java/com/cde/controller/ClientController.java`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/java/com/cde/service/impl/AclServiceImpl.java`

**现象：**

删除用户只执行 `userMapper.deleteById(id)`，更新用户也允许修改 `username`。ACL 表中的 `username` 是普通字符串列，没有外键、级联删除或迁移逻辑。用户删除后，其 ACL 规则仍留在数据库和 EMQX 侧；用户改名后，旧用户名对应的 ACL 也不会自动迁移。

**影响：**

后续创建同名用户时会继承旧发布/订阅权限；改名用户也可能出现 Web 身份与 MQTT ACL 不一致，形成权限残留。

**建议：**

- 删除用户时同步删除或禁用该用户 ACL，并触发 EMQX 全量同步。
- 若允许改名，应在同一事务内迁移 ACL；若不允许改名，应在更新接口中禁止修改 `username`。

### KI-044：adminOnly 路由在用户资料恢复前误判管理员

**优先级：** 必须修复

**涉及位置：**

- `frontend/src/contexts/AuthContext.tsx`
- `frontend/src/components/ProtectedRoute.tsx`
- `backend/src/main/java/com/cde/controller/AuthController.java`

**现象：**

前端从 `sessionStorage` 恢复 token 后立即将 `isAuthenticated` 视为 true，但用户资料可能仍在 `/auth/me` 异步恢复过程中。此时 `ProtectedRoute` 对 `adminOnly` 页面直接判断 `user?.roleType`，当 `user` 暂时为 null 时会把有效管理员误判为非管理员并跳转到 `/publish`。

**影响：**

管理员刷新 `/dashboard`、`/domains`、`/clients`、`/acl`、`/audit` 或 `/network` 等管理页时，可能被错误踢到普通发布页。

**建议：**

- 在认证上下文中增加 `authLoading` 或 `profileReady` 状态，用户资料恢复完成前不要做 adminOnly 判定。
- `ProtectedRoute` 在资料恢复中展示加载状态，恢复失败再执行登出或跳转。

### KI-045：Broker 取消订阅失败仍返回成功

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/mqtt/MqttClientService.java`
- `backend/src/main/java/com/cde/controller/SubscribeController.java`
- `frontend/src/contexts/SubscribeContext.tsx`

**现象：**

`unsubscribeForUser` 先从本地 `subscribedTopics` 移除主题，再向 Broker 发送 UNSUBSCRIBE。如果 Broker 取消失败，catch 块只记录 warn 并忽略异常，Controller 仍返回“已取消订阅”。前端刷新状态后也只能看到本地记忆已删除，无法感知 Broker 侧仍可能保留订阅。

**影响：**

前端、本地订阅记忆和 Broker 实际订阅状态可能不一致。用户以为取消成功，但后续仍可能收到对应主题消息。

**建议：**

- Broker 取消失败时返回明确错误，或先确认 Broker 取消成功后再移除本地订阅记忆。
- 对需要保留“尽力取消”的场景，返回带状态的部分成功响应并提示用户重试。

### KI-046：多个更新/删除接口忽略受影响行数

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/controller/ClientController.java`
- `backend/src/main/java/com/cde/service/impl/AclServiceImpl.java`
- `backend/src/main/java/com/cde/service/impl/DomainServiceImpl.java`

**现象：**

多个更新或删除路径没有检查 `updateById`、`deleteById` 的受影响行数。`ClientController.update` 更新后直接 `selectById` 并解引用结果，传入不存在的 ID 时可能 NPE；ACL 和安全域更新删除也可能在资源不存在时返回成功或继续同步。

**影响：**

客户端无法区分“资源不存在”和“操作成功”，审计与排障也会被误导。部分路径会直接变成 500。

**建议：**

- 检查更新/删除返回值，未命中时返回 404 或业务错误。
- 更新后读取结果前先判空，避免 NPE。

### KI-047：登录失败的 401 被全局当作会话过期处理

**优先级：** 建议修改

**涉及位置：**

- `frontend/src/services/api.ts`
- `frontend/src/pages/Login.tsx`
- `backend/src/main/java/com/cde/exception/GlobalExceptionHandler.java`

**现象：**

后端登录失败会返回 HTTP 401 和 `AUTH_FAILED` 错误码，但前端 `request` 对所有 401 都直接清理 sessionStorage 并跳转 `/login`，没有先解析错误体。登录页输入错误密码时，后端返回的“密码错误”等业务提示可能被“认证已过期”替代，甚至触发页面重载。

**影响：**

用户无法获得准确登录失败原因，前端也难以区分“登录接口认证失败”和“已登录会话过期”。

**建议：**

- 对 `/auth/login` 的 401 保留后端错误体，不执行全局会话过期跳转。
- 或在 `request` 中按错误码区分 `AUTH_FAILED` 与 `UNAUTHORIZED`。

### KI-048：refresh token 契约不可用且文档、DTO、前端服务不一致

**优先级：** 建议修改

**涉及位置：**

- `docs/api.md`
- `frontend/src/services/api.ts`
- `backend/src/main/java/com/cde/dto/LoginResponse.java`
- `backend/src/main/java/com/cde/service/impl/AuthServiceImpl.java`

**现象：**

文档描述登录响应包含 `refreshToken`、`expiresIn`，刷新接口应提交 refresh token；但后端 `LoginResponse` 只有 `token` 和 `expires`，刷新逻辑实际校验旧 access token，过期后会直接拒绝。前端虽然导出了 `api.refreshToken()`，当前页面并未形成可用的 refresh token 流程。

**影响：**

Access token 过期后无法按文档刷新，调用方会误以为系统支持 refresh token 机制，实际只能重新登录。

**建议：**

- 要么实现真正的 refresh token 字段、存储与刷新流程，要么删除文档和前端服务中对 refresh token 的暗示。
- 统一字段名，例如明确使用 `expires` 或 `expiresIn`，避免接口契约漂移。

### KI-049：NetworkController 弱网模拟命令超时保护不可靠

**优先级：** 建议修改

**涉及位置：**

- `backend/src/main/java/com/cde/controller/NetworkController.java`

**现象：**

`executeCommand` 启动子进程后，先同步读取进程输出流，等输出流关闭后才调用 `waitFor(timeout)`。如果 `sh`、`tc` 或 `ip` 子进程卡住且 stdout 不关闭，请求线程会阻塞在读取输出阶段，30 秒超时保护无法生效。

**影响：**

管理员反复调用弱网模拟接口时，卡住的子进程可能长期占用后端请求线程，造成管理接口不可用。

**建议：**

- 先用 `waitFor(timeout)` 控制进程生命周期，再读取已收集输出；或异步消费 stdout/stderr。
- 超时后确保 `destroyForcibly()` 并回收进程资源。