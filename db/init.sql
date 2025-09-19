-- Enterprise Streaming Analytics Platform Database Schema
-- Comprehensive data model for real-time streaming analytics

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Users and Authentication
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- Streaming Platforms
CREATE TABLE streaming_platforms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) NOT NULL,
    api_endpoint TEXT,
    webhook_secret VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Streamers/Content Creators
CREATE TABLE streamers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    platform_id UUID REFERENCES streaming_platforms(id),
    platform_user_id VARCHAR(100),
    follower_count INTEGER DEFAULT 0,
    subscriber_count INTEGER DEFAULT 0,
    avatar_url TEXT,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    UNIQUE(platform_id, platform_user_id)
);

-- Live Streams
CREATE TABLE streams (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    streamer_id UUID REFERENCES streamers(id),
    title VARCHAR(255),
    description TEXT,
    category VARCHAR(100),
    game_name VARCHAR(100),
    viewer_count INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    duration_seconds INTEGER,
    thumbnail_url TEXT,
    stream_url TEXT,
    is_live BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Chat Messages
CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID REFERENCES streams(id),
    user_id UUID REFERENCES users(id),
    username VARCHAR(100),
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    message_type VARCHAR(20) DEFAULT 'chat', -- chat, subscription, donation, etc.
    badges JSONB,
    emotes JSONB,
    sentiment_score DECIMAL(3,2),
    emotion VARCHAR(20),
    toxicity_score DECIMAL(3,2),
    processed_at TIMESTAMP
);

-- Sentiment Analysis Results
CREATE TABLE sentiment_analysis (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    chat_message_id UUID REFERENCES chat_messages(id),
    sentiment VARCHAR(20) NOT NULL, -- positive, negative, neutral
    confidence DECIMAL(5,4) NOT NULL,
    emotion VARCHAR(20), -- joy, anger, sadness, fear, surprise, disgust
    emotion_confidence DECIMAL(5,4),
    model_version VARCHAR(50),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Video Analysis
CREATE TABLE video_frames (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID REFERENCES streams(id),
    frame_number INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    frame_url TEXT,
    objects_detected JSONB,
    brands_detected JSONB,
    emotions_detected JSONB,
    processing_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Object Detection Results
CREATE TABLE detected_objects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    frame_id UUID REFERENCES video_frames(id),
    object_class VARCHAR(100) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    bounding_box JSONB NOT NULL, -- {x, y, width, height}
    tracking_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Brand Recognition
CREATE TABLE brand_detections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    frame_id UUID REFERENCES video_frames(id),
    brand_name VARCHAR(100) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    bounding_box JSONB NOT NULL,
    brand_category VARCHAR(50),
    sponsor_value DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sponsors and Partnerships
CREATE TABLE sponsors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    brand_keywords TEXT[],
    logo_colors JSONB,
    website_url TEXT,
    industry VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Stream Analytics Aggregations
CREATE TABLE stream_analytics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID REFERENCES streams(id),
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(15,4),
    metric_timestamp TIMESTAMP NOT NULL,
    aggregation_period VARCHAR(20), -- minute, hour, day
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Real-time Metrics Cache
CREATE TABLE metrics_cache (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID REFERENCES streams(id),
    metric_type VARCHAR(50) NOT NULL,
    metric_data JSONB NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notification Events
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    stream_id UUID REFERENCES streams(id),
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    message TEXT,
    data JSONB,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- API Usage Tracking
CREATE TABLE api_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id),
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INTEGER,
    response_time_ms INTEGER,
    request_size INTEGER,
    response_size INTEGER,
    user_agent TEXT,
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for Performance
CREATE INDEX idx_chat_messages_stream_id ON chat_messages(stream_id);
CREATE INDEX idx_chat_messages_timestamp ON chat_messages(timestamp);
CREATE INDEX idx_chat_messages_username ON chat_messages(username);
CREATE INDEX idx_sentiment_analysis_sentiment ON sentiment_analysis(sentiment);
CREATE INDEX idx_video_frames_stream_id ON video_frames(stream_id);
CREATE INDEX idx_video_frames_timestamp ON video_frames(timestamp);
CREATE INDEX idx_detected_objects_class ON detected_objects(object_class);
CREATE INDEX idx_brand_detections_brand ON brand_detections(brand_name);
CREATE INDEX idx_stream_analytics_stream_metric ON stream_analytics(stream_id, metric_name);
CREATE INDEX idx_metrics_cache_stream_type ON metrics_cache(stream_id, metric_type);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, is_read);
CREATE INDEX idx_api_requests_endpoint ON api_requests(endpoint);
CREATE INDEX idx_streamers_platform ON streamers(platform_id);
CREATE INDEX idx_streams_streamer ON streams(streamer_id);

-- Text search indexes
CREATE INDEX idx_chat_messages_message_gin ON chat_messages USING gin(to_tsvector('english', message));
CREATE INDEX idx_streamers_username_gin ON streamers USING gin(username gin_trgm_ops);
CREATE INDEX idx_streams_title_gin ON streams USING gin(to_tsvector('english', title));

-- Insert initial data
INSERT INTO streaming_platforms (name, api_endpoint) VALUES 
('Twitch', 'https://api.twitch.tv/helix'),
('YouTube', 'https://www.googleapis.com/youtube/v3'),
('Discord', 'https://discord.com/api/v10'),
('Kick', 'https://kick.com/api/v1');

INSERT INTO sponsors (name, brand_keywords, industry) VALUES 
('Monster Energy', ARRAY['monster', 'energy', 'drink'], 'Beverages'),
('Red Bull', ARRAY['red bull', 'redbull', 'energy'], 'Beverages'),
('NVIDIA', ARRAY['nvidia', 'geforce', 'rtx'], 'Technology'),
('Intel', ARRAY['intel', 'core', 'processor'], 'Technology'),
('HyperX', ARRAY['hyperx', 'gaming', 'headset'], 'Gaming Peripherals');

-- Create triggers for updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_streamers_updated_at BEFORE UPDATE ON streamers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_streams_updated_at BEFORE UPDATE ON streams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();