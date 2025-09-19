package com.streamsense.sentiment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Sentiment Analysis Microservice
 * 
 * Dedicated service for real-time sentiment analysis of streaming chat data.
 * Uses Netflix OSS stack for resilience and observability.
 * 
 * Technologies (per README spec):
 * - Spring Boot + Spring Cloud
 * - Netflix Eureka (Service Discovery)
 * - Netflix Hystrix (Circuit Breakers)
 * - Apache Kafka (Event Streaming)
 * - PostgreSQL + Redis + Cassandra (Databases)
 * - Zipkin (Distributed Tracing)
 * - Micrometer + Prometheus (Monitoring)
 */
@SpringBootApplication
@EnableEurekaClient
@EnableHystrix
@EnableKafka
public class SentimentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(SentimentServiceApplication.class, args);
    }
}