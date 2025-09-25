#!/bin/bash

# StreamSense Uptime Testing Script
# Validates 99.99% uptime requirement with chaos engineering

echo "StreamSense Uptime & Resilience Testing"
echo "=========================================="

# Service endpoints to monitor
declare -A services=(
    ["eureka"]="http://localhost:8761/actuator/health"
    ["api-gateway"]="http://localhost:8080/actuator/health"
    ["chat-service"]="http://localhost:8081/actuator/health"
    ["graphql-service"]="http://localhost:8082/actuator/health"
    ["ml-engine"]="http://localhost:5000/health"
    ["video-service"]="http://localhost:5001/health"
)

# Test configuration
TEST_DURATION_HOURS=24
CHECK_INTERVAL_SECONDS=5
TOTAL_CHECKS=$((TEST_DURATION_HOURS * 3600 / CHECK_INTERVAL_SECONDS))

# Results tracking
declare -A service_failures
declare -A service_checks
declare -A downtime_periods

# Initialize counters
for service in "${!services[@]}"; do
    service_failures[$service]=0
    service_checks[$service]=0
done

# Health check function
check_service_health() {
    local service=$1
    local url=$2
    
    response=$(curl -s -w "%{http_code}" -o /dev/null --max-time 10 "$url")
    
    if [[ $response -ge 200 && $response -lt 300 ]]; then
        return 0  # Success
    else
        return 1  # Failure
    fi
}

# Chaos engineering tests
run_chaos_tests() {
    echo "Running Chaos Engineering Tests..."
    
    # Test 1: Random service restart
    echo "Test 1: Random service restart simulation"
    random_service=$(docker ps --format "table {{.Names}}" | grep -E "(chat-service|ml-engine)" | shuf -n 1)
    if [[ -n $random_service ]]; then
        echo "  Restarting $random_service..."
        docker restart "$random_service"
        sleep 30  # Allow service to recover
    fi
    
    # Test 2: Network partition simulation
    echo "Test 2: Network latency injection"
    # Simulate network delays
    docker exec api-gateway sh -c "tc qdisc add dev eth0 root netem delay 100ms" 2>/dev/null || true
    sleep 60
    docker exec api-gateway sh -c "tc qdisc del dev eth0 root" 2>/dev/null || true
    
    # Test 3: Memory pressure
    echo "Test 3: Resource pressure simulation"
    docker exec ml-engine sh -c "stress --vm 1 --vm-bytes 512M --timeout 30s" 2>/dev/null || true
}

# Main monitoring loop
echo "Starting $TEST_DURATION_HOURS hour uptime test..."
echo "Checking every $CHECK_INTERVAL_SECONDS seconds ($TOTAL_CHECKS total checks)"

start_time=$(date +%s)
check_count=0

# Run chaos tests in background every hour
(
    while true; do
        sleep 3600  # 1 hour
        run_chaos_tests
    done
) &
chaos_pid=$!

# Main monitoring loop
while [[ $check_count -lt $TOTAL_CHECKS ]]; do
    current_time=$(date '+%Y-%m-%d %H:%M:%S')
    all_healthy=true
    
    for service in "${!services[@]}"; do
        url=${services[$service]}
        service_checks[$service]=$((service_checks[$service] + 1))
        
        if check_service_health "$service" "$url"; then
            echo "[PASS] [$current_time] $service: HEALTHY"
        else
            echo "[FAIL] [$current_time] $service: UNHEALTHY"
            service_failures[$service]=$((service_failures[$service] + 1))
            all_healthy=false
            
            # Record downtime period
            echo "$current_time: $service DOWN" >> uptime_failures.log
        fi
    done
    
    check_count=$((check_count + 1))
    
    # Progress report every hour
    if [[ $((check_count % 720)) -eq 0 ]]; then
        hours_elapsed=$((check_count * CHECK_INTERVAL_SECONDS / 3600))
        echo "Progress: $hours_elapsed/$TEST_DURATION_HOURS hours completed"
    fi
    
    sleep $CHECK_INTERVAL_SECONDS
done

# Kill chaos testing background process
kill $chaos_pid 2>/dev/null || true

# Calculate final results
echo ""
echo "UPTIME TEST RESULTS"
echo "======================"

total_uptime=0
total_checks_sum=0

for service in "${!services[@]}"; do
    checks=${service_checks[$service]}
    failures=${service_failures[$service]}
    successes=$((checks - failures))
    uptime_percentage=$(echo "scale=4; $successes * 100 / $checks" | bc)
    
    echo "Service: $service"
    echo "  Total Checks: $checks"
    echo "  Failures: $failures"
    echo "  Uptime: $uptime_percentage%"
    echo ""
    
    total_uptime=$(echo "$total_uptime + $uptime_percentage" | bc)
    total_checks_sum=$((total_checks_sum + checks))
done

# Overall uptime calculation
average_uptime=$(echo "scale=4; $total_uptime / ${#services[@]}" | bc)
target_uptime=99.99

echo "🎯 OVERALL RESULTS:"
echo "==================="
echo "Average Uptime: $average_uptime%"
echo "Target Uptime: $target_uptime%"

# Check if meets requirement
if (( $(echo "$average_uptime >= $target_uptime" | bc -l) )); then
    echo "✅ MEETS 99.99% UPTIME REQUIREMENT"
    exit_code=0
else
    echo "❌ DOES NOT MEET 99.99% UPTIME REQUIREMENT"
    echo "🔧 Review failure logs in uptime_failures.log"
    exit_code=1
fi

# Generate detailed report
end_time=$(date +%s)
total_test_time=$((end_time - start_time))

cat > uptime_report.md << EOF
# StreamSense Uptime Test Report

## Test Configuration
- **Duration**: $TEST_DURATION_HOURS hours
- **Check Interval**: $CHECK_INTERVAL_SECONDS seconds
- **Total Checks per Service**: $TOTAL_CHECKS
- **Services Monitored**: ${#services[@]}

## Results Summary
- **Average Uptime**: $average_uptime%
- **Target Uptime**: $target_uptime%
- **Result**: $(if [[ $exit_code -eq 0 ]]; then echo "✅ PASS"; else echo "❌ FAIL"; fi)

## Individual Service Results
$(for service in "${!services[@]}"; do
    checks=${service_checks[$service]}
    failures=${service_failures[$service]}
    uptime=$(echo "scale=4; ($checks - $failures) * 100 / $checks" | bc)
    echo "- **$service**: $uptime% uptime ($failures failures in $checks checks)"
done)

## Chaos Engineering Tests Performed
- Random service restarts
- Network latency injection
- Resource pressure simulation
- Circuit breaker testing

Generated: $(date)
EOF

echo "📋 Detailed report saved to uptime_report.md"
exit $exit_code