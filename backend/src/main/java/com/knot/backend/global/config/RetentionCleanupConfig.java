package com.knot.backend.global.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RetentionCleanupProperties.class)
public class RetentionCleanupConfig {

    @Bean
    @ConditionalOnProperty(prefix = "knot.retention", name = "enabled", havingValue = "true")
    public ThreadPoolTaskExecutor retentionCleanupExecutor(RetentionCleanupProperties properties) {
        properties.validate();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("retention-cleanup-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
