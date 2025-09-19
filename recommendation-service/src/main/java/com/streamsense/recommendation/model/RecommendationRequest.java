package com.streamsense.recommendation.model;

import lombok.Data;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.List;

/**
 * Recommendation Request Model
 */
@Data
public class RecommendationRequest {
    @NotNull
    private List<String> userIds;
    
    @Positive
    private int limit = 10;
    
    private String algorithm;
    private String category;
}