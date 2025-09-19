package com.streaming.analytics.graphql.service;

import com.streaming.analytics.graphql.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Stream Analytics Service Implementation
 * Aggregates data from all microservices and provides unified access
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamAnalyticsService {

    // Real-time data sinks for subscriptions
    private final Map<String, Sinks.Many<ChatMessage>> chatMessageSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<SentimentUpdate>> sentimentSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<VideoAnalysisUpdate>> videoAnalysisSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<BrandDetection>> brandDetectionSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<AnalyticsDashboard>> analyticsSinks = new ConcurrentHashMap<>();
    private final Sinks.Many<SystemAlert> systemAlertSink = Sinks.many().multicast().onBackpressureBuffer();

    // In-memory data stores (in production, these would be backed by databases/caches)
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessage>> chatMessages = new ConcurrentHashMap<>();
    private final Map<String, SentimentAnalysis> sentimentData = new ConcurrentHashMap<>();
    private final Map<String, VideoAnalysis> videoAnalysisData = new ConcurrentHashMap<>();
    private final Map<String, List<Recommendation>> userRecommendations = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeData() {
        // Initialize sample data
        createSampleStreams();
        log.info("Initialized GraphQL service with {} streams", streams.size());
    }

    // Query Methods

    public List<Stream> getAllStreams() {
        return new ArrayList<>(streams.values());
    }

    public Stream getStreamById(String streamId) {
        return streams.get(streamId);
    }

    public List<ChatMessage> getChatMessages(String streamId, int limit, String before, String after) {
        List<ChatMessage> messages = chatMessages.getOrDefault(streamId, new ArrayList<>());
        
        // Apply time filtering
        if (before != null || after != null) {
            LocalDateTime beforeTime = before != null ? LocalDateTime.parse(before) : null;
            LocalDateTime afterTime = after != null ? LocalDateTime.parse(after) : null;
            
            messages = messages.stream()
                    .filter(msg -> {
                        if (beforeTime != null && msg.getTimestamp().isAfter(beforeTime)) return false;
                        if (afterTime != null && msg.getTimestamp().isBefore(afterTime)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }
        
        return messages.stream().limit(limit).collect(Collectors.toList());
    }

    public SentimentAnalysis getSentimentAnalysis(String streamId, String timeframe) {
        return sentimentData.getOrDefault(streamId, createDefaultSentimentAnalysis(streamId));
    }

    public VideoAnalysis getVideoAnalysis(String streamId, String timeframe) {
        return videoAnalysisData.getOrDefault(streamId, createDefaultVideoAnalysis(streamId));
    }

    public AnalyticsDashboard getAnalyticsDashboard(String streamId) {
        Stream stream = streams.get(streamId);
        if (stream == null) {
            return null;
        }

        SentimentAnalysis sentiment = getSentimentAnalysis(streamId, "1h");
        VideoAnalysis video = getVideoAnalysis(streamId, "1h");
        List<ChatMessage> recentMessages = getChatMessages(streamId, 100, null, null);

        return AnalyticsDashboard.builder()
                .streamId(streamId)
                .viewerCount(stream.getViewerCount())
                .messageRate(calculateMessageRate(recentMessages))
                .sentimentScore(sentiment.getOverallSentiment().getScore())
                .brandMentions(video.getBrandDetections().size())
                .engagement(calculateEngagement(recentMessages, stream.getViewerCount()))
                .trending(stream.getViewerCount() > 1000)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public List<Recommendation> getRecommendations(String userId, int limit) {
        return userRecommendations.getOrDefault(userId, createDefaultRecommendations(userId))
                .stream().limit(limit).collect(Collectors.toList());
    }

    public List<Stream> getTrendingStreams(int limit, String category) {
        return streams.values().stream()
                .filter(stream -> category == null || category.equals(stream.getCategory()))
                .filter(Stream::isLive)
                .sorted((a, b) -> Integer.compare(b.getViewerCount(), a.getViewerCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public UserActivity getUserActivity(String userId, String timeframe) {
        // Sample user activity data
        return UserActivity.builder()
                .userId(userId)
                .totalWatchTime(120) // minutes
                .streamsWatched(5)
                .messagesPosted(25)
                .favoriteCategories(Arrays.asList("gaming", "music"))
                .activityPeriod(timeframe)
                .lastActive(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    public List<Stream> searchStreams(String query, int limit, String category) {
        return streams.values().stream()
                .filter(stream -> category == null || category.equals(stream.getCategory()))
                .filter(stream -> stream.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                                stream.getStreamerName().toLowerCase().contains(query.toLowerCase()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<BrandDetection> getBrandDetections(String streamId, String timeframe) {
        VideoAnalysis analysis = getVideoAnalysis(streamId, timeframe);
        return analysis != null ? analysis.getBrandDetections() : new ArrayList<>();
    }

    public StreamHealth getStreamHealth(String streamId) {
        Stream stream = streams.get(streamId);
        if (stream == null) {
            return null;
        }

        return StreamHealth.builder()
                .streamId(streamId)
                .isHealthy(stream.isLive())
                .bitrate(stream.getBitrate())
                .fps(stream.getFps())
                .quality(stream.getQuality())
                .latency(50) // ms
                .dropRate(0.1) // %
                .lastCheck(LocalDateTime.now())
                .build();
    }

    // Subscription Methods

    public Publisher<ChatMessage> subscribeToChatMessages(String streamId) {
        Sinks.Many<ChatMessage> sink = chatMessageSinks.computeIfAbsent(streamId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    public Publisher<SentimentUpdate> subscribeToSentimentUpdates(String streamId) {
        Sinks.Many<SentimentUpdate> sink = sentimentSinks.computeIfAbsent(streamId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    public Publisher<VideoAnalysisUpdate> subscribeToVideoAnalysis(String streamId) {
        Sinks.Many<VideoAnalysisUpdate> sink = videoAnalysisSinks.computeIfAbsent(streamId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    public Publisher<BrandDetection> subscribeToBrandDetections(String streamId) {
        Sinks.Many<BrandDetection> sink = brandDetectionSinks.computeIfAbsent(streamId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    public Publisher<AnalyticsDashboard> subscribeToAnalyticsUpdates(String streamId) {
        Sinks.Many<AnalyticsDashboard> sink = analyticsSinks.computeIfAbsent(streamId,
                k -> Sinks.many().multicast().onBackpressureBuffer());
        return sink.asFlux();
    }

    public Publisher<StreamStatusUpdate> subscribeToStreamStatus(String streamId) {
        return Flux.interval(java.time.Duration.ofSeconds(30))
                .map(tick -> {
                    Stream stream = streams.get(streamId);
                    return StreamStatusUpdate.builder()
                            .streamId(streamId)
                            .isLive(stream != null && stream.isLive())
                            .viewerCount(stream != null ? stream.getViewerCount() : 0)
                            .timestamp(LocalDateTime.now())
                            .build();
                });
    }

    public Publisher<ViewerCountUpdate> subscribeToViewerCount(String streamId) {
        return Flux.interval(java.time.Duration.ofSeconds(10))
                .map(tick -> {
                    Stream stream = streams.get(streamId);
                    int count = stream != null ? stream.getViewerCount() : 0;
                    // Simulate slight viewer count fluctuations
                    count += (int) (Math.random() * 20 - 10);
                    return ViewerCountUpdate.builder()
                            .streamId(streamId)
                            .viewerCount(Math.max(0, count))
                            .timestamp(LocalDateTime.now())
                            .build();
                });
    }

    public Publisher<List<Recommendation>> subscribeToRecommendations(String userId) {
        return Flux.interval(java.time.Duration.ofMinutes(5))
                .map(tick -> getRecommendations(userId, 10));
    }

    public Publisher<List<Stream>> subscribeToTrendingStreams(String category) {
        return Flux.interval(java.time.Duration.ofMinutes(2))
                .map(tick -> getTrendingStreams(20, category));
    }

    public Publisher<SystemAlert> subscribeToSystemAlerts() {
        return systemAlertSink.asFlux();
    }

    // Kafka Listeners for real-time data ingestion

    @KafkaListener(topics = "chat-messages", groupId = "graphql-service")
    public void processChatMessage(String messageData) {
        try {
            // Parse and process chat message
            // In a real implementation, this would deserialize from JSON
            log.debug("Processing chat message: {}", messageData);
            
            // Create sample chat message
            ChatMessage message = ChatMessage.builder()
                    .id("msg-" + System.currentTimeMillis())
                    .userId("user-123")
                    .username("SampleUser")
                    .content("Sample message content")
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Emit to subscribers
            String streamId = "stream-1"; // Extract from message
            Sinks.Many<ChatMessage> sink = chatMessageSinks.get(streamId);
            if (sink != null) {
                sink.tryEmitNext(message);
            }
            
        } catch (Exception e) {
            log.error("Error processing chat message", e);
        }
    }

    @KafkaListener(topics = "sentiment-analysis", groupId = "graphql-service")
    public void processSentimentUpdate(String sentimentData) {
        try {
            log.debug("Processing sentiment update: {}", sentimentData);
            
            // Create sample sentiment update
            SentimentUpdate update = SentimentUpdate.builder()
                    .streamId("stream-1")
                    .sentiment("positive")
                    .score(0.8)
                    .confidence(0.9)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Emit to subscribers
            Sinks.Many<SentimentUpdate> sink = sentimentSinks.get(update.getStreamId());
            if (sink != null) {
                sink.tryEmitNext(update);
            }
            
        } catch (Exception e) {
            log.error("Error processing sentiment update", e);
        }
    }

    @KafkaListener(topics = "video-analysis", groupId = "graphql-service")
    public void processVideoAnalysis(String videoData) {
        try {
            log.debug("Processing video analysis: {}", videoData);
            
            // Create sample video analysis update
            VideoAnalysisUpdate update = VideoAnalysisUpdate.builder()
                    .streamId("stream-1")
                    .objectsDetected(Arrays.asList("person", "microphone"))
                    .brandDetected("Sample Brand")
                    .confidence(0.85)
                    .timestamp(LocalDateTime.now())
                    .build();
            
            // Emit to subscribers
            Sinks.Many<VideoAnalysisUpdate> sink = videoAnalysisSinks.get(update.getStreamId());
            if (sink != null) {
                sink.tryEmitNext(update);
            }
            
        } catch (Exception e) {
            log.error("Error processing video analysis", e);
        }
    }

    // Helper methods

    private void createSampleStreams() {
        streams.put("stream-1", Stream.builder()
                .id("stream-1")
                .title("Amazing Gaming Stream!")
                .streamerName("ProGamer123")
                .category("gaming")
                .viewerCount(1500)
                .isLive(true)
                .startTime(LocalDateTime.now().minusHours(2))
                .quality("1080p")
                .bitrate(6000)
                .fps(60)
                .build());
        
        streams.put("stream-2", Stream.builder()
                .id("stream-2")
                .title("Music & Chill")
                .streamerName("MusicMaster")
                .category("music")
                .viewerCount(800)
                .isLive(true)
                .startTime(LocalDateTime.now().minusHours(1))
                .quality("720p")
                .bitrate(4000)
                .fps(30)
                .build());
    }

    private SentimentAnalysis createDefaultSentimentAnalysis(String streamId) {
        return SentimentAnalysis.builder()
                .streamId(streamId)
                .overallSentiment(SentimentScore.builder()
                        .sentiment("positive")
                        .score(0.7)
                        .confidence(0.85)
                        .build())
                .emotionBreakdown(Map.of(
                        "joy", 0.4,
                        "excitement", 0.3,
                        "neutral", 0.2,
                        "frustration", 0.1
                ))
                .messageCount(150)
                .timeframe("1h")
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private VideoAnalysis createDefaultVideoAnalysis(String streamId) {
        return VideoAnalysis.builder()
                .streamId(streamId)
                .objectsDetected(Arrays.asList("person", "gaming_setup", "microphone"))
                .brandDetections(Arrays.asList(
                        BrandDetection.builder()
                                .brand("SampleBrand")
                                .confidence(0.9)
                                .timestamp(LocalDateTime.now())
                                .region("top-right")
                                .build()
                ))
                .frameCount(7200) // 2 hours at 60fps
                .timeframe("1h")
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    private List<Recommendation> createDefaultRecommendations(String userId) {
        return Arrays.asList(
                Recommendation.builder()
                        .id("rec-1")
                        .streamId("stream-1")
                        .title("Recommended Gaming Stream")
                        .reason("Based on your viewing history")
                        .confidence(0.8)
                        .build(),
                Recommendation.builder()
                        .id("rec-2")
                        .streamId("stream-2")
                        .title("Music Stream You Might Like")
                        .reason("Popular in your region")
                        .confidence(0.7)
                        .build()
        );
    }

    private double calculateMessageRate(List<ChatMessage> messages) {
        if (messages.isEmpty()) return 0.0;
        
        LocalDateTime now = LocalDateTime.now();
        long recentMessages = messages.stream()
                .filter(msg -> java.time.Duration.between(msg.getTimestamp(), now).toMinutes() < 5)
                .count();
        
        return recentMessages / 5.0; // messages per minute
    }

    private double calculateEngagement(List<ChatMessage> messages, int viewerCount) {
        if (viewerCount == 0) return 0.0;
        
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        long activeUsers = messages.stream()
                .filter(msg -> msg.getTimestamp().isAfter(fiveMinutesAgo))
                .map(ChatMessage::getUserId)
                .distinct()
                .count();
        
        return (double) activeUsers / viewerCount * 100; // percentage
    }
}