package com.cde.service;

import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;

/**
 * 鉴权服务接口 (论文4.5.3类图: AuthService)
 */
public interface AuthService {

    /** JWT令牌签发 */
    LoginResponse login(LoginRequest request);

    /** 令牌刷新 */
    LoginResponse refreshToken(String token);

    /** ACL权限校验 */
    boolean checkACL(String clientId, String topic, String action);
}
