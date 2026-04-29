package com.cde.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置，当前仅注册分页插件。
 *
 * <p>分页插件通过拦截 SQL 查询自动拼接 {@code LIMIT/OFFSET} 子句，
 * 使 {@code Page} 对象的分页查询对业务代码透明。
 *
 * <p>注意：当前指定 {@link DbType#H2} 适配开发环境使用的 H2 数据库；
 * 若生产环境切换为 MySQL/PostgreSQL 等数据库，需同步修改此处的 DbType，
 * 否则分页 SQL 方言可能不正确。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器，包含分页内拦截器。
     *
     * @return 配置好分页插件的 MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        return interceptor;
    }
}
