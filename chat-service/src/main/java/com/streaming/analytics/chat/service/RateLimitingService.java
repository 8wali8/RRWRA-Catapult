package com.streaming.analytics.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting Service for Chat Messages
 * Implements sophisticated rate limiting with Redis backing
 */
@Service
@Slf4j
public class RateLimitingService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    // In-memory cache for rate limit tracking
    private final Map<String, Map<String, Integer>> userRateLimits = new ConcurrentHashMap<>();
    private final Map<String, Map<String, LocalDateTime>> lastMessageTime = new ConcurrentHashMap<>();
    
    // Rate limiting configuration
    private static final int MAX_MESSAGES_PER_MINUTE = 30;
    private static final int MAX_MESSAGES_PER_HOUR = 500;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final Duration COOLDOWN_PERIOD = Duration.ofSeconds(2);

    public RateLimitingService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if user is allowed to send a message
     */
    public Mono<Boolean> isAllowed(String userId, String roomId) {
        return checkRateLimit(userId, roomId)
                .flatMap(allowed -> {
                    if (allowed) {
                        return recordMessageAttempt(userId, roomId);
                    }
                    return Mono.just(false);
                });
    }

    /**
     * Check rate limit for user in room
     */
    private Mono<Boolean> checkRateLimit(String userId, String roomId) {
        return Mono.fromCallable(() -> {
            String key = userId + ":" + roomId;
            LocalDateTime now = LocalDateTime.now();
            
            // Check cooldown period
            LocalDateTime lastMessage = lastMessageTime
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .get(roomId);
            
            if (lastMessage != null && 
                Duration.between(lastMessage, now).compareTo(COOLDOWN_PERIOD) < 0) {
                log.debug("User {} in cooldown period for room {}", userId, roomId);
                return false;
            }
            
            // Check per-minute limit
            int messagesThisMinute = getMessageCount(userId, roomId, Duration.ofMinutes(1));
            if (messagesThisMinute >= MAX_MESSAGES_PER_MINUTE) {
                log.warn("User {} exceeded per-minute rate limit in room {}", userId, roomId);
                return false;
            }
            
            // Check per-hour limit
            int messagesThisHour = getMessageCount(userId, roomId, Duration.ofHours(1));
            if (messagesThisHour >= MAX_MESSAGES_PER_HOUR) {
                log.warn("User {} exceeded per-hour rate limit in room {}", userId, roomId);
                return false;
            }
            
            return true;
        });
    }

    /**
     * Record message attempt and update counters
     */
    private Mono<Boolean> recordMessageAttempt(String userId, String roomId) {
        return Mono.fromCallable(() -> {
            LocalDateTime now = LocalDateTime.now();
            
            // Update last message time
            lastMessageTime
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(roomId, now);
            
            // Increment counters
            Map<String, Integer> userLimits = userRateLimits
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
            
            String minuteKey = roomId + ":minute:" + (now.getMinute());
            String hourKey = roomId + ":hour:" + now.getHour();
            
            userLimits.merge(minuteKey, 1, Integer::sum);
            userLimits.merge(hourKey, 1, Integer::sum);
            
            // Store in Redis for distributed rate limiting
            String redisKey = "rate_limit:" + userId + ":" + roomId;
            redisTemplate.opsForValue()
                    .increment(redisKey)
                    .doOnNext(count -> {
                        if (count == 1) {
                            // Set expiration on first increment
                            redisTemplate.expire(redisKey, RATE_LIMIT_WINDOW).subscribe();
                        }
                    })
                    .subscribe();
            
            log.debug("Recorded message attempt for user {} in room {}", userId, roomId);
            return true;
        });
    }

    /**
     * Get message count for user in room within time window
     */
    private int getMessageCount(String userId, String roomId, Duration window) {
        LocalDateTime cutoff = LocalDateTime.now().minus(window);
        Map<String, Integer> userLimits = userRateLimits.get(userId);
        
        if (userLimits == null) {
            return 0;
        }
        
        return userLimits.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(roomId + ":"))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /**
     * Reset rate limits for a user (admin function)
     */
    public Mono<Void> resetRateLimit(String userId, String roomId) {
        return Mono.fromRunnable(() -> {
            userRateLimits.remove(userId);
            lastMessageTime.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).remove(roomId);
            
            String redisKey = "rate_limit:" + userId + ":" + roomId;
            redisTemplate.delete(redisKey).subscribe();
            
            log.info("Reset rate limits for user {} in room {}", userId, roomId);
        });
    }

    /**
     * Get rate limit status for a user
     */
    public Mono<Map<String, Object>> getRateLimitStatus(String userId, String roomId) {
        return Mono.fromCallable(() -> {
            int minuteCount = getMessageCount(userId, roomId, Duration.ofMinutes(1));
            int hourCount = getMessageCount(userId, roomId, Duration.ofHours(1));
            
            LocalDateTime lastMessage = lastMessageTime
                    .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .get(roomId);
            
            boolean inCooldown = lastMessage != null && 
                Duration.between(lastMessage, LocalDateTime.now()).compareTo(COOLDOWN_PERIOD) < 0;
            
            return Map.of(
                "userId", userId,
                "roomId", roomId,
                "messagesThisMinute", minuteCount,
                "messagesThisHour", hourCount,
                "maxPerMinute", MAX_MESSAGES_PER_MINUTE,
                "maxPerHour", MAX_MESSAGES_PER_HOUR,
                "inCooldown", inCooldown,
                "canSendMessage", minuteCount < MAX_MESSAGES_PER_MINUTE && 
                                hourCount < MAX_MESSAGES_PER_HOUR && !inCooldown
            );
        });
    }
}