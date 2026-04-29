package com.cde.service;

import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.entity.SysUser;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * JWT令牌签发
     *
     * <p>验证用户名/密码后签发JWT，payload包含username、clientId、
     * domainCode（域路径编码，如"gov/minzheng"）和roleType四个声明。</p>
     */
    LoginResponse login(LoginRequest request);

    /**
     * 令牌刷新
     *
     * <p>验证旧令牌有效性后，从数据库重新加载用户信息并签发新令牌，
     * 确保域路径和角色等声明与最新数据一致。</p>
     *
     * @param token 即将过期的旧JWT令牌
     */
    LoginResponse refreshToken(String token);

    /** 获取当前用户实体 */
    SysUser getUserByUsername(String username);

    /**
     * 获取当前用户展示信息
     *
     * <p>根据用户名从数据库加载完整信息，结合已有令牌构建包含
     * 域编码、域名称、角色等展示字段的响应对象。</p>
     */
    LoginResponse getCurrentUserProfile(String username, String token);
}
