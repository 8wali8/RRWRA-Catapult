# StreamSense Monitoring Setup - Enterprise Grade

## 🎯 **What We've Enhanced**

Your Prometheus configuration has been upgraded from basic service monitoring to enterprise-grade observability with the following improvements:

### ✅ **Enhanced Prometheus Configuration**
- **Service Discovery**: Added Eureka integration for dynamic service discovery
- **Better Scraping**: Optimized intervals (5s for ML services, 10s for APIs, 15s for infrastructure)
- **Comprehensive Coverage**: All microservices + infrastructure + exporters
- **Alerting Rules**: Complete alerting suite for all components

### 🚀 **New Exporters Added**
- **PostgreSQL Exporter**: Database performance, connections, query metrics
- **Redis Exporter**: Cache hit rates, memory usage, connection stats
- **Node Exporter**: System-level metrics (CPU, memory, disk, network)
- **Kafka JMX**: JVM metrics for Kafka brokers

### 📊 **Monitoring Coverage**

| Component | Metrics Source | Health Check | Key Metrics |
|-----------|---------------|--------------|-------------|
| **Spring Boot Services** | `/actuator/prometheus` | ✅ | JVM, HTTP requests, Hystrix |
| **ML Engine** | `/metrics` | ✅ | Python process, inference time |
| **PostgreSQL** | `postgres-exporter:9187` | ✅ | Connections, queries, locks |
| **Redis** | `redis-exporter:9121` | ✅ | Memory, operations, hits/misses |
| **System Resources** | `node-exporter:9100` | ✅ | CPU, memory, disk, network |
| **Kafka** | JMX metrics | ✅ | Topics, partitions, consumer lag |

## 🔧 **How to Use**

### 1. **Start Enhanced Monitoring**
```bash
# Start the complete stack with exporters
docker-compose up -d

# Verify all metrics endpoints
./scripts/verify_metrics.sh
```

### 2. **Access Monitoring Dashboards**
- **Prometheus UI**: http://localhost:9090
  - Targets: http://localhost:9090/targets
  - Alerts: http://localhost:9090/alerts
  - Query: http://localhost:9090/graph

- **Grafana**: http://localhost:3001 (admin/grafana)
  - Import dashboard from `grafana-dashboard.json`

### 3. **Key Queries to Try**
```promql
# Service health
up

# Request rate across all services
rate(http_server_requests_seconds_count[5m])

# 95th percentile response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# JVM memory usage
jvm_memory_used_bytes / jvm_memory_max_bytes * 100

# Circuit breaker status
hystrix_circuit_breaker_open

# Database connections
pg_stat_database_numbackends

# ML processing performance
histogram_quantile(0.95, rate(ml_inference_duration_seconds_bucket[5m]))
```

## 🚨 **Alerting Rules**

The system now includes comprehensive alerting for:

### **Critical Alerts**
- Service down (any microservice unavailable > 1min)
- Eureka server down (service discovery failure)
- Database unavailable (PostgreSQL/Redis down)
- High memory usage (JVM > 90%)
- ML model loading failures

### **Warning Alerts**
- High error rate (>5% HTTP 5xx responses)
- High response time (P95 > 200ms)
- High CPU usage (>80%)
- Circuit breaker open
- High database connections (>80% capacity)
- Kafka consumer lag (>1000 messages)

## 📈 **Performance Validation**

The enhanced monitoring now tracks your enterprise requirements:

### **10K+ Events/Second**
```promql
# Track request throughput
sum(rate(http_server_requests_seconds_count[1m]))

# Kafka message rate
rate(kafka_messages_consumed_total[1m])
```

### **<200ms P95 Latency**
```promql
# API Gateway response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job="api-gateway"}[5m]))

# ML processing time
histogram_quantile(0.95, rate(ml_inference_duration_seconds_bucket[5m]))
```

### **99.99% Uptime**
```promql
# Service availability
avg_over_time(up[24h]) * 100
```

## 🛠️ **Troubleshooting**

### **If Targets Show as Down**
1. Check service health: `docker-compose ps`
2. Verify network connectivity: `docker network inspect rrwra-catapult_streaming-network`
3. Check endpoints manually: `curl http://localhost:8080/actuator/prometheus`

### **If Exporters Fail**
1. **PostgreSQL Exporter**: Verify connection string in docker-compose.yml
2. **Redis Exporter**: Check Redis container is running
3. **Node Exporter**: May need host system access for some metrics

### **If Metrics Missing**
1. Check Spring Boot actuator configuration in `application.yml`
2. Verify Python services have prometheus_client installed
3. Ensure `/metrics` endpoints are accessible

## 🎉 **Enterprise Benefits**

This monitoring setup provides:

- **Proactive Issue Detection**: Alerts before users notice problems
- **Performance Optimization**: Detailed metrics for bottleneck identification  
- **Capacity Planning**: Historical data for scaling decisions
- **Compliance**: Audit trail and uptime reporting
- **DevOps Efficiency**: Centralized observability across entire stack

Your StreamSense platform now has **production-ready monitoring** that scales with your enterprise Netflix OSS architecture!