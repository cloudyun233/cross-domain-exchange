-- Demo seed data for the cross-domain exchange project.
-- Password hashes are BCrypt and match the demo credentials in README.md.

INSERT INTO sys_domain (id, parent_id, domain_code, domain_name, status) VALUES
  (1, NULL, 'medical', '医疗域', 1),
  (2, 1, 'swh', '西南医院', 1),
  (3, NULL, 'gov', '政务域', 1),
  (4, NULL, 'enterprise', '企业域', 1);

-- admin / admin123
-- producer_medical_swh / 123456
-- consumer_social / 123456
-- consumer_medical_swh / 123456
INSERT INTO sys_user (domain_id, username, password_hash, role_type, client_id) VALUES
  (NULL, 'admin',                '$2a$10$sZicYQ9PA1ouwJc5uGRjOOn4NTfCj6nqmbaJ/6LJbwL0T8B/R1Gle', 'admin',    'admin_001'),
  (2,    'producer_medical_swh', '$2a$10$bxzESBSX/icH5EonWyrbwubwAx3WxtoGhNxAqT2JQmhrQ3nmovpzO', 'producer', 'producer_medical_swh_001'),
  (3,    'consumer_social',      '$2a$10$bxzESBSX/icH5EonWyrbwubwAx3WxtoGhNxAqT2JQmhrQ3nmovpzO', 'consumer', 'consumer_social_001'),
  (2,    'consumer_medical_swh', '$2a$10$bxzESBSX/icH5EonWyrbwubwAx3WxtoGhNxAqT2JQmhrQ3nmovpzO', 'consumer', 'consumer_medical_swh_001');

INSERT INTO sys_topic_acl (username, topic_filter, action, access_type) VALUES
  ('producer_medical_swh', '/cross_domain/medical/swh/#', 'publish', 'allow'),
  ('consumer_social', '/cross_domain/medical/#', 'subscribe', 'allow'),
  ('admin', '/cross_domain/#', 'all', 'allow'),
  ('consumer_medical_swh', '/cross_domain/medical/swh/#', 'subscribe', 'allow'),
  ('*', '#', 'all', 'deny');
