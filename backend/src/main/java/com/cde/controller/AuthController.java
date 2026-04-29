package com.cde.controller;

import com.cde.dto.ApiResponse;
import com.cde.dto.LoginRequest;
import com.cde.dto.LoginResponse;
import com.cde.security.JwtUtil;
import com.cde.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 REST API
 *
 * <p>提供基于 JWT 身份认证接口：登录/刷新端点公开，/me 需携带有效 JWT。
 * <ul>
 *   <li>POST /api/auth/login    — 用户登录，验证凭据并签发 JWT</li>
 *   <li>POST /api/auth/refresh  — 刷新令牌，对旧令牌重新验证后签发新令牌</li>
 *   <li>GET  /api/auth/me       — 获取当前登录用户信息，从 JWT 中提取用户名后查询数据库</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录
     *
     * <p>验证用户名和密码，验证通过后签发 JWT 访问令牌和刷新令牌。
     *
     * @param request 登录请求体，包含用户名和密码
     * @return 登录响应，包含 JWT 令牌及用户基本信息
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.ok("登录成功", response);
    }

    /**
     * 刷新令牌
     *
     * <p>从请求头中提取旧 JWT 令牌，重新验证用户身份后签发新令牌。
     * 若旧令牌已过期或无效则刷新失败。
     *
     * @param authHeader Authorization 请求头，格式为 "Bearer {token}"
     * @return 新的登录响应，包含刷新后的 JWT 令牌
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        LoginResponse response = authService.refreshToken(token);
        return ApiResponse.ok("令牌刷新成功", response);
    }

    /**
     * 获取当前登录用户信息
     *
     * <p>从 JWT 令牌中提取用户名，再查询数据库获取完整用户资料。
     * 令牌解析失败时返回错误响应。
     *
     * @param authHeader Authorization 请求头，格式为 "Bearer {token}"
     * @return 当前用户的登录响应信息
     */
    @GetMapping("/me")
    public ApiResponse<LoginResponse> getCurrentUser(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String username = jwtUtil.getUsernameFromToken(token);
            LoginResponse response = authService.getCurrentUserProfile(username, token);
            return ApiResponse.ok("获取用户信息成功", response);
        } catch (Exception e) {
            return ApiResponse.fail(e.getMessage());
        }
    }
}
