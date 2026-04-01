package com.cde;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.cde.mapper")
@EnableScheduling
public class CrossDomainExchangeApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrossDomainExchangeApplication.class, args);
    }
}
