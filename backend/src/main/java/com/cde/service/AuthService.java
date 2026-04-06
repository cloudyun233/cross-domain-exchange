package com.cde.service;

import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysUser;

/**
 * 认证服务接口
 */
public interface AuthService {

    /** JWT 令牌签发 */
    LoginResponse login(LoginRequest request);

    /** 令牌刷新 */
    LoginResponse refreshToken(String token);

    /** 获取当前用户实体 */
    SysUser getUserByUsername(String username);

    /** 获取当前用户展示信息 */
    LoginResponse getCurrentUserProfile(String username, String token);
}
