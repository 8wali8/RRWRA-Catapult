package com.streamsense.sentiment.model;

import lombok.Builder;
import lombok.Data;

/**
 * Sentiment Analysis Response Model
 */
@Data
@Builder
public class SentimentResponse {
    private String text;
    private String sentiment;
    private double confidence;
    private String emotion;
    private String status;
    private String modelVersion;
}