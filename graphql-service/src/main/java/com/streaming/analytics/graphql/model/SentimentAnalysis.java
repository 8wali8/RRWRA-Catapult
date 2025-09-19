package com.streaming.analytics.graphql.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SentimentAnalysis {
    private String streamId;
    private SentimentScore overallSentiment;
    private Map<String, Double> emotionBreakdown;
    private int messageCount;
    private String timeframe;
    private LocalDateTime lastUpdated;
}

@Data
@Builder
class SentimentScore {
    private String sentiment;
    private double score;
    private double confidence;
}

@Data
@Builder
class VideoAnalysis {
    private String streamId;
    private List<String> objectsDetected;
    private List<BrandDetection> brandDetections;
    private int frameCount;
    private String timeframe;
    private LocalDateTime lastUpdated;
}

@Data
@Builder
class BrandDetection {
    private String id;
    private String brand;
    private double confidence;
    private LocalDateTime timestamp;
    private String region;
    private int duration;
    private String category;
}

@Data
@Builder
class AnalyticsDashboard {
    private String streamId;
    private int viewerCount;
    private double messageRate;
    private double sentimentScore;
    private int brandMentions;
    private double engagement;
    private boolean trending;
    private LocalDateTime lastUpdated;
}

@Data
@Builder
class ChatMessage {
    private String id;
    private String userId;
    private String username;
    private String content;
    private String messageType;
    private LocalDateTime timestamp;
    private boolean edited;
    private LocalDateTime editedAt;
    private Double sentimentScore;
    private String emotion;
    private boolean flagged;
    private String moderationReason;
}

@Data
@Builder
class UserActivity {
    private String userId;
    private int totalWatchTime;
    private int streamsWatched;
    private int messagesPosted;
    private List<String> favoriteCategories;
    private String activityPeriod;
    private LocalDateTime lastActive;
}

@Data
@Builder
class Recommendation {
    private String id;
    private String streamId;
    private String title;
    private String reason;
    private double confidence;
    private String category;
    private int expectedViewTime;
    private double personalizedScore;
}

@Data
@Builder
class StreamHealth {
    private String streamId;
    private boolean isHealthy;
    private int bitrate;
    private int fps;
    private String quality;
    private int latency;
    private double dropRate;
    private LocalDateTime lastCheck;
}

// Real-time update models
@Data
@Builder
class SentimentUpdate {
    private String streamId;
    private String sentiment;
    private double score;
    private double confidence;
    private LocalDateTime timestamp;
    private String messageId;
}

@Data
@Builder
class VideoAnalysisUpdate {
    private String streamId;
    private List<String> objectsDetected;
    private String brandDetected;
    private double confidence;
    private LocalDateTime timestamp;
    private int frameNumber;
}

@Data
@Builder
class StreamStatusUpdate {
    private String streamId;
    private boolean isLive;
    private int viewerCount;
    private LocalDateTime timestamp;
    private String statusChange;
}

@Data
@Builder
class ViewerCountUpdate {
    private String streamId;
    private int viewerCount;
    private LocalDateTime timestamp;
    private String trend;
}

@Data
@Builder
class SystemAlert {
    private String id;
    private String type;
    private String message;
    private String streamId;
    private String userId;
    private LocalDateTime timestamp;
    private boolean acknowledged;
}