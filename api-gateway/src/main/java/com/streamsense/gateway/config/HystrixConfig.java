package com.streamsense.gateway.config;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HystrixConfig {

    @Bean
    public HystrixCommandProperties.Setter hystrixProperties() {
        return HystrixCommandProperties.Setter()
                .withCircuitBreakerEnabled(true)
                .withCircuitBreakerRequestVolumeThreshold(10)
                .withCircuitBreakerSleepWindowInMilliseconds(5000)
                .withCircuitBreakerErrorThresholdPercentage(50)
                .withExecutionTimeoutInMilliseconds(3000);
    }

    public static class FallbackCommand extends HystrixCommand<String> {
        
        public FallbackCommand() {
            super(HystrixCommandGroupKey.Factory.asKey("StreamSenseGroup"));
        }

        @Override
        protected String run() throws Exception {
            return "Service temporarily unavailable. Please try again later.";
        }

        @Override
        protected String getFallback() {
            return "Circuit breaker activated - Service degraded gracefully";
        }
    }
}