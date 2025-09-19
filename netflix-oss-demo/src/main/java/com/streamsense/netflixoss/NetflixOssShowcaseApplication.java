package com.streamsense.netflixoss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Netflix OSS Technology Showcase Application
 * 
 * This application demonstrates the complete Netflix OSS stack:
 * 
 * SERVICE DISCOVERY & LOAD BALANCING:
 * - Eureka: Service registration and discovery
 * - Ribbon: Client-side load balancing
 * 
 * API GATEWAY & ROUTING:
 * - Zuul: Edge service, routing, filtering, security
 * 
 * RESILIENCE & FAULT TOLERANCE:
 * - Hystrix: Circuit breakers, fallbacks, isolation
 * 
 * INTER-SERVICE COMMUNICATION:
 * - Feign: Declarative REST clients with load balancing
 * 
 * CONFIGURATION MANAGEMENT:
 * - Archaius: Dynamic configuration properties
 * - Config Server: Externalized configuration
 * 
 * DATA PERSISTENCE:
 * - Cassandra: Distributed NoSQL database (Netflix's primary datastore)
 * - Redis: In-memory caching and session storage
 * 
 * OBSERVABILITY:
 * - Zipkin: Distributed tracing across microservices
 * - Prometheus: Metrics collection and monitoring
 * 
 * WORKFLOW ORCHESTRATION:
 * - Conductor: Workflow and task orchestration
 * 
 * This represents the complete technology stack that powers Netflix's
 * global streaming platform serving 200+ million subscribers worldwide.
 */
@SpringBootApplication
@EnableEurekaClient
@EnableZuulProxy
@EnableHystrix
@EnableFeignClients
public class NetflixOssShowcaseApplication {

    public static void main(String[] args) {
        System.out.println("🎬 Starting Netflix OSS Technology Showcase...");
        System.out.println("🚀 Demonstrating production-grade distributed systems");
        System.out.println("🌐 Used by Netflix to serve 200+ million global subscribers");
        
        SpringApplication.run(NetflixOssShowcaseApplication.class, args);
        
        System.out.println("✅ Netflix OSS Stack Successfully Started!");
        System.out.println("📊 Access monitoring at: http://localhost:8080/actuator");
        System.out.println("🔍 Distributed tracing: http://localhost:9411");
        System.out.println("📈 Metrics: http://localhost:9090");
    }
}