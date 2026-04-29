package com.cde.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器。
 * <p>
 * 继承 OncePerRequestFilter，确保每个请求仅过滤一次。从请求中提取 JWT 令牌，
 * 验证其有效性后将用户信息写入 Spring Security 上下文，完成无状态认证。
 * <p>
 * 令牌解析优先级：Authorization 头 (Bearer) > token 查询参数 > access_token 查询参数。
 * 查询参数方式主要用于 MQTT WebSocket 客户端等无法设置请求头的场景。
 * <p>
 * 线程安全：本过滤器无实例状态变更，SecurityContext 通过 SecurityContextHolder
 * 基于 ThreadLocal 管理，每个请求线程独立，不存在并发问题。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    /**
     * 执行 JWT 认证过滤。
     * <p>
     * 解析令牌 -> 验证签名与有效期 -> 提取用户名和角色 -> 构建 Authentication 写入 SecurityContext。
     * 无论令牌是否存在或有效，均会继续执行后续过滤器，由 Spring Security 授权机制决定是否拒绝访问。
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain  过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            if (jwtUtil.validateToken(token)) {
                String usernameFromToken = jwtUtil.getUsernameFromToken(token);
                String roleType = jwtUtil.getRoleTypeFromToken(token);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + roleType.toUpperCase())
                );

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(usernameFromToken, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中解析 JWT 令牌。
     * <p>
     * 按优先级依次尝试：Authorization 请求头 (Bearer 前缀)、token 查询参数、access_token 查询参数。
     *
     * @param request HTTP 请求
     * @return 解析出的令牌字符串，未找到时返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }

        return request.getParameter("access_token");
    }
}
