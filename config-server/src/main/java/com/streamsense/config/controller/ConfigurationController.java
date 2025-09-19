package com.streamsense.config.controller;

import com.streamsense.config.service.ConfigurationManagementService;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.Map;

/**
 * Configuration Management Controller
 * Provides centralized configuration management with Netflix OSS patterns
 * Supports dynamic configuration updates and environment-specific configs
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@RefreshScope
@Slf4j
public class ConfigurationController {

    private final ConfigurationManagementService configService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Get configuration for a specific service and environment
     */
    @GetMapping("/{service}/{environment}")
    @HystrixCommand(
        fallbackMethod = "getDefaultConfiguration",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "3000"),
            @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "10"),
            @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"),
            @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", value = "30000")
        }
    )
    @Timed(value = "config.get.service", description = "Time taken to get service configuration")
    public Mono<Map<String, Object>> getServiceConfiguration(@PathVariable String service,
                                                              @PathVariable String environment,
                                                              @RequestParam(defaultValue = "latest") String version) {
        log.info("Getting configuration for service: {}, environment: {}, version: {}", service, environment, version);
        
        return configService.getConfiguration(service, environment, version)
                .doOnSuccess(config -> {
                    log.info("Successfully retrieved configuration for {}/{}", service, environment);
                    
                    // Publish config access event
                    kafkaTemplate.send("config-events", "config-accessed", 
                        Map.of("service", service, "environment", environment, "version", version));
                })
                .doOnError(error -> log.error("Error retrieving configuration for {}/{}", service, environment, error));
    }

    /**
     * Update configuration for a service
     */
    @PutMapping("/{service}/{environment}")
    @HystrixCommand(
        fallbackMethod = "updateConfigurationFallback",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000")
        }
    )
    @Timed(value = "config.update.service", description = "Time taken to update service configuration")
    public Mono<String> updateServiceConfiguration(@PathVariable String service,
                                                    @PathVariable String environment,
                                                    @Valid @RequestBody Map<String, Object> configuration) {
        log.info("Updating configuration for service: {}, environment: {}", service, environment);
        
        return configService.updateConfiguration(service, environment, configuration)
                .doOnSuccess(result -> {
                    log.info("Successfully updated configuration for {}/{}", service, environment);
                    
                    // Publish config update event
                    kafkaTemplate.send("config-events", "config-updated", 
                        Map.of("service", service, "environment", environment, "config", configuration));
                    
                    // Trigger configuration refresh for all service instances
                    kafkaTemplate.send("config-refresh", service + ":" + environment, configuration);
                })
                .doOnError(error -> log.error("Error updating configuration for {}/{}", service, environment, error));
    }

    /**
     * Get all configurations for a service across environments
     */
    @GetMapping("/{service}")
    @HystrixCommand(
        fallbackMethod = "getAllConfigurationsFallback",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "4000")
        }
    )
    @Timed(value = "config.get.all", description = "Time taken to get all service configurations")
    public Mono<Map<String, Map<String, Object>>> getAllServiceConfigurations(@PathVariable String service) {
        log.info("Getting all configurations for service: {}", service);
        
        return configService.getAllConfigurations(service)
                .doOnSuccess(configs -> {
                    log.info("Retrieved {} environment configurations for service: {}", configs.size(), service);
                    
                    kafkaTemplate.send("config-events", "all-configs-accessed", 
                        Map.of("service", service, "environments", configs.keySet()));
                });
    }

    /**
     * Create new configuration profile
     */
    @PostMapping("/{service}/{environment}/profile")
    @HystrixCommand(fallbackMethod = "createProfileFallback")
    @Timed(value = "config.create.profile", description = "Time taken to create configuration profile")
    public Mono<String> createConfigurationProfile(@PathVariable String service,
                                                    @PathVariable String environment,
                                                    @RequestParam String profileName,
                                                    @Valid @RequestBody Map<String, Object> profileConfig) {
        log.info("Creating configuration profile: {} for {}/{}", profileName, service, environment);
        
        return configService.createProfile(service, environment, profileName, profileConfig)
                .doOnSuccess(result -> {
                    kafkaTemplate.send("config-events", "profile-created", 
                        Map.of("service", service, "environment", environment, "profile", profileName));
                });
    }

    /**
     * Refresh configuration cache
     */
    @PostMapping("/refresh")
    @HystrixCommand(fallbackMethod = "refreshCacheFallback")
    @Timed(value = "config.refresh.cache", description = "Time taken to refresh configuration cache")
    public Mono<String> refreshConfigurationCache(@RequestParam(required = false) String service) {
        log.info("Refreshing configuration cache for service: {}", service != null ? service : "all");
        
        return configService.refreshCache(service)
                .doOnSuccess(result -> {
                    kafkaTemplate.send("config-events", "cache-refreshed", 
                        Map.of("service", service != null ? service : "all"));
                });
    }

    /**
     * Get configuration history and audit trail
     */
    @GetMapping("/{service}/{environment}/history")
    @HystrixCommand(fallbackMethod = "getHistoryFallback")
    @Timed(value = "config.get.history", description = "Time taken to get configuration history")
    public Mono<Map<String, Object>> getConfigurationHistory(@PathVariable String service,
                                                              @PathVariable String environment,
                                                              @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting configuration history for {}/{}, limit: {}", service, environment, limit);
        
        return configService.getConfigurationHistory(service, environment, limit);
    }

    /**
     * Kafka listener for configuration refresh requests
     */
    @KafkaListener(topics = "config-refresh-requests", groupId = "config-service")
    public void processConfigRefreshRequest(String refreshRequest) {
        log.info("Processing config refresh request: {}", refreshRequest);
        
        try {
            String[] parts = refreshRequest.split(":");
            if (parts.length >= 2) {
                String service = parts[0];
                String environment = parts[1];
                
                configService.refreshServiceConfiguration(service, environment)
                        .subscribe(
                            result -> {
                                log.info("Successfully refreshed config for {}/{}", service, environment);
                                kafkaTemplate.send("config-events", "refresh-completed", 
                                    Map.of("service", service, "environment", environment));
                            },
                            error -> log.error("Error refreshing config for {}/{}", service, environment, error)
                        );
            }
        } catch (Exception e) {
            log.error("Failed to process config refresh request: {}", refreshRequest, e);
        }
    }

    /**
     * Kafka listener for configuration validation requests
     */
    @KafkaListener(topics = "config-validation", groupId = "config-service")
    public void processConfigValidation(String validationRequest) {
        log.info("Processing config validation: {}", validationRequest);
        
        try {
            configService.validateConfiguration(validationRequest)
                    .subscribe(
                        result -> log.info("Configuration validation completed: {}", result),
                        error -> log.error("Configuration validation failed: {}", validationRequest, error)
                    );
        } catch (Exception e) {
            log.error("Failed to process config validation: {}", validationRequest, e);
        }
    }

    // Hystrix Fallback Methods

    public Mono<Map<String, Object>> getDefaultConfiguration(String service, String environment, String version) {
        log.warn("Using fallback configuration for {}/{}", service, environment);
        
        return Mono.just(Map.of(
            "service", service,
            "environment", environment,
            "version", "fallback",
            "status", "FALLBACK",
            "message", "Configuration service temporarily unavailable"
        ));
    }

    public Mono<String> updateConfigurationFallback(String service, String environment, Map<String, Object> configuration) {
        log.warn("Configuration update fallback for {}/{}", service, environment);
        return Mono.just("Configuration update temporarily unavailable");
    }

    public Mono<Map<String, Map<String, Object>>> getAllConfigurationsFallback(String service) {
        log.warn("All configurations fallback for service: {}", service);
        return Mono.just(Map.of(
            "fallback", Map.of("status", "FALLBACK", "message", "Service temporarily unavailable")
        ));
    }

    public Mono<String> createProfileFallback(String service, String environment, String profileName, Map<String, Object> profileConfig) {
        log.warn("Create profile fallback for {}/{}/{}", service, environment, profileName);
        return Mono.just("Profile creation temporarily unavailable");
    }

    public Mono<String> refreshCacheFallback(String service) {
        log.warn("Cache refresh fallback for service: {}", service);
        return Mono.just("Cache refresh temporarily unavailable");
    }

    public Mono<Map<String, Object>> getHistoryFallback(String service, String environment, int limit) {
        log.warn("Configuration history fallback for {}/{}", service, environment);
        return Mono.just(Map.of(
            "status", "FALLBACK",
            "message", "Configuration history temporarily unavailable"
        ));
    }
}