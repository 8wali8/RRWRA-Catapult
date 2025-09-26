#!/bin/bash

# StreamSense Metrics Verification Script
# Tests if all services properly expose Prometheus metrics

echo "🔍 StreamSense Metrics Endpoint Verification"
echo "============================================"

# Define service endpoints
declare -A SERVICES=(
    ["Prometheus"]="http://localhost:9090/metrics"
    ["API Gateway"]="http://localhost:8080/actuator/prometheus"
    ["Chat Service"]="http://localhost:8081/actuator/prometheus"
    ["GraphQL Service"]="http://localhost:8082/actuator/prometheus"
    ["ML Engine"]="http://localhost:5000/metrics"
    ["Video Service"]="http://localhost:5001/metrics"
    ["PostgreSQL Exporter"]="http://localhost:9187/metrics"
    ["Redis Exporter"]="http://localhost:9121/metrics"
    ["Node Exporter"]="http://localhost:9100/metrics"
)

# Function to test endpoint
test_endpoint() {
    local name=$1
    local url=$2
    
    printf "%-20s " "$name:"
    
    response=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null)
    
    if [ "$response" = "200" ]; then
        echo "✅ HEALTHY (200)"
        
        # Count metrics
        metric_count=$(curl -s --max-time 5 "$url" 2>/dev/null | grep -v '^#' | wc -l)
        echo "                     📊 Metrics available: $metric_count"
        
        # Check for key metrics
        if [[ "$url" == *"actuator/prometheus"* ]]; then
            check_spring_metrics "$url"
        elif [[ "$url" == *"ml-engine"* ]] || [[ "$url" == *"video-service"* ]]; then
            check_python_metrics "$url"
        fi
        
    elif [ "$response" = "000" ]; then
        echo "❌ UNREACHABLE (Connection failed)"
    else
        echo "⚠️  ISSUE (HTTP $response)"
    fi
}

# Check Spring Boot specific metrics
check_spring_metrics() {
    local url=$1
    local metrics=$(curl -s --max-time 5 "$url" 2>/dev/null)
    
    if echo "$metrics" | grep -q "jvm_memory_used_bytes"; then
        echo "                     ✅ JVM metrics present"
    fi
    
    if echo "$metrics" | grep -q "http_server_requests_seconds"; then
        echo "                     ✅ HTTP metrics present"
    fi
    
    if echo "$metrics" | grep -q "hystrix"; then
        echo "                     ✅ Hystrix metrics present"
    fi
}

# Check Python service specific metrics
check_python_metrics() {
    local url=$1
    local metrics=$(curl -s --max-time 5 "$url" 2>/dev/null)
    
    if echo "$metrics" | grep -q "process_cpu_usage"; then
        echo "                     ✅ Process metrics present"
    fi
    
    if echo "$metrics" | grep -q "python_gc_"; then
        echo "                     ✅ Python GC metrics present"
    fi
}

# Test all services
echo "Testing metrics endpoints..."
echo ""

for service in "${!SERVICES[@]}"; do
    test_endpoint "$service" "${SERVICES[$service]}"
    echo ""
done

# Test Prometheus targets API
echo "🎯 Checking Prometheus Service Discovery..."
echo "=========================================="

targets_response=$(curl -s "http://localhost:9090/api/v1/targets" 2>/dev/null)

if [ $? -eq 0 ]; then
    echo "✅ Prometheus API accessible"
    
    # Parse targets (basic check)
    active_targets=$(echo "$targets_response" | grep -o '"health":"up"' | wc -l)
    total_targets=$(echo "$targets_response" | grep -o '"discoveredLabels"' | wc -l)
    
    echo "📊 Active targets: $active_targets / $total_targets"
    
    if [ "$active_targets" -gt 5 ]; then
        echo "✅ Good target coverage"
    else
        echo "⚠️  Low target coverage - some services may be down"
    fi
else
    echo "❌ Cannot access Prometheus API"
fi

# Test key metrics queries
echo ""
echo "🔍 Testing Key Metrics Queries..."
echo "================================"

queries=(
    "up"
    "http_server_requests_seconds_count"
    "jvm_memory_used_bytes"
    "process_cpu_usage"
)

for query in "${queries[@]}"; do
    printf "%-30s " "$query:"
    
    result=$(curl -s "http://localhost:9090/api/v1/query?query=$query" 2>/dev/null)
    
    if echo "$result" | grep -q '"status":"success"'; then
        data_points=$(echo "$result" | grep -o '"value":\[' | wc -l)
        echo "✅ SUCCESS ($data_points series)"
    else
        echo "❌ FAILED"
    fi
done

echo ""
echo "🎯 Metrics Verification Complete!"
echo ""
echo "📋 Next Steps:"
echo "   1. Visit http://localhost:9090/targets to see all targets"
echo "   2. Visit http://localhost:9090/graph to query metrics"
echo "   3. Visit http://localhost:3001 for Grafana dashboards"
echo "   4. Check alert rules at http://localhost:9090/rules"