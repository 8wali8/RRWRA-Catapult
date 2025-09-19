package com.streaming.analytics.chat.dto;

import lombok.Builder;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Chat Message DTO for API communication
 */
@Data
@Builder
public class ChatMessageDto {
    
    private String id;
    
    @NotBlank(message = "Room ID is required")
    private String roomId;
    
    @NotBlank(message = "User ID is required") 
    private String userId;
    
    @NotBlank(message = "Username is required")
    @Size(min = 1, max = 50, message = "Username must be between 1 and 50 characters")
    private String username;
    
    @NotBlank(message = "Content is required")
    @Size(min = 1, max = 2000, message = "Message content must be between 1 and 2000 characters")
    private String content;
    
    private String messageType = "text"; // text, image, emote, system
    
    private LocalDateTime timestamp;
    
    private boolean edited = false;
    
    private LocalDateTime editedAt;
    
    // Metadata
    private String sentimentScore;
    private String emotion;
    private boolean flagged = false;
    private String moderationReason;
}