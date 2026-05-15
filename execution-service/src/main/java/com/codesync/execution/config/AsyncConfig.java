package com.codesync.execution.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "executionTaskExecutor")
    public Executor executionTaskExecutor(
            @Value("${codesync.execution.async.core-pool-size:2}") int corePoolSize,
            @Value("${codesync.execution.async.max-pool-size:4}") int maxPoolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("execution-worker-");
        executor.initialize();
        return executor;
    }
}
