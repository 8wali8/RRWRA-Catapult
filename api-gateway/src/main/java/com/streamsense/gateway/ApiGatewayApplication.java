package com.streamsense.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;

@SpringBootApplication
@EnableDiscoveryClient
@EnableZuulProxy
@EnableHystrix
@EnableHystrixDashboard
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
        System.out.println("🌐 StreamSense API Gateway (Enterprise Zuul) Started!");
        System.out.println("🔧 Hystrix Dashboard: http://localhost:8080/hystrix");
        System.out.println("📊 Actuator: http://localhost:8080/actuator");
        System.out.println("📈 Metrics: http://localhost:8080/actuator/prometheus");
    }
}