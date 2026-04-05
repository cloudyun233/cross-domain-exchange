package com.cde.service;

import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysUser;

/**
 * 鉴权服务接口 (论文4.5.3类图: AuthService)
 */
public interface AuthService {

    /** JWT令牌签发 */
    LoginResponse login(LoginRequest request);

    /** 令牌刷新 */
    LoginResponse refreshToken(String token);

    /**
     * ACL权限校验 (已禁用)
     * ACL校验由EMQX全权负责，后端不重复验证
     */
    // boolean checkACL(String clientId, String topic, String action);

    /** 获取当前用户信息 */
    SysUser getUserByUsername(String username);
}
