package com.codesync.execution.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Bean
    @ConditionalOnProperty(name = "codesync.execution.rabbitmq.enabled", havingValue = "true")
    public Queue executionJobsQueue(
            @Value("${codesync.execution.rabbitmq.queue-name:execution.jobs}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean(name = "rabbitHealthIndicator")
    @ConditionalOnProperty(name = "codesync.execution.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
    public HealthIndicator rabbitHealthIndicatorFallback() {
        return () -> Health.up()
                .withDetail("mode", "async-fallback")
                .withDetail("rabbitmqEnabled", false)
                .build();
    }
}
