-- Demo seed data for the cross-domain exchange project.
-- Password hashes are BCrypt and match the demo credentials in README.md.

INSERT INTO sys_domain (id, domain_code, domain_name, status) VALUES
  (1, 'admin', 'Administration Domain', 1),
  (2, 'medical', 'Medical Domain', 1),
  (3, 'gov', 'Government Domain', 1),
  (4, 'enterprise', 'Enterprise Domain', 1);

-- admin / admin123
-- producer_swu / 123456
-- consumer_social / 123456
-- consumer_c / 123456
INSERT INTO sys_user (domain_id, username, password_hash, role_type, client_id) VALUES
  (1, 'admin',           '$2a$10$sZicYQ9PA1ouwJc5uGRjOOn4NTfCj6nqmbaJ/6LJbwL0T8B/R1Gle', 'admin', 'admin_001'),
  (2, 'producer_swu',    '$2a$10$bxzESBSX/icH5EonWyrbwubwAx3WxtoGhNxAqT2JQmhrQ3nmovpzO', 'producer', 'producer_swu_001'),
  (3, 'consumer_social', '$2a$10$bxzESBSX/icH5EonWyrbwubwAx3WxtoGhNxAqT2JQmhrQ3nmovpzO', 'consumer', 'consumer_social_001'),
  (4, 'consumer_c',      '$2a$10$bxzESBSX/icH5EonWyrbwubwAx3WxtoGhNxAqT2JQmhrQ3nmovpzO', 'consumer', 'consumer_c_001');

INSERT INTO sys_topic_acl (username, topic_filter, action, access_type) VALUES
  ('producer_swu', '/cross_domain/medical/hosp_swu/#', 'publish', 'allow'),
  ('consumer_social', '/cross_domain/medical/#', 'subscribe', 'allow'),
  ('admin', '/cross_domain/#', 'all', 'allow'),
  ('consumer_c', '/cross_domain/enterprise/#', 'subscribe', 'allow'),
  ('*', '#', 'all', 'deny');
