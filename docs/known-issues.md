# 已知问题清单

本文记录 2026-05-19 基于用户使用流程完成的全仓库审查结果，并于 2026-05-22 再次复核补充。审查路径覆盖登录、发布、订阅、ACL 管理、域管理、用户管理、审计、监控与 Docker/EMQX 部署配置。

> 当前状态：后端现有测试通过，前端可完成生产构建。以下问题属于已知风险，尚未在代码中修复。

## 问题总览

| 编号 | 优先级 | 问题 | 主要影响 | 状态 |
| --- | --- | --- | --- | --- |
| KI-001 | 必须修复 | H2 Console 在 Docker 默认运行路径中公开可访问 | 数据库控制台可能被外部访问 | 待处理 |
| KI-002 | 必须修复 | JWT、EMQX API Key、集群 Cookie、MySQL 密码硬编码进仓库 | 可伪造令牌或操作 Broker 管理 API | 待处理 |
| KI-003 | 必须修复 | 用户删除、角色变更、域禁用后旧 JWT 仍可继续使用 | 账号撤权不会立即生效 | 待处理 |
| KI-004 | 必须修复 | 安全域可被改出父子环，登录时可能陷入循环 | 影响登录与域路径生成 | 待处理 |
| KI-005 | 建议修改 | EMQX Webhook 审计入口无来源校验 | 审计日志可被伪造或刷入大量噪声 | 待处理 |
| KI-006 | 建议修改 | 发布/订阅消息正文会进入服务端日志 | 敏感业务数据可能落盘 | 待处理 |
| KI-007 | 建议修改 | Docker 场景允许 TLS 降级到明文 TCP，且明文端口对宿主开放 | MQTT 传输链路安全性不足 | 待处理 |
| KI-008 | 建议修改 | 前端主包较大，首屏加载压力偏高 | 首次访问体验变慢 | 待处理 |
| KI-009 | 建议修改 | SSE 重连耗尽后无法在页面内恢复 | 订阅页实时消息通道只能靠刷新页面恢复 | 待处理 |
| KI-010 | 建议修改 | 登录后重定向直接读取 sessionStorage 用户信息 | 登录跳转依赖两套用户数据源 | 待处理 |
| KI-011 | 必须修复 | 刷新或重连后已恢复订阅无法取消 | 用户无法取消已记忆的订阅 | 待处理 |
| KI-012 | 必须修复 | sessionStorage 中用户数据损坏会导致前端崩溃 | 用户只能手动清理浏览器存储恢复 | 待处理 |
| KI-013 | 必须修复 | 非法或过期 JWT 不会稳定触发退出登录 | 用户可能卡在已登录但接口不可用状态 | 待处理 |
| KI-014 | 必须修复 | 安全域编码未校验就拼入 MQTT 主题路径 | 主题树、发布、订阅和 ACL 匹配可能异常 | 待处理 |
| KI-015 | 建议修改 | 连接状态检查把后端和 EMQX 状态耦合 | 单路失败会误报另一服务离线 | 待处理 |
| KI-016 | 建议修改 | 审计日志页面缺少 Schema 校验失败类型映射 | 事件显示、筛选和高亮不完整 | 待处理 |
| KI-017 | 建议修改 | 审计日志 PDF 导出一次性加载全量日志 | 日志增长后可能超时或造成内存压力 | 待处理 |

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

## 存在性验证（2026-05-22）

| 编号 | 验证状态 | 证据 | 说明 |
| --- | --- | --- | --- |
| KI-001 | 仍存在 | `application.yml`、`SecurityConfig.java`、`docker-compose.yml` | H2 Console 仍启用并允许远程访问，安全配置仍放行 `/h2-console/**`，Docker 后端仍映射 `8080`。 |
| KI-002 | 仍存在 | `application.yml`、`application-mysql.yml`、`dockerrun/emqx/emqx.conf`、`README.md` | JWT 密钥、EMQX API Key/Secret、集群 Cookie、MySQL 示例密码仍在仓库内。 |
| KI-003 | 仍存在 | `JwtAuthenticationFilter.java`、`JwtUtil.java`、`ClientController.java`、`AuthServiceImpl.java` | 鉴权仍主要依赖 JWT 签名与过期时间，缺少用户状态、角色版本和撤权校验。 |
| KI-004 | 仍存在 | `DomainManage.tsx`、`DomainServiceImpl.java`、`AuthServiceImpl.java` | 前端只排除当前域作为父域，后端创建和更新未防环，域路径遍历未记录 visited。 |
| KI-005 | 仍存在 | `SecurityConfig.java`、`WebhookController.java`、`AuditServiceImpl.java` | `/api/webhook/**` 仍公开放行，Webhook 入库前没有来源、签名或共享密钥校验。 |
| KI-006 | 仍存在 | `MqttClientService.java`、`SubscribeServiceImpl.java` | 订阅接收和 SSE 推送日志仍记录 payload 前 200 字符。 |
| KI-007 | 仍存在 | `application-docker.yml`、`MqttClientService.java`、`docker-compose.yml`、`emqx.conf` | Docker Profile 仍信任任意证书，TLS 失败仍降级 TCP，1883 明文端口仍开放。 |
| KI-008 | 仍存在 | `App.tsx`、`Dashboard.tsx`、`vite.config.ts`、`dist/index.html` | 路由页面和 ECharts 仍静态导入，Vite 未配置手动分包，构建产物仍以单个主 JS 为主。 |
| KI-009 | 仍存在 | `SubscribeContext.tsx` | SSE 重试耗尽后仍关闭重连开关，后续主动连接仍会触发“组件已卸载”错误路径。 |
| KI-010 | 仍存在 | `Login.tsx`、`AuthContext.tsx` | 登录跳转仍直接解析 `sessionStorage.user`，`login()` 仍不返回标准化用户信息。 |
| KI-011 | 仍存在 | `SubscribeContext.tsx`、`Subscribe.tsx` | 会话状态只恢复 `subscribedTopics`，取消订阅仍依赖 `activeTopic`。 |
| KI-012 | 仍存在 | `AuthContext.tsx` | 初始化仍直接 `JSON.parse(sessionStorage.user)`，没有解析失败兜底。 |
| KI-013 | 仍存在 | `JwtAuthenticationFilter.java`、`SecurityConfig.java`、`AuthContext.tsx`、`api.ts` | 无效 JWT 未稳定返回 401，前端 profile 刷新失败也未清理会话。 |
| KI-014 | 仍存在 | `DomainServiceImpl.java`、`DomainManage.tsx` | `domainCode` 仍未校验就入库并拼入 MQTT 主题路径。 |
| KI-015 | 仍存在 | `ConnectionStatus.tsx` | 后端和 EMQX 健康检查仍由同一个 `Promise.all` 统一失败处理。 |
| KI-016 | 仍存在 | `AuditLog.tsx`、`TopicController.java` | 后端会记录 `json_schema_validate_fail`，前端审计映射仍缺少该类型。 |
| KI-017 | 仍存在 | `AuditServiceImpl.java` | PDF 导出仍一次性 `selectList()` 全量日志并同步生成 PDF。 |

## 已完成验证

```bash
cd backend
.\mvnw.cmd test
```

结果：后端 9 个测试全部通过。

```bash
cd frontend
npm run build
```

结果：前端生产构建成功，但存在主包体积过大的构建警告。

## 后续建议

优先处理 KI-001 到 KI-004，这几项直接影响认证、撤权和部署安全。KI-005 到 KI-008 可作为下一轮安全加固和体验优化任务推进。
