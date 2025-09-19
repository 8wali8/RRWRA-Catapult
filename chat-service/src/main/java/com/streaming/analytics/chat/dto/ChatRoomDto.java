package com.streaming.analytics.chat.dto;

import lombok.Builder;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Chat Room DTO for API communication
 */
@Data
@Builder
public class ChatRoomDto {
    
    private String id;
    
    @NotBlank(message = "Room name is required")
    @Size(min = 1, max = 100, message = "Room name must be between 1 and 100 characters")
    private String name;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    @NotBlank(message = "Streamer ID is required")
    private String streamerId;
    
    @Builder.Default
    private boolean isActive = true;
    
    private LocalDateTime createdAt;
    
    @Builder.Default
    private int messageCount = 0;
    
    @Builder.Default
    private int activeUserCount = 0;
    
    // Room settings
    @Builder.Default
    private boolean requiresModeration = false;
    
    @Builder.Default
    private boolean allowsGuests = true;
    
    @Builder.Default
    private int maxUsers = 1000;
    
    private String category; // gaming, music, talk, etc.
    private String language;
    private String[] tags;
}