package com.graduation.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置：文档解析在独立线程池执行，不阻塞上传请求
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("parseExecutor")
    public ThreadPoolTaskExecutor parseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-parse-");
        return executor;
    }

    /** 问答流式处理线程池（与解析隔离，互不阻塞） */
    @Bean("chatExecutor")
    public ThreadPoolTaskExecutor chatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-");
        return executor;
    }
}
