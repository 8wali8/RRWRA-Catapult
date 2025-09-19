package com.streaming.analytics.graphql.resolver;

import com.streaming.analytics.graphql.model.*;
import com.streaming.analytics.graphql.service.StreamAnalyticsService;
import graphql.kickstart.tools.GraphQLQueryResolver;
import graphql.kickstart.tools.GraphQLSubscriptionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * GraphQL Query and Subscription Resolver
 * Provides unified access to all streaming analytics data
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreamAnalyticsResolver implements GraphQLQueryResolver, GraphQLSubscriptionResolver {

    private final StreamAnalyticsService analyticsService;

    // Query Resolvers

    /**
     * Get all active streams
     */
    public List<Stream> getAllStreams() {
        log.info("GraphQL Query: getAllStreams");
        return analyticsService.getAllStreams();
    }

    /**
     * Get stream by ID
     */
    public Stream getStream(String streamId) {
        log.info("GraphQL Query: getStream({})", streamId);
        return analyticsService.getStreamById(streamId);
    }

    /**
     * Get chat messages for a stream
     */
    public List<ChatMessage> getChatMessages(String streamId, Integer limit, String before, String after) {
        log.info("GraphQL Query: getChatMessages(streamId={}, limit={})", streamId, limit);
        return analyticsService.getChatMessages(streamId, limit != null ? limit : 50, before, after);
    }

    /**
     * Get sentiment analysis for stream
     */
    public SentimentAnalysis getSentimentAnalysis(String streamId, String timeframe) {
        log.info("GraphQL Query: getSentimentAnalysis(streamId={}, timeframe={})", streamId, timeframe);
        return analyticsService.getSentimentAnalysis(streamId, timeframe);
    }

    /**
     * Get video analysis data
     */
    public VideoAnalysis getVideoAnalysis(String streamId, String timeframe) {
        log.info("GraphQL Query: getVideoAnalysis(streamId={}, timeframe={})", streamId, timeframe);
        return analyticsService.getVideoAnalysis(streamId, timeframe);
    }

    /**
     * Get real-time analytics dashboard data
     */
    public AnalyticsDashboard getAnalyticsDashboard(String streamId) {
        log.info("GraphQL Query: getAnalyticsDashboard({})", streamId);
        return analyticsService.getAnalyticsDashboard(streamId);
    }

    /**
     * Get recommendations for a user
     */
    public List<Recommendation> getRecommendations(String userId, Integer limit) {
        log.info("GraphQL Query: getRecommendations(userId={}, limit={})", userId, limit);
        return analyticsService.getRecommendations(userId, limit != null ? limit : 10);
    }

    /**
     * Get trending streams
     */
    public List<Stream> getTrendingStreams(Integer limit, String category) {
        log.info("GraphQL Query: getTrendingStreams(limit={}, category={})", limit, category);
        return analyticsService.getTrendingStreams(limit != null ? limit : 20, category);
    }

    /**
     * Get user activity data
     */
    public UserActivity getUserActivity(String userId, String timeframe) {
        log.info("GraphQL Query: getUserActivity(userId={}, timeframe={})", userId, timeframe);
        return analyticsService.getUserActivity(userId, timeframe);
    }

    /**
     * Search streams by query
     */
    public List<Stream> searchStreams(String query, Integer limit, String category) {
        log.info("GraphQL Query: searchStreams(query={}, limit={}, category={})", query, limit, category);
        return analyticsService.searchStreams(query, limit != null ? limit : 20, category);
    }

    /**
     * Get brand detections in video
     */
    public List<BrandDetection> getBrandDetections(String streamId, String timeframe) {
        log.info("GraphQL Query: getBrandDetections(streamId={}, timeframe={})", streamId, timeframe);
        return analyticsService.getBrandDetections(streamId, timeframe);
    }

    /**
     * Get stream health metrics
     */
    public StreamHealth getStreamHealth(String streamId) {
        log.info("GraphQL Query: getStreamHealth({})", streamId);
        return analyticsService.getStreamHealth(streamId);
    }

    // Subscription Resolvers

    /**
     * Subscribe to real-time chat messages
     */
    public Publisher<ChatMessage> chatMessageAdded(String streamId) {
        log.info("GraphQL Subscription: chatMessageAdded({})", streamId);
        return analyticsService.subscribeToChatMessages(streamId);
    }

    /**
     * Subscribe to real-time sentiment updates
     */
    public Publisher<SentimentUpdate> sentimentUpdated(String streamId) {
        log.info("GraphQL Subscription: sentimentUpdated({})", streamId);
        return analyticsService.subscribeToSentimentUpdates(streamId);
    }

    /**
     * Subscribe to real-time video analysis updates
     */
    public Publisher<VideoAnalysisUpdate> videoAnalysisUpdated(String streamId) {
        log.info("GraphQL Subscription: videoAnalysisUpdated({})", streamId);
        return analyticsService.subscribeToVideoAnalysis(streamId);
    }

    /**
     * Subscribe to brand detection events
     */
    public Publisher<BrandDetection> brandDetected(String streamId) {
        log.info("GraphQL Subscription: brandDetected({})", streamId);
        return analyticsService.subscribeToBrandDetections(streamId);
    }

    /**
     * Subscribe to analytics dashboard updates
     */
    public Publisher<AnalyticsDashboard> analyticsUpdated(String streamId) {
        log.info("GraphQL Subscription: analyticsUpdated({})", streamId);
        return analyticsService.subscribeToAnalyticsUpdates(streamId);
    }

    /**
     * Subscribe to stream status changes
     */
    public Publisher<StreamStatusUpdate> streamStatusChanged(String streamId) {
        log.info("GraphQL Subscription: streamStatusChanged({})", streamId);
        return analyticsService.subscribeToStreamStatus(streamId);
    }

    /**
     * Subscribe to viewer count updates
     */
    public Publisher<ViewerCountUpdate> viewerCountUpdated(String streamId) {
        log.info("GraphQL Subscription: viewerCountUpdated({})", streamId);
        return analyticsService.subscribeToViewerCount(streamId);
    }

    /**
     * Subscribe to recommendation updates for a user
     */
    public Publisher<List<Recommendation>> recommendationsUpdated(String userId) {
        log.info("GraphQL Subscription: recommendationsUpdated({})", userId);
        return analyticsService.subscribeToRecommendations(userId);
    }

    /**
     * Subscribe to trending streams updates
     */
    public Publisher<List<Stream>> trendingStreamsUpdated(String category) {
        log.info("GraphQL Subscription: trendingStreamsUpdated({})", category);
        return analyticsService.subscribeToTrendingStreams(category);
    }

    /**
     * Subscribe to system alerts and notifications
     */
    public Publisher<SystemAlert> systemAlertReceived() {
        log.info("GraphQL Subscription: systemAlertReceived");
        return analyticsService.subscribeToSystemAlerts();
    }
}