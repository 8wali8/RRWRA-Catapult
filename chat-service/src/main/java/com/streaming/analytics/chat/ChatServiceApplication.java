package com.streaming.analytics.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Enterprise Chat Service Application
 * 
 * High-performance reactive chat processing service with:
 * - Real-time WebSocket connections
 * - Reactive Spring WebFlux
 * - Redis caching and pub/sub
 * - Kafka message streaming
 * - PostgreSQL persistence
 * - Eureka service discovery
 * - Advanced rate limiting
 * - Circuit breaker patterns
 * - Comprehensive monitoring
 */
@SpringBootApplication
@EnableEurekaClient
@EnableWebFlux
@EnableJpaRepositories
@EnableRedisRepositories
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableCaching
public class ChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}