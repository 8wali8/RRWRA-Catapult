import { gql } from '@apollo/client';

export const GET_ALL_STREAMS = gql`
  query GetAllStreams {
    getAllStreams {
      id
      title
      streamerName
      category
      viewerCount
      isLive
      startTime
      quality
      bitrate
      fps
      description
      tags
      language
      thumbnailUrl
      streamUrl
    }
  }
`;

export const GET_STREAM = gql`
  query GetStream($streamId: String!) {
    getStream(streamId: $streamId) {
      id
      title
      streamerName
      category
      viewerCount
      isLive
      startTime
      endTime
      quality
      bitrate
      fps
      description
      tags
      language
      thumbnailUrl
      streamUrl
    }
  }
`;

export const GET_TRENDING_STREAMS = gql`
  query GetTrendingStreams($limit: Int, $category: String) {
    getTrendingStreams(limit: $limit, category: $category) {
      id
      title
      streamerName
      category
      viewerCount
      isLive
      thumbnailUrl
    }
  }
`;

export const GET_CHAT_MESSAGES = gql`
  query GetChatMessages($streamId: String!, $limit: Int, $before: String, $after: String) {
    getChatMessages(streamId: $streamId, limit: $limit, before: $before, after: $after) {
      id
      userId
      username
      content
      messageType
      timestamp
      edited
      editedAt
      sentimentScore
      emotion
      flagged
      moderationReason
    }
  }
`;

export const GET_SENTIMENT_ANALYSIS = gql`
  query GetSentimentAnalysis($streamId: String!, $timeframe: String) {
    getSentimentAnalysis(streamId: $streamId, timeframe: $timeframe) {
      streamId
      overallSentiment {
        sentiment
        score
        confidence
      }
      emotionBreakdown {
        joy
        anger
        sadness
        fear
        surprise
        disgust
        neutral
      }
      messageCount
      timeframe
      lastUpdated
      trendData {
        timestamp
        sentiment
        score
        messageCount
      }
    }
  }
`;

export const GET_VIDEO_ANALYSIS = gql`
  query GetVideoAnalysis($streamId: String!, $timeframe: String) {
    getVideoAnalysis(streamId: $streamId, timeframe: $timeframe) {
      streamId
      objectsDetected
      brandDetections {
        id
        brand
        confidence
        timestamp
        region
        duration
        category
      }
      frameCount
      timeframe
      lastUpdated
      qualityMetrics {
        averageBitrate
        frameDrops
        bufferingEvents
        qualityScore
      }
    }
  }
`;

export const GET_ANALYTICS_DASHBOARD = gql`
  query GetAnalyticsDashboard($streamId: String!) {
    getAnalyticsDashboard(streamId: $streamId) {
      streamId
      viewerCount
      messageRate
      sentimentScore
      brandMentions
      engagement
      trending
      lastUpdated
      realtimeMetrics {
        chatActivity {
          messagesPerMinute
          activeUsers
          moderationActions
          averageMessageLength
        }
        viewerActivity {
          joinRate
          leaveRate
          averageWatchTime
          peakViewers
        }
        contentMetrics {
          videoQuality
          audioQuality
          streamStability
          interactionRate
        }
      }
    }
  }
`;

export const GET_RECOMMENDATIONS = gql`
  query GetRecommendations($userId: String!, $limit: Int) {
    getRecommendations(userId: $userId, limit: $limit) {
      id
      streamId
      title
      reason
      confidence
      category
      expectedViewTime
      personalizedScore
    }
  }
`;

export const GET_USER_ACTIVITY = gql`
  query GetUserActivity($userId: String!, $timeframe: String) {
    getUserActivity(userId: $userId, timeframe: $timeframe) {
      userId
      totalWatchTime
      streamsWatched
      messagesPosted
      favoriteCategories
      activityPeriod
      lastActive
      preferences {
        categories
        languages
        qualityPreference
        notificationsEnabled
      }
    }
  }
`;

export const SEARCH_STREAMS = gql`
  query SearchStreams($query: String!, $limit: Int, $category: String) {
    searchStreams(query: $query, limit: $limit, category: $category) {
      id
      title
      streamerName
      category
      viewerCount
      isLive
      thumbnailUrl
      description
      tags
    }
  }
`;

export const GET_BRAND_DETECTIONS = gql`
  query GetBrandDetections($streamId: String!, $timeframe: String) {
    getBrandDetections(streamId: $streamId, timeframe: $timeframe) {
      id
      brand
      confidence
      timestamp
      region
      duration
      category
    }
  }
`;

export const GET_STREAM_HEALTH = gql`
  query GetStreamHealth($streamId: String!) {
    getStreamHealth(streamId: $streamId) {
      streamId
      isHealthy
      bitrate
      fps
      quality
      latency
      dropRate
      lastCheck
      issues {
        type
        severity
        description
        timestamp
        resolved
      }
    }
  }
`;