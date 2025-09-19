# Cassandra initialization script for streaming analytics
# Creates keyspaces and tables for high-velocity streaming data

# Create keyspace for streaming analytics
CREATE KEYSPACE IF NOT EXISTS streaming_analytics
WITH REPLICATION = {
    'class': 'SimpleStrategy',
    'replication_factor': 3
};

USE streaming_analytics;

# Chat messages table - optimized for high write throughput
CREATE TABLE IF NOT EXISTS chat_messages (
    stream_id text,
    timestamp timestamp,
    message_id uuid,
    username text,
    message_text text,
    sentiment_score double,
    emotion text,
    language text,
    PRIMARY KEY (stream_id, timestamp, message_id)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {'class': 'TimeWindowCompactionStrategy'};

# Video frames table - for sponsor detection tracking
CREATE TABLE IF NOT EXISTS video_frames (
    stream_id text,
    timestamp timestamp,
    frame_id uuid,
    sponsor_detections list<frozen<map<text, text>>>,
    objects_detected list<text>,
    frame_quality_score double,
    PRIMARY KEY (stream_id, timestamp, frame_id)
) WITH CLUSTERING ORDER BY (timestamp DESC)
AND compaction = {'class': 'TimeWindowCompactionStrategy'};

# Stream analytics aggregates - for real-time dashboards
CREATE TABLE IF NOT EXISTS stream_analytics (
    stream_id text,
    hour timestamp,
    total_messages counter,
    positive_sentiment counter,
    negative_sentiment counter,
    neutral_sentiment counter,
    sponsor_mentions counter,
    unique_viewers counter,
    PRIMARY KEY (stream_id, hour)
) WITH CLUSTERING ORDER BY (hour DESC);

# Sponsor detection events - for brand analytics
CREATE TABLE IF NOT EXISTS sponsor_detections (
    sponsor_brand text,
    detection_date date,
    stream_id text,
    timestamp timestamp,
    confidence_score double,
    bounding_box frozen<map<text, int>>,
    PRIMARY KEY ((sponsor_brand, detection_date), timestamp, stream_id)
) WITH CLUSTERING ORDER BY (timestamp DESC);

# User engagement metrics - for personalization
CREATE TABLE IF NOT EXISTS user_engagement (
    user_id text,
    stream_id text,
    engagement_date date,
    watch_duration int,
    chat_messages_sent int,
    sponsor_interactions int,
    sentiment_distribution map<text, int>,
    PRIMARY KEY ((user_id, engagement_date), stream_id)
);

# Real-time trending topics - for content discovery
CREATE TABLE IF NOT EXISTS trending_topics (
    topic text,
    time_window timestamp,
    mention_count counter,
    sentiment_avg double,
    streams_mentioned set<text>,
    PRIMARY KEY (topic, time_window)
) WITH CLUSTERING ORDER BY (time_window DESC);

# Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS chat_username_idx ON chat_messages (username);
CREATE INDEX IF NOT EXISTS video_sponsor_idx ON video_frames (sponsor_detections);
CREATE INDEX IF NOT EXISTS detection_confidence_idx ON sponsor_detections (confidence_score);