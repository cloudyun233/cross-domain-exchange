-- ============================================
-- 跨域数据交换系统 - 数据库表结构
-- 兼容 H2 (MODE=MySQL) 与 MySQL 8.0
-- ============================================

-- 安全域表
CREATE TABLE IF NOT EXISTS sys_domain (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_code  VARCHAR(64)  NOT NULL UNIQUE COMMENT '域编码(如 gov, medical)',
    domain_name  VARCHAR(128) NOT NULL COMMENT '域名称',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态(1:启用, 0:禁用)'
);

-- 客户端/用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain_id      BIGINT       NOT NULL COMMENT '所属安全域ID',
    username       VARCHAR(128) NOT NULL UNIQUE COMMENT '用户账号(如 user_001)',
    password_hash  VARCHAR(256) NOT NULL COMMENT '加密后密码',
    role_type      VARCHAR(32)  NOT NULL COMMENT '角色(producer/consumer/admin)',
    CONSTRAINT fk_user_domain FOREIGN KEY (domain_id) REFERENCES sys_domain(id)
);

-- 访问控制规则表
CREATE TABLE IF NOT EXISTS sys_topic_acl (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id     VARCHAR(128) NOT NULL COMMENT '客户端ID(支持*通配)',
    topic_filter  VARCHAR(256) NOT NULL COMMENT '主题过滤器',
    action        VARCHAR(16)  NOT NULL COMMENT '动作(publish/subscribe/all)',
    access_type   VARCHAR(16)  NOT NULL COMMENT '访问类型(allow/deny)'
);

-- 审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id    VARCHAR(128) COMMENT '客户端ID',
    action_type  VARCHAR(32)  NOT NULL COMMENT '操作类型(connect/publish/subscribe/acl_deny)',
    detail       TEXT         COMMENT '详细信息',
    ip_address   VARCHAR(64)  NOT NULL DEFAULT '0.0.0.0' COMMENT '客户端IP',
    action_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作发生时间'
);
