package com.streamsense.config.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration Management Service Implementation
 * Provides centralized configuration management with versioning and audit capabilities
 */
@Service
@Slf4j
public class ConfigurationManagementService {

    @Value("${config.default.environment:development}")
    private String defaultEnvironment;

    @Value("${config.cache.ttl:300}")
    private long cacheTimeToLive;

    // In-memory configuration store (in production, this would be backed by a database)
    private final Map<String, Map<String, Map<String, Object>>> configStore = new ConcurrentHashMap<>();
    private final Map<String, List<ConfigurationHistory>> configHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> configCacheTimestamps = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeDefaultConfigurations() {
        log.info("Initializing default configurations");
        
        // Initialize default configurations for each service
        createDefaultConfiguration("api-gateway", "development");
        createDefaultConfiguration("chat-service", "development");
        createDefaultConfiguration("video-service", "development");
        createDefaultConfiguration("ml-engine", "development");
        createDefaultConfiguration("sentiment-service", "development");
        createDefaultConfiguration("recommendation-service", "development");
        createDefaultConfiguration("eureka-server", "development");
        
        log.info("Default configurations initialized for {} services", configStore.size());
    }

    /**
     * Get configuration for a specific service and environment
     */
    public Mono<Map<String, Object>> getConfiguration(String service, String environment, String version) {
        return Mono.fromCallable(() -> {
            log.debug("Retrieving configuration for {}/{}/{}", service, environment, version);
            
            Map<String, Map<String, Object>> serviceConfigs = configStore.get(service);
            if (serviceConfigs == null) {
                log.warn("No configuration found for service: {}", service);
                return createEmptyConfiguration(service, environment);
            }
            
            Map<String, Object> config = serviceConfigs.get(environment);
            if (config == null) {
                log.warn("No configuration found for {}/{}", service, environment);
                return createEmptyConfiguration(service, environment);
            }
            
            // Add metadata
            Map<String, Object> result = new HashMap<>(config);
            result.put("_metadata", Map.of(
                "service", service,
                "environment", environment,
                "version", version,
                "lastUpdated", LocalDateTime.now(),
                "status", "SUCCESS"
            ));
            
            return result;
        });
    }

    /**
     * Update configuration for a service and environment
     */
    public Mono<String> updateConfiguration(String service, String environment, Map<String, Object> configuration) {
        return Mono.fromCallable(() -> {
            log.debug("Updating configuration for {}/{}", service, environment);
            
            // Get existing configuration for history
            Map<String, Object> oldConfig = configStore
                    .computeIfAbsent(service, k -> new ConcurrentHashMap<>())
                    .get(environment);
            
            // Update configuration
            configStore.get(service).put(environment, new HashMap<>(configuration));
            
            // Update cache timestamp
            String cacheKey = service + ":" + environment;
            configCacheTimestamps.put(cacheKey, System.currentTimeMillis());
            
            // Record history
            recordConfigurationHistory(service, environment, oldConfig, configuration);
            
            log.info("Configuration updated successfully for {}/{}", service, environment);
            return "Configuration updated successfully";
        });
    }

    /**
     * Get all configurations for a service across environments
     */
    public Mono<Map<String, Map<String, Object>>> getAllConfigurations(String service) {
        return Mono.fromCallable(() -> {
            log.debug("Retrieving all configurations for service: {}", service);
            
            Map<String, Map<String, Object>> serviceConfigs = configStore.get(service);
            if (serviceConfigs == null) {
                return Map.of();
            }
            
            // Add metadata to each environment configuration
            Map<String, Map<String, Object>> result = new HashMap<>();
            serviceConfigs.forEach((env, config) -> {
                Map<String, Object> configWithMetadata = new HashMap<>(config);
                configWithMetadata.put("_metadata", Map.of(
                    "environment", env,
                    "lastUpdated", LocalDateTime.now(),
                    "configCount", config.size()
                ));
                result.put(env, configWithMetadata);
            });
            
            return result;
        });
    }

    /**
     * Create configuration profile
     */
    public Mono<String> createProfile(String service, String environment, String profileName, Map<String, Object> profileConfig) {
        return Mono.fromCallable(() -> {
            log.debug("Creating profile {} for {}/{}", profileName, service, environment);
            
            Map<String, Object> config = configStore
                    .computeIfAbsent(service, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(environment, k -> new ConcurrentHashMap<>());
            
            // Create profile section
            Map<String, Object> profiles = (Map<String, Object>) config.computeIfAbsent("profiles", k -> new ConcurrentHashMap<>());
            profiles.put(profileName, profileConfig);
            
            log.info("Profile {} created for {}/{}", profileName, service, environment);
            return "Profile created successfully";
        });
    }

    /**
     * Refresh configuration cache
     */
    public Mono<String> refreshCache(String service) {
        return Mono.fromCallable(() -> {
            log.debug("Refreshing cache for service: {}", service != null ? service : "all");
            
            if (service != null) {
                // Refresh cache for specific service
                configCacheTimestamps.entrySet().removeIf(entry -> entry.getKey().startsWith(service + ":"));
                log.info("Cache refreshed for service: {}", service);
            } else {
                // Refresh all caches
                configCacheTimestamps.clear();
                log.info("All configuration caches refreshed");
            }
            
            return "Cache refreshed successfully";
        });
    }

    /**
     * Get configuration history
     */
    public Mono<Map<String, Object>> getConfigurationHistory(String service, String environment, int limit) {
        return Mono.fromCallable(() -> {
            log.debug("Retrieving configuration history for {}/{}", service, environment);
            
            String historyKey = service + ":" + environment;
            List<ConfigurationHistory> history = configHistory.getOrDefault(historyKey, new ArrayList<>());
            
            // Limit results and sort by timestamp
            List<ConfigurationHistory> limitedHistory = history.stream()
                    .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                    .limit(limit)
                    .toList();
            
            return Map.of(
                "service", service,
                "environment", environment,
                "historyCount", history.size(),
                "history", limitedHistory
            );
        });
    }

    /**
     * Refresh specific service configuration
     */
    public Mono<String> refreshServiceConfiguration(String service, String environment) {
        return Mono.fromCallable(() -> {
            log.debug("Refreshing configuration for {}/{}", service, environment);
            
            String cacheKey = service + ":" + environment;
            configCacheTimestamps.remove(cacheKey);
            
            // In a real implementation, this would reload from the configuration source
            log.info("Configuration refreshed for {}/{}", service, environment);
            return "Configuration refreshed successfully";
        });
    }

    /**
     * Validate configuration
     */
    public Mono<String> validateConfiguration(String validationRequest) {
        return Mono.fromCallable(() -> {
            log.debug("Validating configuration: {}", validationRequest);
            
            // Parse validation request: "service:environment:configKey"
            String[] parts = validationRequest.split(":");
            if (parts.length < 3) {
                return "Invalid validation request format";
            }
            
            String service = parts[0];
            String environment = parts[1];
            String configKey = parts[2];
            
            Map<String, Object> config = configStore
                    .getOrDefault(service, Map.of())
                    .getOrDefault(environment, Map.of());
            
            boolean isValid = config.containsKey(configKey);
            String result = isValid ? "VALID" : "INVALID";
            
            log.info("Configuration validation for {}/{}/{}: {}", service, environment, configKey, result);
            return result;
        });
    }

    // Private helper methods

    private void createDefaultConfiguration(String service, String environment) {
        Map<String, Object> defaultConfig = new HashMap<>();
        
        // Common configuration
        defaultConfig.put("server.port", getDefaultPort(service));
        defaultConfig.put("eureka.client.service-url.defaultZone", "http://localhost:8761/eureka");
        defaultConfig.put("management.endpoints.web.exposure.include", "*");
        defaultConfig.put("management.endpoint.health.show-details", "always");
        
        // Service-specific configuration
        switch (service) {
            case "api-gateway":
                defaultConfig.put("zuul.routes.chat-service.path", "/chat/**");
                defaultConfig.put("zuul.routes.video-service.path", "/video/**");
                defaultConfig.put("hystrix.command.default.execution.isolation.thread.timeoutInMilliseconds", 60000);
                break;
            case "chat-service":
                defaultConfig.put("spring.redis.host", "localhost");
                defaultConfig.put("spring.redis.port", 6379);
                defaultConfig.put("spring.kafka.bootstrap-servers", "localhost:9092");
                break;
            case "video-service":
                defaultConfig.put("yolo.model.path", "./yolov8n.pt");
                defaultConfig.put("video.processing.threads", 4);
                break;
            case "ml-engine":
                defaultConfig.put("transformers.model.sentiment", "roberta-base");
                defaultConfig.put("ml.cache.enabled", true);
                defaultConfig.put("ml.cache.ttl", 3600);
                break;
        }
        
        configStore.computeIfAbsent(service, k -> new ConcurrentHashMap<>()).put(environment, defaultConfig);
        log.debug("Default configuration created for {}/{}", service, environment);
    }

    private int getDefaultPort(String service) {
        return switch (service) {
            case "eureka-server" -> 8761;
            case "config-server" -> 8888;
            case "api-gateway" -> 8080;
            case "chat-service" -> 8081;
            case "video-service" -> 8082;
            case "ml-engine" -> 8083;
            case "sentiment-service" -> 8084;
            case "recommendation-service" -> 8085;
            default -> 8090;
        };
    }

    private Map<String, Object> createEmptyConfiguration(String service, String environment) {
        return Map.of(
            "_metadata", Map.of(
                "service", service,
                "environment", environment,
                "status", "NOT_FOUND",
                "message", "Configuration not found"
            )
        );
    }

    private void recordConfigurationHistory(String service, String environment, 
                                            Map<String, Object> oldConfig, Map<String, Object> newConfig) {
        String historyKey = service + ":" + environment;
        List<ConfigurationHistory> history = configHistory.computeIfAbsent(historyKey, k -> new ArrayList<>());
        
        ConfigurationHistory record = ConfigurationHistory.builder()
                .timestamp(LocalDateTime.now())
                .service(service)
                .environment(environment)
                .oldConfig(oldConfig)
                .newConfig(newConfig)
                .action("UPDATE")
                .build();
        
        history.add(record);
        
        // Keep only last 100 records per service/environment
        if (history.size() > 100) {
            history.removeFirst();
        }
    }

    // Inner class for configuration history
    @lombok.Data
    @lombok.Builder
    private static class ConfigurationHistory {
        private LocalDateTime timestamp;
        private String service;
        private String environment;
        private Map<String, Object> oldConfig;
        private Map<String, Object> newConfig;
        private String action;
    }
}