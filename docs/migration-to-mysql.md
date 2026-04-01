# H2 → MySQL 迁移指南

## 概述

本系统默认使用 H2 内存数据库（MySQL兼容模式），可快速迁移到 MySQL 8.0 生产环境。

## 迁移步骤

### 1. 创建 MySQL 数据库

```sql
CREATE DATABASE crossdomaindb CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 2. 导入表结构

`schema.sql` 使用 H2 MySQL兼容模式编写，语法完全兼容 MySQL 8.0。

```bash
mysql -u root -p crossdomaindb < backend/src/main/resources/db/schema.sql
```

### 3. 导入初始数据（可选）

```bash
mysql -u root -p crossdomaindb < backend/src/main/resources/db/data.sql
```

### 4. 切换 Spring Boot Profile

#### 方式一：命令行参数
```bash
java -jar cross-domain-exchange.jar --spring.profiles.active=mysql
```

#### 方式二：Docker 环境变量
```yaml
# docker-compose.yml
backend:
  environment:
    - SPRING_PROFILES_ACTIVE=mysql
    - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/crossdomaindb
    - SPRING_DATASOURCE_USERNAME=root
    - SPRING_DATASOURCE_PASSWORD=root123
```

### 5. 验证

访问 `/actuator/health` 确认数据源连接正常。

## 注意事项

- `application-mysql.yml` 已预配置 MySQL 连接参数
- `pom.xml` 已包含 `mysql-connector-j` 依赖（optional）
- 无需修改任何业务代码
