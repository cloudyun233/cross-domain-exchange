-- ============================================
-- 演示预设数据 (对齐需求文档演示流程)
-- 密码统一使用 BCrypt 加密
-- ============================================

-- 安全域
INSERT INTO sys_domain (domain_code, domain_name, status) VALUES
('admin',      '管理域',   1),
('medical',    '医疗域',   1),
('gov',        '政务域',   1),
('enterprise', '企业域',   1);

-- 用户 (密码: admin123 和 123456 的BCrypt哈希)
-- admin123 -> $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6CQmSRFN.auW8G2KOcA1Kl60W
-- 123456  -> $2a$10$Wv1FvELwQqOa9Y3f1NxOb.oWGHlRVOq4Kf7KY1qpXh7KIi6vKYpHe
INSERT INTO sys_user (domain_id, client_id, password_hash, role_type) VALUES
(1, 'admin',           '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6CQmSRFN.auW8G2KOcA1Kl60W', 'admin'),
(2, 'producer_swu',    '$2a$10$Wv1FvELwQqOa9Y3f1NxOb.oWGHlRVOq4Kf7KY1qpXh7KIi6vKYpHe', 'producer'),
(3, 'consumer_social', '$2a$10$Wv1FvELwQqOa9Y3f1NxOb.oWGHlRVOq4Kf7KY1qpXh7KIi6vKYpHe', 'consumer'),
(4, 'consumer_c',      '$2a$10$Wv1FvELwQqOa9Y3f1NxOb.oWGHlRVOq4Kf7KY1qpXh7KIi6vKYpHe', 'consumer');

-- ACL规则 (对齐需求文档演示流程)
-- producer_swu: 只能发布到医疗域主题
INSERT INTO sys_topic_acl (client_id, topic_filter, action, access_type) VALUES
('producer_swu', '/cross_domain/medical/hosp_swu/#', 'publish', 'allow');

-- consumer_social: 只能订阅医疗域主题
INSERT INTO sys_topic_acl (client_id, topic_filter, action, access_type) VALUES
('consumer_social', '/cross_domain/medical/#', 'subscribe', 'allow');

-- admin: 可订阅所有主题(全域监控)
INSERT INTO sys_topic_acl (client_id, topic_filter, action, access_type) VALUES
('admin', '/cross_domain/#', 'all', 'allow');

-- consumer_c: 初始无权限 (用于演示动态ACL添加)

-- 默认兜底拒绝策略 (论文4.2.3: 默认拒绝所有未授权访问)
INSERT INTO sys_topic_acl (client_id, topic_filter, action, access_type) VALUES
('*', '#', 'all', 'deny');
