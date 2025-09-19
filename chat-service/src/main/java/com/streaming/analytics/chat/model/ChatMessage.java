package com.streaming.analytics.chat.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Chat Message Entity
 */
@Data
@Builder
public class ChatMessage {
    
    private String id;
    private String roomId;
    private String userId;
    private String username;
    private String content;
    
    @Builder.Default
    private String messageType = "text";
    
    private LocalDateTime timestamp;
    
    @Builder.Default
    private boolean edited = false;
    
    private LocalDateTime editedAt;
    
    // Moderation fields
    @Builder.Default
    private boolean flagged = false;
    
    private String moderationReason;
    private String moderatedBy;
    private LocalDateTime moderatedAt;
    
    // Analytics fields
    private String sentimentScore;
    private String emotion;
    private Double toxicityScore;
    
    // Message metadata
    private String ipAddress;
    private String userAgent;
    private String platform; // web, mobile, etc.
}