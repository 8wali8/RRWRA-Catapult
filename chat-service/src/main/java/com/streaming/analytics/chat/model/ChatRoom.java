package com.streaming.analytics.chat.model;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Chat Room Entity
 */
@Data
@Builder
public class ChatRoom {
    
    private String id;
    private String name;
    private String description;
    private String streamerId;
    
    @Builder.Default
    private boolean isActive = true;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Builder.Default
    private int messageCount = 0;
    
    @Builder.Default
    private int activeUserCount = 0;
    
    @Builder.Default
    private int totalUserCount = 0;
    
    // Room settings
    @Builder.Default
    private boolean requiresModeration = false;
    
    @Builder.Default
    private boolean allowsGuests = true;
    
    @Builder.Default
    private int maxUsers = 1000;
    
    @Builder.Default
    private boolean isPrivate = false;
    
    private String password; // for private rooms
    
    // Categorization
    private String category; // gaming, music, talk, tech, etc.
    private String language;
    private String[] tags;
    
    // Analytics
    private double averageMessageRate; // messages per minute
    private LocalDateTime lastActivityAt;
    private String mostActiveUser;
    
    // Moderation settings
    @Builder.Default
    private boolean autoModeration = true;
    
    @Builder.Default
    private boolean slowMode = false;
    
    private int slowModeSeconds;
    
    @Builder.Default
    private boolean subscriberOnly = false;
    
    @Builder.Default
    private boolean emotesOnly = false;
}