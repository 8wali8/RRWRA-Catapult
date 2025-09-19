package com.streaming.analytics.chat.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Sentiment Analysis Event - matches README implementation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalysisEvent {
    private String messageId;
    private String streamId;
    private String sentiment;
    private Double confidence;
    private String emotion;
    private LocalDateTime timestamp;
    private String modelVersion;
}