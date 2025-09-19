import { gql } from '@apollo/client';

export const CHAT_MESSAGE_ADDED = gql`
  subscription ChatMessageAdded($streamId: String!) {
    chatMessageAdded(streamId: $streamId) {
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

export const SENTIMENT_UPDATED = gql`
  subscription SentimentUpdated($streamId: String!) {
    sentimentUpdated(streamId: $streamId) {
      streamId
      sentiment
      score
      confidence
      timestamp
      messageId
    }
  }
`;

export const VIDEO_ANALYSIS_UPDATED = gql`
  subscription VideoAnalysisUpdated($streamId: String!) {
    videoAnalysisUpdated(streamId: $streamId) {
      streamId
      objectsDetected
      brandDetected
      confidence
      timestamp
      frameNumber
    }
  }
`;

export const BRAND_DETECTED = gql`
  subscription BrandDetected($streamId: String!) {
    brandDetected(streamId: $streamId) {
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

export const ANALYTICS_UPDATED = gql`
  subscription AnalyticsUpdated($streamId: String!) {
    analyticsUpdated(streamId: $streamId) {
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

export const STREAM_STATUS_CHANGED = gql`
  subscription StreamStatusChanged($streamId: String!) {
    streamStatusChanged(streamId: $streamId) {
      streamId
      isLive
      viewerCount
      timestamp
      statusChange
    }
  }
`;

export const VIEWER_COUNT_UPDATED = gql`
  subscription ViewerCountUpdated($streamId: String!) {
    viewerCountUpdated(streamId: $streamId) {
      streamId
      viewerCount
      timestamp
      trend
    }
  }
`;

export const RECOMMENDATIONS_UPDATED = gql`
  subscription RecommendationsUpdated($userId: String!) {
    recommendationsUpdated(userId: $userId) {
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

export const TRENDING_STREAMS_UPDATED = gql`
  subscription TrendingStreamsUpdated($category: String) {
    trendingStreamsUpdated(category: $category) {
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

export const SYSTEM_ALERT_RECEIVED = gql`
  subscription SystemAlertReceived {
    systemAlertReceived {
      id
      type
      message
      streamId
      userId
      timestamp
      acknowledged
      details {
        component
        errorCode
        stackTrace
        affectedUsers
        estimatedResolution
      }
    }
  }
`;