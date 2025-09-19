package com.streamsense.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

/**
 * Netflix OSS Config Server - Centralized Configuration Management
 * 
 * Provides externalized configuration for all microservices in the streaming analytics platform.
 * Supports Git, Vault, and JDBC backends with encryption/decryption capabilities.
 * 
 * Key Features:
 * - Environment-specific configurations (dev, staging, prod)
 * - Real-time configuration refresh without service restart
 * - Configuration encryption for sensitive data
 * - Git-based version control for configuration history
 * - Service discovery integration with Eureka
 */
@SpringBootApplication
@EnableConfigServer
@EnableEurekaClient
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}