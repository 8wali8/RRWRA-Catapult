package com.streamsense.gateway.controller;

import graphql.ExecutionResult;
import graphql.GraphQL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * GraphQL Federation Gateway Controller
 * 
 * Exposes federated GraphQL endpoint that combines:
 * - Chat Service GraphQL API
 * - Video Service GraphQL API  
 * - Sentiment Service GraphQL API
 * - Recommendation Service GraphQL API
 * - Core GraphQL Service API
 */
@RestController
@RequestMapping("/graphql")
@RequiredArgsConstructor
@Slf4j
public class GraphQLFederationController {

    private final GraphQL graphQL;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> graphql(@RequestBody Map<String, Object> body) {
        return Mono.fromCompletionStage(() -> {
            String query = (String) body.get("query");
            Map<String, Object> variables = (Map<String, Object>) body.get("variables");
            String operationName = (String) body.get("operationName");

            log.info("Executing federated GraphQL query: {}", operationName != null ? operationName : "anonymous");

            ExecutionResult executionResult = graphQL.execute(builder -> builder
                    .query(query)
                    .variables(variables != null ? variables : Map.of())
                    .operationName(operationName)
            );

            return Map.of(
                    "data", executionResult.getData(),
                    "errors", executionResult.getErrors()
            );
        });
    }

    @GetMapping("/schema")
    public Mono<String> schema() {
        return Mono.just(graphQL.getGraphQLSchema().toString());
    }

    @GetMapping("/playground")
    public Mono<String> playground() {
        return Mono.just("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset=utf-8/>
                <meta name="viewport" content="user-scalable=no, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, minimal-ui">
                <title>GraphQL Federation Playground</title>
                <link rel="stylesheet" href="//cdn.jsdelivr.net/npm/graphql-playground-react/build/static/css/index.css" />
                <link rel="shortcut icon" href="//cdn.jsdelivr.net/npm/graphql-playground-react/build/favicon.png" />
                <script src="//cdn.jsdelivr.net/npm/graphql-playground-react/build/static/js/middleware.js"></script>
            </head>
            <body>
                <div id="root">
                    <style>
                        body { background-color: rgb(23, 42, 58); font-family: Open Sans, sans-serif; height: 90vh; }
                        #root { height: 100%; width: 100%; display: flex; align-items: center; justify-content: center; }
                        .loading { font-size: 32px; font-weight: 200; color: rgba(255, 255, 255, .6); margin-left: 20px; }
                        img { width: 78px; height: 78px; }
                        .title { font-weight: 400; }
                    </style>
                    <img src="//cdn.jsdelivr.net/npm/graphql-playground-react/build/logo.png" alt="">
                    <div class="loading"> Loading
                        <span class="title">GraphQL Federation Playground</span>
                    </div>
                </div>
                <script>
                    window.addEventListener('load', function (event) {
                        GraphQLPlayground.init(document.getElementById('root'), {
                            endpoint: '/graphql',
                            settings: {
                                'general.betaUpdates': false,
                                'editor.theme': 'dark',
                                'editor.reuseHeaders': true,
                                'tracing.hideTracingResponse': true,
                                'editor.fontSize': 14,
                                "editor.fontFamily": "'Source Code Pro', 'Consolas', 'Inconsolata', 'Droid Sans Mono', 'Monaco', monospace",
                                'request.credentials': 'omit',
                            },
                            tabs: [
                                {
                                    endpoint: '/graphql',
                                    query: `# StreamSense GraphQL Federation Gateway
            # 
            # This federated endpoint combines data from:
            # - Chat Service (real-time messaging)
            # - Video Service (sponsor detection)  
            # - Sentiment Service (emotion analysis)
            # - Recommendation Service (personalization)
            #
            # Example federated query:
            
            query GetStreamerProfile($streamerId: String!) {
              getStreamerProfile(streamerId: $streamerId) {
                streamerId
                chatStats {
                  totalMessages
                  averageSentiment
                  topEmotes
                }
                videoStats {
                  totalFrames
                  sponsorDetections
                  averageConfidence
                }
                sentimentStats {
                  averageScore
                  emotionBreakdown {
                    emotion
                    percentage
                  }
                  trendDirection
                }
                recommendations {
                  streamerId
                  name
                  category
                  confidence
                  reason
                }
              }
            }`,
                                    variables: JSON.stringify({ streamerId: "example_streamer" }, null, 2),
                                },
                            ],
                        })
                    })
                </script>
            </body>
            </html>
        """);
    }
}