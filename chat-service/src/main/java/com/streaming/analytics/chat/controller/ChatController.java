package com.streaming.analytics.chat.controller;

import com.streaming.analytics.chat.model.ChatMessage;
import com.streaming.analytics.chat.model.ChatRoom;
import com.streaming.analytics.chat.service.ChatService;
import com.streaming.analytics.chat.service.RateLimitingService;
import com.streaming.analytics.chat.dto.ChatMessageDto;
import com.streaming.analytics.chat.dto.ChatRoomDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Reactive Chat Controller
 * 
 * Features:
 * - Real-time message streaming with Server-Sent Events
 * - Advanced rate limiting per user and room
 * - Circuit breaker for fault tolerance
 * - WebFlux reactive programming
 * - Comprehensive input validation
 * - Prometheus metrics integration
 * - Redis caching for performance
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ChatController {

    private final ChatService chatService;
    private final RateLimitingService rateLimitingService;
    
    // Real-time message sinks for each chat room
    private final ConcurrentHashMap<String, Sinks.Many<ChatMessage>> roomSinks = new ConcurrentHashMap<>();

    /**
     * Get all active chat rooms
     */
    @GetMapping("/rooms")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackGetRooms")
    public Flux<ChatRoomDto> getAllRooms() {
        log.info("Fetching all chat rooms");
        return chatService.getAllRooms()
                .map(this::toChatRoomDto)
                .doOnError(error -> log.error("Error fetching rooms", error));
    }

    /**
     * Create a new chat room
     */
    @PostMapping("/rooms")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackCreateRoom")
    @RateLimiter(name = "room-creation")
    public Mono<ResponseEntity<ChatRoomDto>> createRoom(@Valid @RequestBody ChatRoomDto roomDto) {
        log.info("Creating new chat room: {}", roomDto.getName());
        
        return chatService.createRoom(roomDto.getName(), roomDto.getDescription(), roomDto.getStreamerId())
                .map(this::toChatRoomDto)
                .map(dto -> ResponseEntity.ok(dto))
                .doOnSuccess(room -> log.info("Created room: {}", room))
                .doOnError(error -> log.error("Error creating room", error));
    }

    /**
     * Get chat room by ID
     */
    @GetMapping("/rooms/{roomId}")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackGetRoom")
    public Mono<ResponseEntity<ChatRoomDto>> getRoom(@PathVariable @NotBlank String roomId) {
        log.info("Fetching room: {}", roomId);
        
        return chatService.getRoomById(roomId)
                .map(this::toChatRoomDto)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching room: {}", roomId, error));
    }

    /**
     * Get chat history for a room with pagination
     */
    @GetMapping("/rooms/{roomId}/messages")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackGetMessages")
    public Flux<ChatMessageDto> getMessages(
            @PathVariable @NotBlank String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String before,
            @RequestParam(required = false) String after) {
        
        log.info("Fetching messages for room: {}, page: {}, size: {}", roomId, page, size);
        
        return chatService.getMessages(roomId, page, size, before, after)
                .map(this::toChatMessageDto)
                .doOnError(error -> log.error("Error fetching messages for room: {}", roomId, error));
    }

    /**
     * Send a new chat message
     */
    @PostMapping("/rooms/{roomId}/messages")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackSendMessage")
    public Mono<ResponseEntity<ChatMessageDto>> sendMessage(
            @PathVariable @NotBlank String roomId,
            @Valid @RequestBody ChatMessageDto messageDto) {
        
        log.info("Sending message to room: {} from user: {}", roomId, messageDto.getUserId());
        
        // Check rate limit
        return rateLimitingService.isAllowed(messageDto.getUserId(), roomId)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.just(ResponseEntity.status(429).build());
                    }
                    
                    return chatService.sendMessage(roomId, messageDto.getUserId(), 
                                    messageDto.getUsername(), messageDto.getContent(), messageDto.getMessageType())
                            .map(this::toChatMessageDto)
                            .doOnNext(dto -> {
                                // Broadcast to real-time subscribers
                                broadcastMessage(roomId, chatService.toEntity(dto));
                            })
                            .map(dto -> ResponseEntity.ok(dto))
                            .doOnSuccess(response -> log.info("Message sent successfully"))
                            .doOnError(error -> log.error("Error sending message", error));
                });
    }

    /**
     * Real-time message stream for a chat room using Server-Sent Events
     */
    @GetMapping(value = "/rooms/{roomId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackMessageStream")
    public Flux<ChatMessageDto> getMessageStream(@PathVariable @NotBlank String roomId) {
        log.info("Starting message stream for room: {}", roomId);
        
        // Create or get existing sink for the room
        Sinks.Many<ChatMessage> sink = roomSinks.computeIfAbsent(roomId, 
                k -> Sinks.many().multicast().onBackpressureBuffer());
        
        return sink.asFlux()
                .map(this::toChatMessageDto)
                .doOnSubscribe(subscription -> log.info("New subscriber for room: {}", roomId))
                .doOnCancel(() -> log.info("Subscriber cancelled for room: {}", roomId))
                .doOnError(error -> log.error("Error in message stream for room: {}", roomId, error));
    }

    /**
     * Delete a message (admin/moderator only)
     */
    @DeleteMapping("/messages/{messageId}")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackDeleteMessage")
    public Mono<ResponseEntity<Void>> deleteMessage(
            @PathVariable @NotBlank String messageId,
            @RequestParam @NotBlank String userId) {
        
        log.info("Deleting message: {} by user: {}", messageId, userId);
        
        return chatService.deleteMessage(messageId, userId)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error deleting message: {}", messageId, error));
    }

    /**
     * Update user activity status
     */
    @PostMapping("/rooms/{roomId}/activity")
    @RateLimiter(name = "activity-updates")
    public Mono<ResponseEntity<Void>> updateActivity(
            @PathVariable @NotBlank String roomId,
            @RequestParam @NotBlank String userId,
            @RequestParam @NotBlank String activity) {
        
        return chatService.updateUserActivity(roomId, userId, activity)
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .doOnError(error -> log.error("Error updating activity", error));
    }

    /**
     * Get active users in a room
     */
    @GetMapping("/rooms/{roomId}/users")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackGetActiveUsers")
    public Flux<String> getActiveUsers(@PathVariable @NotBlank String roomId) {
        return chatService.getActiveUsers(roomId)
                .doOnError(error -> log.error("Error fetching active users for room: {}", roomId, error));
    }

    /**
     * Get chat analytics for a room
     */
    @GetMapping("/rooms/{roomId}/analytics")
    @CircuitBreaker(name = "chat-service", fallbackMethod = "fallbackGetAnalytics")
    public Mono<ResponseEntity<Object>> getAnalytics(@PathVariable @NotBlank String roomId) {
        return chatService.getChatAnalytics(roomId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching analytics for room: {}", roomId, error));
    }

    // Circuit Breaker Fallback Methods
    
    public Flux<ChatRoomDto> fallbackGetRooms(Exception ex) {
        log.warn("Fallback: Unable to fetch rooms", ex);
        return Flux.empty();
    }
    
    public Mono<ResponseEntity<ChatRoomDto>> fallbackCreateRoom(ChatRoomDto roomDto, Exception ex) {
        log.warn("Fallback: Unable to create room", ex);
        return Mono.just(ResponseEntity.status(503).build());
    }
    
    public Mono<ResponseEntity<ChatRoomDto>> fallbackGetRoom(String roomId, Exception ex) {
        log.warn("Fallback: Unable to fetch room: {}", roomId, ex);
        return Mono.just(ResponseEntity.status(503).build());
    }
    
    public Flux<ChatMessageDto> fallbackGetMessages(String roomId, int page, int size, String before, String after, Exception ex) {
        log.warn("Fallback: Unable to fetch messages for room: {}", roomId, ex);
        return Flux.empty();
    }
    
    public Mono<ResponseEntity<ChatMessageDto>> fallbackSendMessage(String roomId, ChatMessageDto messageDto, Exception ex) {
        log.warn("Fallback: Unable to send message to room: {}", roomId, ex);
        return Mono.just(ResponseEntity.status(503).build());
    }
    
    public Flux<ChatMessageDto> fallbackMessageStream(String roomId, Exception ex) {
        log.warn("Fallback: Unable to create message stream for room: {}", roomId, ex);
        return Flux.empty();
    }
    
    public Mono<ResponseEntity<Void>> fallbackDeleteMessage(String messageId, String userId, Exception ex) {
        log.warn("Fallback: Unable to delete message: {}", messageId, ex);
        return Mono.just(ResponseEntity.status(503).build());
    }
    
    public Flux<String> fallbackGetActiveUsers(String roomId, Exception ex) {
        log.warn("Fallback: Unable to fetch active users for room: {}", roomId, ex);
        return Flux.empty();
    }
    
    public Mono<ResponseEntity<Object>> fallbackGetAnalytics(String roomId, Exception ex) {
        log.warn("Fallback: Unable to fetch analytics for room: {}", roomId, ex);
        return Mono.just(ResponseEntity.status(503).build());
    }

    // Private Helper Methods
    
    private void broadcastMessage(String roomId, ChatMessage message) {
        Sinks.Many<ChatMessage> sink = roomSinks.get(roomId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
    }
    
    private ChatMessageDto toChatMessageDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .userId(message.getUserId())
                .username(message.getUsername())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .timestamp(message.getTimestamp())
                .edited(message.isEdited())
                .editedAt(message.getEditedAt())
                .build();
    }
    
    private ChatRoomDto toChatRoomDto(ChatRoom room) {
        return ChatRoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .streamerId(room.getStreamerId())
                .isActive(room.isActive())
                .createdAt(room.getCreatedAt())
                .messageCount(room.getMessageCount())
                .activeUserCount(room.getActiveUserCount())
                .build();
    }
}