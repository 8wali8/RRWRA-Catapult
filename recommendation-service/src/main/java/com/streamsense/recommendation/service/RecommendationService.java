package com.streamsense.recommendation.service;

import com.streamsense.recommendation.model.RecommendationRequest;
import com.streamsense.recommendation.model.RecommendationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;

/**
 * Enhanced Recommendation Service Implementation
 * Real-time data integration with StreamSense ecosystem
 * Provides collaborative filtering and content-based recommendation algorithms
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // Real-time data structures
    private final Map<String, Set<String>> userInteractions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> userPreferences = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> contentFeatures = new ConcurrentHashMap<>();
    private final Map<String, StreamMetrics> streamMetrics = new ConcurrentHashMap<>();
    private final Map<String, UserProfile> userProfiles = new ConcurrentHashMap<>();
    private final Map<String, List<SponsorEvent>> streamSponsors = new ConcurrentHashMap<>();
    private final Map<String, SentimentScore> streamSentiments = new ConcurrentHashMap<>();
    
    // Real-time trending and popularity tracking
    private final Map<String, PopularityScore> streamPopularity = new ConcurrentHashMap<>();
    private final Queue<UserInteraction> recentInteractions = new LinkedList<>();
    
    private final Random random = new Random();

    // === EMBEDDED DATA CLASSES ===
    
    @Data
    @Builder
    public static class StreamMetrics {
        private String streamId;
        private int viewerCount;
        private double averageSentiment;
        private int chatActivityLevel;
        private double qualityScore;
        private LocalDateTime lastUpdated;
        private Map<String, Integer> objectCounts;
    }

    @Data
    @Builder
    public static class UserProfile {
        private String userId;
        private Map<String, Double> categoryPreferences;
        private double engagementScore;
        private List<String> preferredStreamers;
        private List<String> dislikedBrands;
        private Map<String, Double> sentimentHistory;
        private LocalDateTime lastActive;
    }

    @Data
    @Builder
    public static class SponsorEvent {
        private String brandName;
        private double confidence;
        private String objectType;
        private LocalDateTime timestamp;
        private String detectionMethod;
    }

    @Data
    @Builder
    public static class SentimentScore {
        private String streamId;
        private double averageSentiment;
        private String dominantEmotion;
        private int messageCount;
        private LocalDateTime lastUpdated;
    }

    @Data
    @Builder
    public static class PopularityScore {
        private String streamId;
        private double popularityScore;
        private int recentViews;
        private int recentChats;
        private double trendingScore;
        private LocalDateTime calculatedAt;
    }

    @Data
    @Builder
    public static class UserInteraction {
        private String userId;
        private String streamId;
        private String interactionType;
        private long timestamp;
        private Map<String, Object> metadata;
    }

    @PostConstruct
    public void initializeRecommendationEngine() {
        log.info("Initializing Real-time Recommendation Engine...");
        
        // Load historical data from Redis
        loadHistoricalData();
        
        // Start trending calculation background task
        startTrendingCalculationTask();
        
        log.info("Recommendation Engine initialized successfully");
    }

    // === REAL-TIME KAFKA LISTENERS ===

    /**
     * Process chat sentiment events from chat-service
     */
    @KafkaListener(topics = "stream.sentiment.events")
    public void processSentimentEvent(String sentimentData) {
        try {
            JsonNode sentiment = objectMapper.readTree(sentimentData);
            String streamId = sentiment.get("streamId").asText();
            String userId = sentiment.get("userId").asText();
            double sentimentScore = sentiment.get("sentiment").asDouble();
            String emotion = sentiment.get("emotion").asText();
            
            log.debug("Processing sentiment event - Stream: {}, User: {}, Sentiment: {}", 
                     streamId, userId, sentimentScore);
            
            // Update stream sentiment metrics
            updateStreamSentiment(streamId, sentimentScore, emotion);
            
            // Update user preferences based on sentiment
            updateUserPreferencesFromSentiment(userId, streamId, sentimentScore, emotion);
            
            // Cache sentiment data in Redis
            cacheUserSentiment(userId, streamId, sentimentScore);
            
        } catch (Exception e) {
            log.error("Error processing sentiment event: {}", sentimentData, e);
        }
    }

    /**
     * Process sponsor detection events from video-service
     */
    @KafkaListener(topics = "stream.brand.detections")
    public void processSponsorEvent(String sponsorData) {
        try {
            JsonNode sponsor = objectMapper.readTree(sponsorData);
            String streamId = sponsor.get("streamId").asText();
            JsonNode brands = sponsor.get("brands");
            
            log.debug("Processing sponsor event - Stream: {}, Brands detected: {}", 
                     streamId, brands.size());
            
            // Process each brand detection
            for (JsonNode brand : brands) {
                String brandName = brand.get("brand").asText();
                double confidence = brand.get("confidence").asDouble();
                String objectType = brand.get("object_type").asText();
                
                // Update stream sponsor information
                updateStreamSponsors(streamId, brandName, confidence, objectType);
                
                // Update content features for content-based filtering
                updateContentFeaturesFromSponsor(streamId, brandName, objectType);
            }
            
            // Cache sponsor data in Redis
            cacheSponsorData(streamId, sponsor.toString());
            
        } catch (Exception e) {
            log.error("Error processing sponsor event: {}", sponsorData, e);
        }
    }

    /**
     * Process video analysis events from video-service
     */
    @KafkaListener(topics = "stream.video.analysis")
    public void processVideoAnalysisEvent(String analysisData) {
        try {
            JsonNode analysis = objectMapper.readTree(analysisData);
            String streamId = analysis.get("stream_id").asText();
            JsonNode detections = analysis.get("detections");
            
            log.debug("Processing video analysis - Stream: {}, Objects detected: {}", 
                     streamId, detections.size());
            
            // Extract content features from object detection
            Set<String> objectTypes = new HashSet<>();
            for (JsonNode detection : detections) {
                String className = detection.get("class_name").asText();
                objectTypes.add(className);
            }
            
            // Update content features
            contentFeatures.computeIfAbsent(streamId, k -> new HashSet<>()).addAll(objectTypes);
            
            // Update stream quality metrics based on processing time
            double processingTime = analysis.get("frame_info").get("processing_time").asDouble();
            updateStreamQualityMetrics(streamId, processingTime, detections.size());
            
        } catch (Exception e) {
            log.error("Error processing video analysis event: {}", analysisData, e);
        }
    }

    /**
     * Process chat interaction events from chat-service
     */
    @KafkaListener(topics = "stream.chat.messages")
    public void processChatEvent(String chatData) {
        try {
            JsonNode chat = objectMapper.readTree(chatData);
            String streamId = chat.get("streamId").asText();
            String userId = chat.get("userId").asText();
            String message = chat.get("message").asText();
            long timestamp = chat.get("timestamp").asLong();
            
            log.debug("Processing chat event - Stream: {}, User: {}", streamId, userId);
            
            // Record user interaction
            recordUserInteraction(userId, streamId, "chat", timestamp);
            
            // Update stream activity metrics
            updateStreamActivity(streamId, timestamp);
            
            // Analyze chat engagement patterns
            analyzeUserEngagement(userId, streamId, message, timestamp);
            
        } catch (Exception e) {
            log.error("Error processing chat event: {}", chatData, e);
        }
    }

    /**
     * Process ML engine predictions and user behavior analysis
     */
    @KafkaListener(topics = "ml.user.predictions")
    public void processMLPredictions(String predictionData) {
        try {
            JsonNode prediction = objectMapper.readTree(predictionData);
            String userId = prediction.get("userId").asText();
            JsonNode preferences = prediction.get("predicted_preferences");
            double engagementScore = prediction.get("engagement_score").asDouble();
            
            log.debug("Processing ML prediction - User: {}, Engagement: {}", userId, engagementScore);
            
            // Update user profile with ML predictions
            updateUserProfileFromML(userId, preferences, engagementScore);
            
        } catch (Exception e) {
            log.error("Error processing ML prediction: {}", predictionData, e);
        }
    }

    /**
     * Enhanced personalized recommendations with real-time data
     */
    public Mono<RecommendationResponse> getPersonalizedRecommendations(String userId, int limit) {
        return Mono.fromCallable(() -> {
            log.debug("Generating real-time personalized recommendations for user: {}", userId);
            
            // Get user profile and preferences
            UserProfile userProfile = userProfiles.get(userId);
            Map<String, Double> preferences = userPreferences.getOrDefault(userId, new HashMap<>());
            
            // Generate hybrid recommendations
            List<String> collaborativeRecs = collaborativeFiltering(userId, limit * 2);
            List<String> contentBasedRecs = contentBasedFilteringEnhanced(userId, limit * 2);
            List<String> trendingRecs = getTrendingRecommendations(userId, limit);
            
            // Combine and rank recommendations
            List<String> finalRecommendations = combineRecommendations(
                collaborativeRecs, contentBasedRecs, trendingRecs, userProfile, limit);
            
            // Apply business rules (brand safety, sentiment filtering)
            finalRecommendations = applyBusinessRules(userId, finalRecommendations);
            
            // Cache recommendations in Redis
            cacheRecommendations(userId, finalRecommendations);
            
            // Send recommendation event to Kafka for analytics
            publishRecommendationEvent(userId, finalRecommendations, "personalized");
            
            return RecommendationResponse.builder()
                    .userId(userId)
                    .recommendations(finalRecommendations)
                    .algorithm("hybrid_realtime")
                    .confidence(calculateConfidenceScore(userProfile, finalRecommendations))
                    .status("SUCCESS")
                    .metadata(Map.of(
                        "collaborative_count", collaborativeRecs.size(),
                        "content_based_count", contentBasedRecs.size(),
                        "trending_count", trendingRecs.size(),
                        "user_engagement_score", userProfile != null ? userProfile.getEngagementScore() : 0.0
                    ))
                    .build();
        });
    }

    /**
     * Content-based recommendations with sponsor and sentiment awareness
     */
    public Mono<RecommendationResponse> getContentBasedRecommendations(String streamId, int limit) {
        return Mono.fromCallable(() -> {
            log.debug("Generating content-based recommendations for stream: {}", streamId);
            
            // Get stream features
            Set<String> streamFeatures = contentFeatures.getOrDefault(streamId, new HashSet<>());
            List<SponsorEvent> sponsors = streamSponsors.getOrDefault(streamId, new ArrayList<>());
            SentimentScore sentiment = streamSentiments.get(streamId);
            
            // Find similar streams based on content features
            List<String> recommendations = findSimilarStreams(streamId, streamFeatures, sponsors, limit);
            
            // Apply sentiment filtering (prefer positive sentiment streams)
            recommendations = filterBySentiment(recommendations, 0.3); // Minimum sentiment threshold
            
            // Cache and publish event
            cacheContentRecommendations(streamId, recommendations);
            publishRecommendationEvent(streamId, recommendations, "content_based");
            
            return RecommendationResponse.builder()
                    .streamId(streamId)
                    .recommendations(recommendations)
                    .algorithm("content_based_enhanced")
                    .confidence(calculateContentConfidence(streamFeatures, sponsors, sentiment))
                    .status("SUCCESS")
                    .metadata(Map.of(
                        "feature_count", streamFeatures.size(),
                        "sponsor_count", sponsors.size(),
                        "sentiment_score", sentiment != null ? sentiment.getAverageSentiment() : 0.0
                    ))
                    .build();
        });
    }

    // === REAL-TIME DATA PROCESSING METHODS ===

    private void updateStreamSentiment(String streamId, double sentimentScore, String emotion) {
        streamSentiments.compute(streamId, (key, existing) -> {
            if (existing == null) {
                return SentimentScore.builder()
                        .streamId(streamId)
                        .averageSentiment(sentimentScore)
                        .dominantEmotion(emotion)
                        .messageCount(1)
                        .lastUpdated(LocalDateTime.now())
                        .build();
            } else {
                // Update running average
                int newCount = existing.getMessageCount() + 1;
                double newAverage = ((existing.getAverageSentiment() * existing.getMessageCount()) + sentimentScore) / newCount;
                
                return SentimentScore.builder()
                        .streamId(streamId)
                        .averageSentiment(newAverage)
                        .dominantEmotion(emotion)
                        .messageCount(newCount)
                        .lastUpdated(LocalDateTime.now())
                        .build();
            }
        });
    }

    private void updateUserPreferencesFromSentiment(String userId, String streamId, double sentiment, String emotion) {
        // Extract stream category for preference updates
        String category = extractCategoryFromStream(streamId);
        
        // Weight preference update based on sentiment
        double weight = sentiment > 0.5 ? 0.2 : -0.1; // Positive sentiment increases preference
        
        userPreferences.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .merge(category, weight, Double::sum);
        
        // Update user profile
        userProfiles.compute(userId, (key, profile) -> {
            if (profile == null) {
                return UserProfile.builder()
                        .userId(userId)
                        .categoryPreferences(Map.of(category, weight))
                        .engagementScore(sentiment)
                        .lastActive(LocalDateTime.now())
                        .sentimentHistory(Map.of(streamId, sentiment))
                        .build();
            } else {
                // Update existing profile
                Map<String, Double> newPrefs = new HashMap<>(profile.getCategoryPreferences());
                newPrefs.merge(category, weight, Double::sum);
                
                Map<String, Double> newSentimentHistory = new HashMap<>(profile.getSentimentHistory());
                newSentimentHistory.put(streamId, sentiment);
                
                return UserProfile.builder()
                        .userId(userId)
                        .categoryPreferences(newPrefs)
                        .engagementScore((profile.getEngagementScore() + sentiment) / 2.0)
                        .preferredStreamers(profile.getPreferredStreamers())
                        .dislikedBrands(profile.getDislikedBrands())
                        .sentimentHistory(newSentimentHistory)
                        .lastActive(LocalDateTime.now())
                        .build();
            }
        });
    }

    private void updateStreamSponsors(String streamId, String brandName, double confidence, String objectType) {
        streamSponsors.computeIfAbsent(streamId, k -> new ArrayList<>()).add(
            SponsorEvent.builder()
                    .brandName(brandName)
                    .confidence(confidence)
                    .objectType(objectType)
                    .timestamp(LocalDateTime.now())
                    .detectionMethod("yolo_detection")
                    .build()
        );
        
        // Update content features
        contentFeatures.computeIfAbsent(streamId, k -> new HashSet<>()).add("brand_" + brandName);
        contentFeatures.get(streamId).add("object_" + objectType);
    }

    private void updateContentFeaturesFromSponsor(String streamId, String brandName, String objectType) {
        Set<String> features = contentFeatures.computeIfAbsent(streamId, k -> new HashSet<>());
        features.add("sponsor_" + brandName.toLowerCase());
        features.add("product_" + objectType.toLowerCase());
        
        // Brand category mapping
        String brandCategory = getBrandCategory(brandName);
        if (brandCategory != null) {
            features.add("brand_category_" + brandCategory);
        }
    }

    private void recordUserInteraction(String userId, String streamId, String interactionType, long timestamp) {
        // Update user-stream interaction matrix
        userInteractions.computeIfAbsent(userId, k -> new HashSet<>()).add(streamId);
        
        // Track recent interactions for trending calculation
        UserInteraction interaction = UserInteraction.builder()
                .userId(userId)
                .streamId(streamId)
                .interactionType(interactionType)
                .timestamp(timestamp)
                .metadata(Map.of("source", "real_time"))
                .build();
        
        recentInteractions.offer(interaction);
        
        // Keep only recent interactions (last hour)
        cleanupOldInteractions();
        
        // Update stream popularity
        updateStreamPopularity(streamId);
    }

    private void updateStreamActivity(String streamId, long timestamp) {
        streamMetrics.compute(streamId, (key, metrics) -> {
            if (metrics == null) {
                return StreamMetrics.builder()
                        .streamId(streamId)
                        .viewerCount(1)
                        .chatActivityLevel(1)
                        .lastUpdated(LocalDateTime.now())
                        .build();
            } else {
                return StreamMetrics.builder()
                        .streamId(streamId)
                        .viewerCount(metrics.getViewerCount())
                        .chatActivityLevel(metrics.getChatActivityLevel() + 1)
                        .averageSentiment(metrics.getAverageSentiment())
                        .qualityScore(metrics.getQualityScore())
                        .lastUpdated(LocalDateTime.now())
                        .objectCounts(metrics.getObjectCounts())
                        .build();
            }
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
    
    // === HELPER METHODS FOR ENHANCED RECOMMENDATIONS ===
    
    private UserProfile createDefaultProfile(String userId) {
        return UserProfile.builder()
                .userId(userId)
                .categoryPreferences(new HashMap<>())
                .engagementScore(0.5)
                .preferredStreamers(new ArrayList<>())
                .dislikedBrands(new ArrayList<>())
                .sentimentHistory(new HashMap<>())
                .lastActive(LocalDateTime.now())
                .build();
    }
    
    private Map<String, Double> findSimilarUsers(String userId, Set<String> userStreams) {
        Map<String, Double> similarities = new HashMap<>();
        
        for (Map.Entry<String, Set<String>> entry : userInteractions.entrySet()) {
            String otherUserId = entry.getKey();
            if (!otherUserId.equals(userId)) {
                Set<String> otherStreams = entry.getValue();
                double similarity = calculateUserSimilarity(userStreams, otherStreams);
                if (similarity > 0.1) {
                    similarities.put(otherUserId, similarity);
                }
            }
        }
        
        return similarities.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new));
    }
    
    private double calculateUserSimilarity(Set<String> streams1, Set<String> streams2) {
        Set<String> intersection = new HashSet<>(streams1);
        intersection.retainAll(streams2);
        
        Set<String> union = new HashSet<>(streams1);
        union.addAll(streams2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    private double calculateEnhancedScore(String streamId, UserProfile profile, double userSimilarity) {
        StreamMetrics metrics = streamMetrics.get(streamId);
        SentimentScore sentiment = streamSentiments.get(streamId);
        PopularityScore popularity = streamPopularity.get(streamId);
        
        double score = userSimilarity * 0.4; // Base similarity weight
        
        if (metrics != null) {
            score += metrics.getQualityScore() * 0.2;
            score += Math.min(metrics.getViewerCount() / 1000.0, 1.0) * 0.1;
        }
        
        if (sentiment != null && sentiment.getAverageSentiment() > 0) {
            score += sentiment.getAverageSentiment() * 0.2;
        }
        
        if (popularity != null) {
            score += popularity.getTrendingScore() * 0.1;
        }
        
        return Math.min(score, 1.0);
    }
    
    private double calculateFinalScore(String streamId, UserProfile profile) {
        double score = 0.5; // Base score
        
        StreamMetrics metrics = streamMetrics.get(streamId);
        if (metrics != null) {
            score += metrics.getQualityScore() * 0.3;
            
            // Apply user preferences
            for (Map.Entry<String, Double> pref : profile.getCategoryPreferences().entrySet()) {
                if (metrics.getObjectCounts().containsKey(pref.getKey())) {
                    score += pref.getValue() * 0.2;
                }
            }
        }
        
        SentimentScore sentiment = streamSentiments.get(streamId);
        if (sentiment != null) {
            score += sentiment.getAverageSentiment() * 0.2;
        }
        
        // Check for disliked brands
        List<SponsorEvent> sponsors = streamSponsors.get(streamId);
        if (sponsors != null) {
            for (SponsorEvent sponsor : sponsors) {
                if (profile.getDislikedBrands().contains(sponsor.getBrandName())) {
                    score -= 0.3; // Penalty for disliked brands
                }
            }
        }
        
        return Math.max(score, 0.0);
    }
    
    private double calculateFeatureSimilarity(Set<String> features1, Set<String> features2) {
        if (features1.isEmpty() || features2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> intersection = new HashSet<>(features1);
        intersection.retainAll(features2);
        
        Set<String> union = new HashSet<>(features1);
        union.addAll(features2);
        
        return (double) intersection.size() / union.size();
    }
    
    private boolean passesQualityThreshold(String streamId) {
        StreamMetrics metrics = streamMetrics.get(streamId);
        if (metrics == null) return false;
        
        SentimentScore sentiment = streamSentiments.get(streamId);
        
        return metrics.getQualityScore() > 0.5 && 
               metrics.getViewerCount() > 10 &&
               (sentiment == null || sentiment.getAverageSentiment() > -0.3);
    }
    
    private double calculateContentScore(String streamId, Set<String> originalFeatures) {
        Set<String> candidateFeatures = contentFeatures.getOrDefault(streamId, new HashSet<>());
        double similarity = calculateFeatureSimilarity(originalFeatures, candidateFeatures);
        
        StreamMetrics metrics = streamMetrics.get(streamId);
        if (metrics != null) {
            similarity += metrics.getQualityScore() * 0.3;
            similarity += Math.min(metrics.getViewerCount() / 1000.0, 0.5);
        }
        
        PopularityScore popularity = streamPopularity.get(streamId);
        if (popularity != null) {
            similarity += popularity.getTrendingScore() * 0.2;
        }
        
        return similarity;
    }
    
    private RecommendationResponse buildRecommendationResponse(String id, List<String> recommendations, String algorithm) {
        return RecommendationResponse.builder()
                .streamId(id)
                .recommendations(recommendations)
                .algorithm(algorithm)
                .confidence(0.8 + (random.nextDouble() * 0.2))
                .status("SUCCESS")
                .metadata(Map.of(
                    "recommendation_count", recommendations.size(),
                    "algorithm_version", "2.0",
                    "real_time_integration", true
                ))
                .build();
    }
    
    private RecommendationResponse buildErrorResponse() {
        return RecommendationResponse.builder()
                .recommendations(Collections.emptyList())
                .algorithm("error_fallback")
                .confidence(0.0)
                .status("ERROR")
                .build();
    }
}