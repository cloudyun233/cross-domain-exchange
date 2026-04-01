package com.cde.service.auth;

import java.util.List;

/**
 * 认证插件接口 (论文4.4.3: AuthPlugin)
 */
public interface AuthPlugin {
    boolean authenticate(String clientId, String credentials);
    List<String> getAuthorizedTopics(String clientId);
}
