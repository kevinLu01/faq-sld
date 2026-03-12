package com.sld.faq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 * <p>
 * 提供名为 "faqTaskExecutor" 的线程池，用于 FAQ 生成等后台任务。
 * 线程数和队列容量通过配置文件 {@code async.faq-task.*} 调整。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${async.faq-task.core-pool-size:4}")
    private int corePoolSize;

    @Value("${async.faq-task.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${async.faq-task.queue-capacity:100}")
    private int queueCapacity;

    /**
     * FAQ 任务专用线程池
     * <p>
     * 拒绝策略使用 {@link ThreadPoolExecutor.CallerRunsPolicy}：
     * 队列满时由调用线程直接执行，避免任务丢失。
     */
    @Bean("faqTaskExecutor")
    public Executor faqTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("faq-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
