package com.streamsense.recommendation.controller;

import com.streamsense.recommendation.model.RecommendationRequest;
import com.streamsense.recommendation.model.RecommendationResponse;
import com.streamsense.recommendation.service.RecommendationService;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;

/**
 * Recommendation Service Controller
 * Provides personalized content recommendations using collaborative filtering
 * and content-based algorithms with Netflix OSS patterns
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Get personalized content recommendations
     */
    @GetMapping("/user/{userId}")
    @HystrixCommand(
        fallbackMethod = "getDefaultRecommendations",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000"),
            @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "10"),
            @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"),
            @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", value = "60000")
        }
    )
    @Timed(value = "recommendation.get.user", description = "Time taken to get user recommendations")
    public Mono<RecommendationResponse> getUserRecommendations(@PathVariable String userId,
                                                               @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting recommendations for user: {}, limit: {}", userId, limit);
        
        return recommendationService.getPersonalizedRecommendations(userId, limit)
                .doOnSuccess(response -> {
                    log.info("Successfully generated {} recommendations for user: {}", 
                            response.getRecommendations().size(), userId);
                    
                    // Publish recommendation event to Kafka
                    kafkaTemplate.send("recommendation-events", 
                        "user-recommendation-generated", response);
                })
                .doOnError(error -> log.error("Error generating recommendations for user: {}", userId, error));
    }

    /**
     * Get content-based recommendations for a specific stream
     */
    @GetMapping("/stream/{streamId}")
    @HystrixCommand(
        fallbackMethod = "getDefaultStreamRecommendations",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "3000")
        }
    )
    @Timed(value = "recommendation.get.stream", description = "Time taken to get stream recommendations")
    public Mono<RecommendationResponse> getStreamRecommendations(@PathVariable String streamId,
                                                                 @RequestParam(defaultValue = "5") int limit) {
        log.info("Getting recommendations for stream: {}, limit: {}", streamId, limit);
        
        return recommendationService.getContentBasedRecommendations(streamId, limit)
                .doOnSuccess(response -> {
                    kafkaTemplate.send("recommendation-events", 
                        "stream-recommendation-generated", response);
                });
    }

    /**
     * Generate batch recommendations for multiple users
     */
    @PostMapping("/batch")
    @HystrixCommand(
        fallbackMethod = "getBatchDefaultRecommendations",
        commandProperties = {
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "10000")
        }
    )
    @Timed(value = "recommendation.batch", description = "Time taken to generate batch recommendations")
    public Flux<RecommendationResponse> generateBatchRecommendations(@Valid @RequestBody RecommendationRequest request) {
        log.info("Generating batch recommendations for {} users", request.getUserIds().size());
        
        return recommendationService.generateBatchRecommendations(request)
                .doOnNext(response -> {
                    kafkaTemplate.send("recommendation-events", 
                        "batch-recommendation-generated", response);
                })
                .doOnComplete(() -> log.info("Completed batch recommendation generation"));
    }

    /**
     * Update user preferences for better recommendations
     */
    @PostMapping("/user/{userId}/preferences")
    @HystrixCommand(fallbackMethod = "updatePreferencesFallback")
    @Timed(value = "recommendation.preferences.update", description = "Time taken to update user preferences")
    public Mono<String> updateUserPreferences(@PathVariable String userId,
                                              @RequestBody String preferences) {
        log.info("Updating preferences for user: {}", userId);
        
        return recommendationService.updateUserPreferences(userId, preferences)
                .doOnSuccess(result -> {
                    kafkaTemplate.send("user-events", "preferences-updated", 
                        userId + ":" + preferences);
                });
    }

    /**
     * Kafka listener for user interaction events to improve recommendations
     */
    @KafkaListener(topics = "user-interactions", groupId = "recommendation-service")
    public void processUserInteraction(String interactionData) {
        log.info("Processing user interaction: {}", interactionData);
        
        try {
            // Parse interaction data and update recommendation models
            recommendationService.processInteractionEvent(interactionData)
                    .subscribe(
                        result -> log.debug("Processed interaction successfully: {}", result),
                        error -> log.error("Error processing interaction: {}", interactionData, error)
                    );
        } catch (Exception e) {
            log.error("Failed to process user interaction: {}", interactionData, e);
        }
    }

    /**
     * Kafka listener for content analytics to improve content-based recommendations
     */
    @KafkaListener(topics = "content-analytics", groupId = "recommendation-service")
    public void processContentAnalytics(String analyticsData) {
        log.info("Processing content analytics: {}", analyticsData);
        
        try {
            recommendationService.processContentAnalytics(analyticsData)
                    .subscribe(
                        result -> log.debug("Processed analytics successfully: {}", result),
                        error -> log.error("Error processing analytics: {}", analyticsData, error)
                    );
        } catch (Exception e) {
            log.error("Failed to process content analytics: {}", analyticsData, e);
        }
    }

    // Hystrix Fallback Methods

    public Mono<RecommendationResponse> getDefaultRecommendations(String userId, int limit) {
        log.warn("Using fallback recommendations for user: {}", userId);
        
        return Mono.just(RecommendationResponse.builder()
                .userId(userId)
                .recommendations(Arrays.asList(
                    "trending-stream-1",
                    "trending-stream-2", 
                    "trending-stream-3"
                ))
                .algorithm("fallback")
                .confidence(0.5)
                .status("FALLBACK")
                .build());
    }

    public Mono<RecommendationResponse> getDefaultStreamRecommendations(String streamId, int limit) {
        log.warn("Using fallback stream recommendations for: {}", streamId);
        
        return Mono.just(RecommendationResponse.builder()
                .streamId(streamId)
                .recommendations(Arrays.asList("similar-stream-1", "similar-stream-2"))
                .algorithm("fallback")
                .confidence(0.3)
                .status("FALLBACK")
                .build());
    }

    public Flux<RecommendationResponse> getBatchDefaultRecommendations(RecommendationRequest request) {
        log.warn("Using fallback batch recommendations");
        
        return Flux.fromIterable(request.getUserIds())
                .map(userId -> RecommendationResponse.builder()
                        .userId(userId)
                        .recommendations(Arrays.asList("default-stream"))
                        .algorithm("fallback")
                        .confidence(0.2)
                        .status("FALLBACK")
                        .build());
    }

    public Mono<String> updatePreferencesFallback(String userId, String preferences) {
        log.warn("Preferences update fallback for user: {}", userId);
        return Mono.just("Preferences update temporarily unavailable");
    }
}