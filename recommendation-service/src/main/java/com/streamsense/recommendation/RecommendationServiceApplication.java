package com.streamsense.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Recommendation Engine Microservice
 * 
 * Personalization engine that analyzes user behavior patterns and streaming preferences
 * to provide intelligent content recommendations and sponsor targeting.
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
public class RecommendationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}