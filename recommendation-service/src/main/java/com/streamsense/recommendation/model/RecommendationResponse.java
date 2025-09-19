package com.streamsense.recommendation.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Recommendation Response Model
 */
@Data
@Builder
public class RecommendationResponse {
    private String userId;
    private String streamId;
    private List<String> recommendations;
    private String algorithm;
    private double confidence;
    private String status;
}