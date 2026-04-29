package com.cde.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域（CORS）配置，适配前后端分离架构。
 *
 * <p>前端开发服务器与后端 API 通常部署在不同源（不同端口或域名），
 * 浏览器同源策略会阻止跨域请求。此配置允许前端以携带凭证的方式
 * 访问所有 {@code /api/**} 端点，预检请求缓存 3600 秒。
 *
 * <p>注意：{@code allowedOriginPatterns("*")} 适用于开发环境，
 * 生产环境应替换为具体的前端域名以收紧安全策略。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 注册 CORS 映射规则，仅对 {@code /api/**} 路径生效。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
