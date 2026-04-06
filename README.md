# 跨域数据交换系统

一个基于 MQTT 发布订阅模型的跨域数据交换演示项目，后端使用 Spring Boot，前端使用 React + Ant Design，Broker 使用 EMQX。

## 当前数据模型

- 安全域支持一层父子结构，当前内置：
  - `medical` 医疗域
  - `medical / swh` 西南医院
  - `gov` 政务域
  - `enterprise` 企业域
- 管理员不再占用单独“管理域”，`admin` 为全域用户，`domain_id` 为空。
- 域编码用于 topic 路径，建议使用英文或拼音。
- 域名称用于展示，可以直接使用中文。

## 演示账号

| 用户名 | 密码 | 角色 | 所属域 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 管理员 | 全域 |
| `producer_medical_swh` | `123456` | 生产者 | 医疗域 / 西南医院 |
| `consumer_social` | `123456` | 消费者 | 政务域 |
| `consumer_medical_swh` | `123456` | 消费者 | 医疗域 / 西南医院 |

## 主题与权限

- 生产者 `producer_medical_swh` 允许发布：`/cross_domain/medical/swh/#`
- 消费者 `consumer_social` 允许订阅：`/cross_domain/medical/#`
- 消费者 `consumer_medical_swh` 允许订阅：`/cross_domain/medical/swh/#`
- 管理员 `admin` 拥有：`/cross_domain/#`

## 界面变更

- 发布页和订阅页的域树都由后端查 `sys_domain` 表生成。
- 右上角用户角色、所属域通过后端 `/api/auth/me` 返回。
- 安全域管理页已支持父域配置。

## 本地启动

### 1. 启动 EMQX

```bash
docker-compose up -d emqx
```

### 2. 启动后端

```bash
cd backend
.\mvnw.cmd spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

## 默认内存库

- H2 初始化脚本：[backend/src/main/resources/db/schema.sql](/c:/Users/cloud/OneDrive/code/cross-domain-exchange/backend/src/main/resources/db/schema.sql)
- H2 初始化数据：[backend/src/main/resources/db/data.sql](/c:/Users/cloud/OneDrive/code/cross-domain-exchange/backend/src/main/resources/db/data.sql)

## 说明

- 这是最简实现，主题树直接映射域树节点。
- 如果后续要支持更深层级、业务主题模板或不同域的独立主题策略，建议再单独拆出主题模型。
