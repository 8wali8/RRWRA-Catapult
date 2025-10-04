package com.streamsense.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.chat.model.ChatMessage;
import com.streamsense.chat.model.EmojiReaction;
import com.streamsense.chat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.time.Instant;
import java.util.Map;

@Component
@ServerEndpoint(value = "/chat/stream/{streamId}")
public class ChatWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Store active sessions per stream
    private static final Map<String, CopyOnWriteArraySet<Session>> streamSessions = new ConcurrentHashMap<>();
    
    @Autowired
    private static ChatService chatService;
    
    @OnOpen
    public void onOpen(Session session, @PathParam("streamId") String streamId) {
        logger.info("WebSocket connection opened for stream: {} from session: {}", streamId, session.getId());
        
        streamSessions.computeIfAbsent(streamId, k -> new CopyOnWriteArraySet<>()).add(session);
        
        // Send welcome message with stream stats
        try {
            Map<String, Object> welcomeMessage = Map.of(
                "type", "welcome",
                "streamId", streamId,
                "activeViewers", streamSessions.get(streamId).size(),
                "timestamp", Instant.now().toString()
            );
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(welcomeMessage));
        } catch (IOException e) {
            logger.error("Error sending welcome message", e);
        }
        
        broadcastViewerCount(streamId);
    }
    
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("streamId") String streamId) {
        try {
            Map<String, Object> messageData = objectMapper.readValue(message, Map.class);
            String messageType = (String) messageData.get("type");
            
            switch (messageType) {
                case "chat":
                    handleChatMessage(messageData, session, streamId);
                    break;
                case "emoji_reaction":
                    handleEmojiReaction(messageData, session, streamId);
                    break;
                case "typing":
                    handleTypingIndicator(messageData, session, streamId);
                    break;
                case "viewer_join":
                    handleViewerJoin(messageData, session, streamId);
                    break;
                default:
                    logger.warn("Unknown message type: {}", messageType);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", message, e);
        }
    }
    
    @OnClose
    public void onClose(Session session, @PathParam("streamId") String streamId) {
        logger.info("WebSocket connection closed for stream: {} from session: {}", streamId, session.getId());
        
        CopyOnWriteArraySet<Session> sessions = streamSessions.get(streamId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                streamSessions.remove(streamId);
            } else {
                broadcastViewerCount(streamId);
            }
        }
    }
    
    @OnError
    public void onError(Session session, Throwable throwable) {
        logger.error("WebSocket error for session: {}", session.getId(), throwable);
    }
    
    private void handleChatMessage(Map<String, Object> messageData, Session session, String streamId) {
        try {
            // Create chat message
            ChatMessage chatMessage = ChatMessage.builder()
                .streamId(streamId)
                .username((String) messageData.get("username"))
                .message((String) messageData.get("message"))
                .timestamp(Instant.now())
                .sessionId(session.getId())
                .build();
            
            // Save to database and process through ML
            chatService.processMessage(chatMessage);
            
            // Broadcast to all viewers
            Map<String, Object> broadcastMessage = Map.of(
                "type", "chat",
                "username", chatMessage.getUsername(),
                "message", chatMessage.getMessage(),
                "timestamp", chatMessage.getTimestamp().toString(),
                "sentiment", chatMessage.getSentiment() != null ? chatMessage.getSentiment() : "NEUTRAL",
                "messageId", chatMessage.getId()
            );
            
            broadcastToStream(streamId, broadcastMessage);
            
        } catch (Exception e) {
            logger.error("Error handling chat message", e);
        }
    }
    
    private void handleEmojiReaction(Map<String, Object> messageData, Session session, String streamId) {
        try {
            EmojiReaction reaction = EmojiReaction.builder()
                .streamId(streamId)
                .emoji((String) messageData.get("emoji"))
                .username((String) messageData.get("username"))
                .targetMessageId((String) messageData.get("targetMessageId"))
                .timestamp(Instant.now())
                .build();
            
            // Save reaction
            chatService.saveEmojiReaction(reaction);
            
            // Broadcast reaction to all viewers
            Map<String, Object> broadcastReaction = Map.of(
                "type", "emoji_reaction",
                "emoji", reaction.getEmoji(),
                "username", reaction.getUsername(),
                "targetMessageId", reaction.getTargetMessageId(),
                "timestamp", reaction.getTimestamp().toString()
            );
            
            broadcastToStream(streamId, broadcastReaction);
            
        } catch (Exception e) {
            logger.error("Error handling emoji reaction", e);
        }
    }
    
    private void handleTypingIndicator(Map<String, Object> messageData, Session session, String streamId) {
        // Broadcast typing indicator (excluding sender)
        Map<String, Object> typingMessage = Map.of(
            "type", "typing",
            "username", messageData.get("username"),
            "isTyping", messageData.get("isTyping")
        );
        
        broadcastToStreamExcept(streamId, typingMessage, session);
    }
    
    private void handleViewerJoin(Map<String, Object> messageData, Session session, String streamId) {
        // Broadcast viewer join notification
        Map<String, Object> joinMessage = Map.of(
            "type", "viewer_join",
            "username", messageData.get("username"),
            "timestamp", Instant.now().toString()
        );
        
        broadcastToStreamExcept(streamId, joinMessage, session);
    }
    
    private void broadcastViewerCount(String streamId) {
        CopyOnWriteArraySet<Session> sessions = streamSessions.get(streamId);
        if (sessions != null) {
            Map<String, Object> viewerCountMessage = Map.of(
                "type", "viewer_count",
                "count", sessions.size(),
                "timestamp", Instant.now().toString()
            );
            
            broadcastToStream(streamId, viewerCountMessage);
        }
    }
    
    private void broadcastToStream(String streamId, Map<String, Object> message) {
        CopyOnWriteArraySet<Session> sessions = streamSessions.get(streamId);
        if (sessions != null) {
            String messageJson;
            try {
                messageJson = objectMapper.writeValueAsString(message);
            } catch (Exception e) {
                logger.error("Error serializing message", e);
                return;
            }
            
            sessions.forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.getBasicRemote().sendText(messageJson);
                    }
                } catch (IOException e) {
                    logger.error("Error sending message to session: {}", session.getId(), e);
                    sessions.remove(session);
                }
            });
        }
    }
    
    private void broadcastToStreamExcept(String streamId, Map<String, Object> message, Session excludeSession) {
        CopyOnWriteArraySet<Session> sessions = streamSessions.get(streamId);
        if (sessions != null) {
            String messageJson;
            try {
                messageJson = objectMapper.writeValueAsString(message);
            } catch (Exception e) {
                logger.error("Error serializing message", e);
                return;
            }
            
            sessions.forEach(session -> {
                if (!session.equals(excludeSession)) {
                    try {
                        if (session.isOpen()) {
                            session.getBasicRemote().sendText(messageJson);
                        }
                    } catch (IOException e) {
                        logger.error("Error sending message to session: {}", session.getId(), e);
                        sessions.remove(session);
                    }
                }
            });
        }
    }
    
    // Static method to broadcast from external services
    public static void broadcastSystemMessage(String streamId, String message, String type) {
        Map<String, Object> systemMessage = Map.of(
            "type", type,
            "message", message,
            "timestamp", Instant.now().toString(),
            "isSystem", true
        );
        
        CopyOnWriteArraySet<Session> sessions = streamSessions.get(streamId);
        if (sessions != null) {
            try {
                String messageJson = objectMapper.writeValueAsString(systemMessage);
                sessions.forEach(session -> {
                    try {
                        if (session.isOpen()) {
                            session.getBasicRemote().sendText(messageJson);
                        }
                    } catch (IOException e) {
                        logger.error("Error sending system message", e);
                    }
                });
            } catch (Exception e) {
                logger.error("Error broadcasting system message", e);
            }
        }
    }
    
    // Get active viewer count for a stream
    public static int getActiveViewers(String streamId) {
        CopyOnWriteArraySet<Session> sessions = streamSessions.get(streamId);
        return sessions != null ? sessions.size() : 0;
    }
}