package com.cde;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 跨域交换平台 Spring Boot 启动入口。
 *
 * <p>通过 {@code @MapperScan("com.cde.mapper")} 启用 MyBatis Mapper 接口扫描，
 * 避免在每个 Mapper 上重复添加 {@code @Mapper} 注解；
 * 通过 {@code @EnableScheduling} 启用 Spring 定时任务调度能力，
 * 支持系统中基于 {@code @Scheduled} 的周期性任务执行。
 */
@SpringBootApplication
@MapperScan("com.cde.mapper")
@EnableScheduling
public class CrossDomainExchangeApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrossDomainExchangeApplication.class, args);
    }
}
