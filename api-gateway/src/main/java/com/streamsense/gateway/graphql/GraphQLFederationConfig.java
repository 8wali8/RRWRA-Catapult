package com.streamsense.gateway.graphql;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * GraphQL Federation Gateway Configuration
 * 
 * Federates multiple GraphQL services:
 * - Chat Service (chat-service:8081/graphql)
 * - Video Service (video-service:8082/graphql) 
 * - Sentiment Service (sentiment-service:8083/graphql)
 * - Recommendation Service (recommendation-service:8084/graphql)
 * - GraphQL Service (graphql-service:8086/graphql)
 */
@Configuration
@Slf4j
public class GraphQLFederationConfig {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Bean
    public GraphQL graphQL() throws IOException {
        // Load federated schema
        String schemaString = loadSchema();
        
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaString);
        RuntimeWiring runtimeWiring = buildRuntimeWiring();
        
        GraphQLSchema federatedSchema = Federation.transform(typeRegistry, runtimeWiring)
                .fetchEntities(entityDataFetcher())
                .resolveEntityType(env -> {
                    final Object src = env.getObject();
                    if (src instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) src;
                        String typename = (String) map.get("__typename");
                        return env.getSchema().getObjectType(typename);
                    }
                    return null;
                })
                .build();

        return GraphQL.newGraphQL(federatedSchema).build();
    }

    private String loadSchema() throws IOException {
        return """
            type Query {
                # Chat Service Queries
                getChatRooms: [ChatRoom]
                getChatMessages(roomId: String!): [ChatMessage]
                
                # Video Service Queries  
                getStreamAnalytics(streamerId: String!): StreamAnalytics
                getSponsorDetections(streamerId: String!): [SponsorDetection]
                
                # Sentiment Service Queries
                getSentimentAnalysis(text: String!): SentimentResponse
                getSentimentTrends(streamerId: String!): [SentimentTrend]
                
                # Recommendation Service Queries
                getRecommendations(userId: String!): [Recommendation]
                getTrendingStreams: [Stream]
                
                # Federated Queries
                getStreamerProfile(streamerId: String!): StreamerProfile
            }
            
            type Mutation {
                # Chat Service Mutations
                sendMessage(input: ChatMessageInput!): ChatMessage
                createChatRoom(input: ChatRoomInput!): ChatRoom
                
                # Video Service Mutations
                uploadFrame(input: FrameInput!): UploadResponse
                
                # Sentiment Service Mutations
                analyzeSentiment(input: SentimentInput!): SentimentResponse
                
                # Recommendation Service Mutations
                updatePreferences(input: PreferencesInput!): PreferencesResponse
            }
            
            type Subscription {
                # Real-time Chat Messages
                onChatMessage(roomId: String!): ChatMessage
                
                # Real-time Sponsor Detections
                onSponsorDetection(streamerId: String!): SponsorDetection
                
                # Real-time Sentiment Updates
                onSentimentUpdate(streamerId: String!): SentimentUpdate
                
                # Real-time Analytics
                onStreamAnalytics(streamerId: String!): StreamAnalytics
            }
            
            # Federated Types
            type StreamerProfile @key(fields: "streamerId") {
                streamerId: String!
                chatStats: ChatStats
                videoStats: VideoStats
                sentimentStats: SentimentStats
                recommendations: [Recommendation]
            }
            
            type ChatRoom @key(fields: "id") {
                id: String!
                name: String!
                description: String
                streamerId: String!
                isActive: Boolean!
                messageCount: Int!
                activeUserCount: Int!
                createdAt: String!
            }
            
            type ChatMessage @key(fields: "id") {
                id: String!
                roomId: String!
                userId: String!
                username: String!
                content: String!
                timestamp: String!
                sentiment: Float
                mentions: [String]
                emotes: [String]
            }
            
            type SponsorDetection @key(fields: "id") {
                id: String!
                streamerId: String!
                timestamp: String!
                sponsor: String!
                confidence: Float!
                boundingBox: BoundingBox
                frameUrl: String
            }
            
            type SentimentResponse {
                sentiment: String!
                confidence: Float!
                emotions: [Emotion]
                keywords: [String]
            }
            
            type StreamAnalytics {
                streamerId: String!
                viewerCount: Int!
                chatActivity: Float!
                sentimentScore: Float!
                sponsorDetections: Int!
                timestamp: String!
            }
            
            # Input Types
            input ChatMessageInput {
                roomId: String!
                userId: String!
                username: String!
                content: String!
            }
            
            input ChatRoomInput {
                name: String!
                description: String
                streamerId: String!
            }
            
            input FrameInput {
                streamerId: String!
                frameData: String!
                timestamp: String!
            }
            
            input SentimentInput {
                text: String!
                context: String
            }
            
            input PreferencesInput {
                userId: String!
                categories: [String]
                streamers: [String]
            }
            
            # Supporting Types
            type BoundingBox {
                x: Float!
                y: Float!
                width: Float!
                height: Float!
            }
            
            type Emotion {
                name: String!
                confidence: Float!
            }
            
            type ChatStats {
                totalMessages: Int!
                averageSentiment: Float!
                topEmotes: [String]
            }
            
            type VideoStats {
                totalFrames: Int!
                sponsorDetections: Int!
                averageConfidence: Float!
            }
            
            type SentimentStats {
                averageScore: Float!
                emotionBreakdown: [EmotionStat]
                trendDirection: String!
            }
            
            type EmotionStat {
                emotion: String!
                percentage: Float!
            }
            
            type Recommendation {
                streamerId: String!
                name: String!
                category: String!
                confidence: Float!
                reason: String!
            }
            
            type Stream {
                streamerId: String!
                title: String!
                category: String!
                viewerCount: Int!
                isLive: Boolean!
            }
            
            type SentimentTrend {
                timestamp: String!
                score: Float!
                volume: Int!
            }
            
            type SentimentUpdate {
                streamerId: String!
                score: Float!
                trend: String!
                timestamp: String!
            }
            
            type UploadResponse {
                success: Boolean!
                frameId: String
                message: String!
            }
            
            type PreferencesResponse {
                success: Boolean!
                message: String!
            }
        """;
    }

    private RuntimeWiring buildRuntimeWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("getChatRooms", fetchFromService("chat-service", 8081, "/graphql"))
                        .dataFetcher("getChatMessages", fetchFromService("chat-service", 8081, "/graphql"))
                        .dataFetcher("getStreamAnalytics", fetchFromService("video-service", 8082, "/graphql"))
                        .dataFetcher("getSponsorDetections", fetchFromService("video-service", 8082, "/graphql"))
                        .dataFetcher("getSentimentAnalysis", fetchFromService("sentiment-service", 8083, "/graphql"))
                        .dataFetcher("getSentimentTrends", fetchFromService("sentiment-service", 8083, "/graphql"))
                        .dataFetcher("getRecommendations", fetchFromService("recommendation-service", 8084, "/graphql"))
                        .dataFetcher("getTrendingStreams", fetchFromService("recommendation-service", 8084, "/graphql"))
                        .dataFetcher("getStreamerProfile", federatedProfileFetcher())
                )
                .type("Mutation", builder -> builder
                        .dataFetcher("sendMessage", fetchFromService("chat-service", 8081, "/graphql"))
                        .dataFetcher("createChatRoom", fetchFromService("chat-service", 8081, "/graphql"))
                        .dataFetcher("uploadFrame", fetchFromService("video-service", 8082, "/graphql"))
                        .dataFetcher("analyzeSentiment", fetchFromService("sentiment-service", 8083, "/graphql"))
                        .dataFetcher("updatePreferences", fetchFromService("recommendation-service", 8084, "/graphql"))
                )
                .build();
    }

    private DataFetcher<?> fetchFromService(String serviceName, int port, String path) {
        return environment -> {
            WebClient webClient = webClientBuilder.build();
            
            Map<String, Object> query = Map.of(
                "query", environment.getExecutionStepInfo().getPath().toString(),
                "variables", environment.getArguments()
            );

            return webClient.post()
                    .uri("http://" + serviceName + ":" + port + path)
                    .bodyValue(query)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(response -> response.get("data"))
                    .toFuture();
        };
    }

    private DataFetcher<?> entityDataFetcher() {
        return environment -> {
            List<Map<String, Object>> representations = environment.getArgument(_Entity.argumentName);
            return representations.stream()
                    .map(this::fetchEntityByRepresentation)
                    .map(CompletableFuture::completedFuture)
                    .toArray(CompletableFuture[]::new);
        };
    }

    private DataFetcher<?> federatedProfileFetcher() {
        return environment -> {
            String streamerId = environment.getArgument("streamerId");
            WebClient webClient = webClientBuilder.build();
            
            // Fetch data from multiple services and federate
            CompletableFuture<Map<String, Object>> chatStats = fetchChatStats(webClient, streamerId);
            CompletableFuture<Map<String, Object>> videoStats = fetchVideoStats(webClient, streamerId);
            CompletableFuture<Map<String, Object>> sentimentStats = fetchSentimentStats(webClient, streamerId);
            CompletableFuture<List<Map<String, Object>>> recommendations = fetchRecommendations(webClient, streamerId);
            
            return CompletableFuture.allOf(chatStats, videoStats, sentimentStats, recommendations)
                    .thenApply(v -> Map.of(
                            "streamerId", streamerId,
                            "chatStats", chatStats.join(),
                            "videoStats", videoStats.join(),
                            "sentimentStats", sentimentStats.join(),
                            "recommendations", recommendations.join()
                    ));
        };
    }

    private CompletableFuture<Map<String, Object>> fetchChatStats(WebClient webClient, String streamerId) {
        return webClient.get()
                .uri("http://chat-service:8081/api/chat/stats/" + streamerId)
                .retrieve()
                .bodyToMono(Map.class)
                .toFuture();
    }

    private CompletableFuture<Map<String, Object>> fetchVideoStats(WebClient webClient, String streamerId) {
        return webClient.get()
                .uri("http://video-service:8082/api/video/stats/" + streamerId)
                .retrieve()
                .bodyToMono(Map.class)
                .toFuture();
    }

    private CompletableFuture<Map<String, Object>> fetchSentimentStats(WebClient webClient, String streamerId) {
        return webClient.get()
                .uri("http://sentiment-service:8083/api/sentiment/stats/" + streamerId)
                .retrieve()
                .bodyToMono(Map.class)
                .toFuture();
    }

    private CompletableFuture<List<Map<String, Object>>> fetchRecommendations(WebClient webClient, String streamerId) {
        return webClient.get()
                .uri("http://recommendation-service:8084/api/recommendations/streamer/" + streamerId)
                .retrieve()
                .bodyToMono(List.class)
                .toFuture();
    }

    private Object fetchEntityByRepresentation(Map<String, Object> representation) {
        String typename = (String) representation.get("__typename");
        String id = (String) representation.get("id");
        
        // Route to appropriate service based on typename
        switch (typename) {
            case "ChatRoom":
            case "ChatMessage":
                return fetchFromChatService(id);
            case "SponsorDetection":
                return fetchFromVideoService(id);
            default:
                return representation;
        }
    }

    private Object fetchFromChatService(String id) {
        // Implementation for fetching from chat service
        return Map.of("id", id, "__typename", "ChatRoom");
    }

    private Object fetchFromVideoService(String id) {
        // Implementation for fetching from video service
        return Map.of("id", id, "__typename", "SponsorDetection");
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}