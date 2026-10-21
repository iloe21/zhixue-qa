package com.graduation.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 基于RAG的智能学习问答系统 - 启动类
 *
 * 排除 UserDetailsServiceAutoConfiguration：
 * 本系统用自定义 JWT 认证，不需要 Spring Security 默认的内存用户，
 * 排除后启动日志不再出现 "Using generated security password" 警告
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
