package com.cde.config;

import com.cde.security.JwtAuthenticationFilter;
import com.cde.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置。
 *
 * <p>整体采用无状态 JWT 认证架构：禁用 CSRF（REST API 无需跨站请求伪造防护），
 * 会话策略设为 STATELESS（不创建 HttpSession，完全依赖 JWT Token 鉴权）。
 *
 * <p>公开访问端点包括：登录/刷新令牌、状态查询、Webhook 回调、H2 控制台及 Actuator；
 * 其余所有请求均需经过 {@link JwtAuthenticationFilter} 认证。
 *
 * <p>注意：H2 控制台仅在开发环境使用，生产环境应通过 Profile 控制其访问权限。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** JWT 认证过滤器，在 UsernamePasswordAuthenticationFilter 之前执行 Token 校验 */
    private final JwtAuthenticationFilter jwtFilter;
    private final ObjectMapper objectMapper;

    /**
     * 密码编码器，使用 BCrypt 算法进行单向哈希。
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置 HTTP 安全过滤链。
     *
     * <ul>
     *   <li>禁用 CSRF：前后端分离架构下使用无状态 Token 认证，无需 CSRF 防护</li>
     *   <li>无状态会话：不创建服务端 Session，每次请求通过 JWT 独立鉴权</li>
     *   <li>同源 frame-options：允许 H2 控制台在 iframe 中正常显示</li>
     *   <li>公开端点：登录、刷新令牌、状态、Webhook、H2 控制台、Actuator</li>
     *   <li>JWT 过滤器：在标准认证过滤器之前插入自定义 JWT 校验逻辑</li>
     * </ul>
     *
     * @param http HttpSecurity 构建器
     * @return 配置完成的 SecurityFilterChain
     * @throws Exception 配置过程中的异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.frameOptions(fo -> fo.sameOrigin()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), ApiResponse.nack("UNAUTHORIZED", "请先登录"));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), ApiResponse.nack("ACCESS_DENIED", "无权访问该资源"));
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/status/**").permitAll()
                .requestMatchers("/api/webhook/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
