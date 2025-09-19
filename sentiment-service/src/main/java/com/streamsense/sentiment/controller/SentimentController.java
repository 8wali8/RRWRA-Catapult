package com.streamsense.sentiment.controller;

import com.streamsense.sentiment.model.SentimentRequest;
import com.streamsense.sentiment.model.SentimentResponse;
import com.streamsense.sentiment.service.SentimentAnalysisService;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import org.springframework.http.ResponseEntity;

/**
 * Sentiment Analysis Controller
 * Production-ready implementation with Netflix OSS patterns
 */
@RestController
@RequestMapping("/api/sentiment")
@RequiredArgsConstructor
@Slf4j
public class SentimentController {

    private final SentimentAnalysisService sentimentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @PostMapping("/analyze")
    @HystrixCommand(fallbackMethod = "fallbackSentimentAnalysis")
    public Mono<ResponseEntity<SentimentResponse>> analyzeSentiment(@RequestBody SentimentRequest request) {
        return sentimentService.analyzeSentiment(request.getText())
                .map(result -> ResponseEntity.ok(result))
                .onErrorReturn(ResponseEntity.badRequest().build());
    }

    @KafkaListener(topics = "stream.chat.messages")
    public void processStreamChatMessage(String chatMessage) {
        log.info("Processing chat message for sentiment: {}", chatMessage);
        
        sentimentService.analyzeSentiment(chatMessage)
                .doOnSuccess(result -> {
                    kafkaTemplate.send("stream.sentiment.events", result);
                    log.info("Sentiment analysis complete: {}", result);
                })
                .doOnError(error -> log.error("Sentiment analysis failed", error))
                .subscribe();
    }

    public Mono<ResponseEntity<SentimentResponse>> fallbackSentimentAnalysis(SentimentRequest request) {
        SentimentResponse fallback = SentimentResponse.builder()
                .text(request.getText())
                .sentiment("NEUTRAL")
                .confidence(0.0)
                .emotion("UNKNOWN")
                .status("FALLBACK")
                .build();
        return Mono.just(ResponseEntity.ok(fallback));
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Sentiment Service is healthy");
    }
}