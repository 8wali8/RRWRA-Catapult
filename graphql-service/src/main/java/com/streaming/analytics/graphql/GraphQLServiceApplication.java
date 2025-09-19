package com.streaming.analytics.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enterprise GraphQL Service Application
 * 
 * Unified GraphQL API Gateway providing:
 * - Single endpoint for all streaming analytics data
 * - Advanced query optimization with DataLoader
 * - Real-time subscriptions for live data
 * - Microservice aggregation via Feign clients
 * - Intelligent caching strategies
 * - Schema stitching and federation
 * - Rate limiting and security
 * - Comprehensive monitoring
 */
@SpringBootApplication
@EnableEurekaClient
@EnableFeignClients
@EnableJpaRepositories
@EnableCaching
@EnableAsync
@EnableTransactionManagement
public class GraphQLServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphQLServiceApplication.class, args);
    }
}