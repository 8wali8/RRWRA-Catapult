package com.streaming.analytics.chat.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Chat Message Event - matches README implementation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent {
    private String messageId;
    private String streamId;
    private String username;
    private String message;
    private LocalDateTime timestamp;
    private String platform;
    private Object metadata;
}