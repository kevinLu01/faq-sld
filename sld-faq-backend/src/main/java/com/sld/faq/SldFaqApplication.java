package com.sld.faq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * SLD FAQ 智能整理助手 — 后端启动类
 */
@SpringBootApplication
@MapperScan("com.sld.faq.module.*.mapper")
public class SldFaqApplication {

    public static void main(String[] args) {
        SpringApplication.run(SldFaqApplication.class, args);
    }
}
