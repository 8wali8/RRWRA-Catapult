package com.streamsense.recommendation.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Real-time data models for recommendation service
 */
public class RealtimeModels {

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

    @Data
    @Builder
    public static class ContentFeatures {
        private String streamId;
        private List<String> gameCategories;
        private List<String> detectedObjects;
        private List<String> sponsorBrands;
        private String streamCategory;
        private double contentQuality;
        private Map<String, Double> featureScores;
    }

    @Data
    @Builder
    public static class RecommendationContext {
        private String userId;
        private String currentStreamId;
        private LocalDateTime requestTime;
        private String deviceType;
        private String location;
        private Map<String, Double> sessionFeatures;
    }
}