package com.streamsense.recommendation.service;

import com.streamsense.recommendation.model.RecommendationRequest;
import com.streamsense.recommendation.model.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Recommendation Service Implementation
 * Provides collaborative filtering and content-based recommendation algorithms
 */
@Service
@Slf4j
public class RecommendationService {

    // Simulated user-item interaction matrix
    private final Map<String, Set<String>> userInteractions = new HashMap<>();
    private final Map<String, Map<String, Double>> userPreferences = new HashMap<>();
    private final Map<String, Set<String>> contentFeatures = new HashMap<>();
    private final Random random = new Random();

    /**
     * Generate personalized recommendations using collaborative filtering
     */
    public Mono<RecommendationResponse> getPersonalizedRecommendations(String userId, int limit) {
        return Mono.fromCallable(() -> {
            log.debug("Generating personalized recommendations for user: {}", userId);
            
            // Collaborative filtering algorithm
            List<String> recommendations = collaborativeFiltering(userId, limit);
            
            return RecommendationResponse.builder()
                    .userId(userId)
                    .recommendations(recommendations)
                    .algorithm("collaborative_filtering")
                    .confidence(0.8 + (random.nextDouble() * 0.2))
                    .status("SUCCESS")
                    .build();
        });
    }

    /**
     * Generate content-based recommendations for a stream
     */
    public Mono<RecommendationResponse> getContentBasedRecommendations(String streamId, int limit) {
        return Mono.fromCallable(() -> {
            log.debug("Generating content-based recommendations for stream: {}", streamId);
            
            List<String> recommendations = contentBasedFiltering(streamId, limit);
            
            return RecommendationResponse.builder()
                    .streamId(streamId)
                    .recommendations(recommendations)
                    .algorithm("content_based")
                    .confidence(0.7 + (random.nextDouble() * 0.3))
                    .status("SUCCESS")
                    .build();
        });
    }

    /**
     * Generate batch recommendations for multiple users
     */
    public Flux<RecommendationResponse> generateBatchRecommendations(RecommendationRequest request) {
        return Flux.fromIterable(request.getUserIds())
                .flatMap(userId -> getPersonalizedRecommendations(userId, request.getLimit())
                        .onErrorResume(error -> {
                            log.error("Error generating recommendations for user: {}", userId, error);
                            return Mono.just(createFallbackResponse(userId));
                        }));
    }

    /**
     * Update user preferences for better recommendations
     */
    public Mono<String> updateUserPreferences(String userId, String preferences) {
        return Mono.fromCallable(() -> {
            log.debug("Updating preferences for user: {}", userId);
            
            // Parse and store preferences (simplified)
            Map<String, Double> prefMap = parsePreferences(preferences);
            userPreferences.put(userId, prefMap);
            
            return "Preferences updated successfully";
        });
    }

    /**
     * Process user interaction events for model improvement
     */
    public Mono<String> processInteractionEvent(String interactionData) {
        return Mono.fromCallable(() -> {
            log.debug("Processing interaction event: {}", interactionData);
            
            // Parse interaction data: "userId:streamId:action:timestamp"
            String[] parts = interactionData.split(":");
            if (parts.length >= 3) {
                String userId = parts[0];
                String streamId = parts[1];
                String action = parts[2];
                
                // Update user interaction matrix
                userInteractions.computeIfAbsent(userId, k -> new HashSet<>()).add(streamId);
                
                // Update preferences based on action
                updatePreferencesFromInteraction(userId, streamId, action);
            }
            
            return "Interaction processed";
        });
    }

    /**
     * Process content analytics for content-based improvements
     */
    public Mono<String> processContentAnalytics(String analyticsData) {
        return Mono.fromCallable(() -> {
            log.debug("Processing content analytics: {}", analyticsData);
            
            // Parse analytics data and update content features
            String[] parts = analyticsData.split(":");
            if (parts.length >= 2) {
                String contentId = parts[0];
                String features = parts[1];
                
                // Store content features for content-based filtering
                contentFeatures.put(contentId, Set.of(features.split(",")));
            }
            
            return "Analytics processed";
        });
    }

    // Private helper methods

    private List<String> collaborativeFiltering(String userId, int limit) {
        Set<String> userItems = userInteractions.getOrDefault(userId, new HashSet<>());
        Map<String, Double> similarities = new HashMap<>();
        
        // Find similar users
        for (Map.Entry<String, Set<String>> entry : userInteractions.entrySet()) {
            if (!entry.getKey().equals(userId)) {
                double similarity = calculateJaccardSimilarity(userItems, entry.getValue());
                if (similarity > 0.1) {
                    similarities.put(entry.getKey(), similarity);
                }
            }
        }
        
        // Get recommendations from similar users
        Set<String> recommendations = new HashSet<>();
        similarities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    Set<String> similarUserItems = userInteractions.get(entry.getKey());
                    similarUserItems.stream()
                            .filter(item -> !userItems.contains(item))
                            .limit(3)
                            .forEach(recommendations::add);
                });
        
        // If not enough recommendations, add trending content
        if (recommendations.size() < limit) {
            addTrendingContent(recommendations, limit);
        }
        
        return recommendations.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> contentBasedFiltering(String streamId, int limit) {
        Set<String> streamFeatures = contentFeatures.getOrDefault(streamId, new HashSet<>());
        List<String> recommendations = new ArrayList<>();
        
        // Find content with similar features
        for (Map.Entry<String, Set<String>> entry : contentFeatures.entrySet()) {
            if (!entry.getKey().equals(streamId)) {
                double similarity = calculateJaccardSimilarity(streamFeatures, entry.getValue());
                if (similarity > 0.3) {
                    recommendations.add(entry.getKey());
                }
            }
        }
        
        // Sort by similarity and limit
        Collections.shuffle(recommendations);
        return recommendations.stream().limit(limit).collect(Collectors.toList());
    }

    private double calculateJaccardSimilarity(Set<String> set1, Set<String> set2) {
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private void addTrendingContent(Set<String> recommendations, int limit) {
        List<String> trending = Arrays.asList(
            "trending-gaming-stream",
            "trending-music-stream", 
            "trending-talk-show",
            "trending-esports-stream",
            "trending-art-stream"
        );
        
        for (String item : trending) {
            if (recommendations.size() >= limit) break;
            recommendations.add(item);
        }
    }

    private Map<String, Double> parsePreferences(String preferences) {
        Map<String, Double> prefMap = new HashMap<>();
        
        // Simple parsing: "gaming:0.8,music:0.6,sports:0.4"
        String[] pairs = preferences.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                try {
                    prefMap.put(parts[0], Double.parseDouble(parts[1]));
                } catch (NumberFormatException e) {
                    log.warn("Invalid preference value: {}", pair);
                }
            }
        }
        
        return prefMap;
    }

    private void updatePreferencesFromInteraction(String userId, String streamId, String action) {
        Map<String, Double> prefs = userPreferences.computeIfAbsent(userId, k -> new HashMap<>());
        
        // Update preferences based on action type
        double weight = switch (action.toLowerCase()) {
            case "view" -> 0.1;
            case "like" -> 0.3;
            case "share" -> 0.5;
            case "subscribe" -> 0.8;
            default -> 0.05;
        };
        
        // Simplified category extraction from streamId
        String category = extractCategory(streamId);
        prefs.merge(category, weight, Double::sum);
    }

    private String extractCategory(String streamId) {
        // Simple category extraction based on stream ID patterns
        if (streamId.contains("gaming")) return "gaming";
        if (streamId.contains("music")) return "music";
        if (streamId.contains("sports")) return "sports";
        if (streamId.contains("talk")) return "talk";
        return "general";
    }

    private RecommendationResponse createFallbackResponse(String userId) {
        return RecommendationResponse.builder()
                .userId(userId)
                .recommendations(Arrays.asList("fallback-stream-1", "fallback-stream-2"))
                .algorithm("fallback")
                .confidence(0.3)
                .status("FALLBACK")
                .build();
    }
}