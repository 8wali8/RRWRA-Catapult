#!/bin/bash

# Enterprise Streaming Analytics Platform - Quick Start Script
# Provides development and production environment setup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
print_section() {
    echo -e "\n${BLUE}=== $1 ===${NC}"
}

print_success() {
    echo -e "${GREEN}[SUCCESS] $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}[WARNING] $1${NC}"
}

print_error() {
    echo -e "${RED}[ERROR] $1${NC}"
}

# Banner
echo -e "${BLUE}"
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║              Streaming Analytics Platform                     ║"
echo "║                  Enterprise Setup                             ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check if Docker is running
print_section "Docker Status Check"
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker Desktop."
    exit 1
fi
print_success "Docker is running"

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    print_error "docker-compose not found. Please install Docker Compose."
    exit 1
fi
print_success "Docker Compose is available"

# Build and start services
print_section "Building and Starting Services"
echo "This may take several minutes for initial setup..."

# Clean up any existing containers
print_warning "Cleaning up existing containers..."
docker-compose down --volumes --remove-orphans || true

# Build all services
print_warning "Building all services..."
docker-compose build --no-cache

# Start infrastructure services first
print_section "Starting Infrastructure Services"
docker-compose up -d postgres redis zookeeper kafka

# Wait for infrastructure to be ready
print_warning "Waiting for infrastructure services to be ready..."
sleep 30

# Start application services
print_section "Starting Application Services"
docker-compose up -d eureka-server
sleep 20

docker-compose up -d api-gateway chat-service graphql-service ml-engine video-service

# Wait for services to register
sleep 30

# Start frontend and monitoring
docker-compose up -d frontend prometheus grafana

# Health checks
print_section "Health Checks"
echo "Checking service health..."

services=(
    "http://localhost:8761 Eureka-Server"
    "http://localhost:8888/actuator/health Config-Server"
    "http://localhost:8080/actuator/health API-Gateway"
    "http://localhost:8081/actuator/health Chat-Service"
    "http://localhost:8082/actuator/health GraphQL-Service"
    "http://localhost:8083/actuator/health Sentiment-Service"
    "http://localhost:8084/actuator/health Recommendation-Service"
    "http://localhost:5000/health ML-Engine"
    "http://localhost:5001/health Video-Service"
    "http://localhost:3000 Frontend"
    "http://localhost:9090 Prometheus"
    "http://localhost:3001 Grafana"
    "http://localhost:9411 Zipkin"
)

for service in "${services[@]}"; do
    url=$(echo $service | cut -d' ' -f1)
    name=$(echo $service | cut -d' ' -f2)
    
    if curl -s --max-time 10 $url > /dev/null 2>&1; then
        print_success "$name is healthy"
    else
        print_warning "$name may still be starting..."
    fi
done

# Display access information
print_section "Access Information"
echo -e "${GREEN}"
echo "Application URLs:"
echo "   Frontend:          http://localhost:3000"
echo "   API Gateway:       http://localhost:8080"
echo "   Eureka Dashboard:  http://localhost:8761"
echo "   GraphQL Playground: http://localhost:8082/graphql"
echo ""
echo "Monitoring:"
echo "   Prometheus:        http://localhost:9090"
echo "   Grafana:          http://localhost:3001 (admin/grafana)"
echo ""
echo "Data Services:"
echo "   PostgreSQL:        localhost:5432"
echo "   Redis:            localhost:6379"
echo "   Kafka:            localhost:9092"
echo -e "${NC}"

print_section "Development Commands"
echo "Useful commands:"
echo "  docker-compose logs -f [service-name]  # View logs"
echo "  docker-compose ps                      # View status"
echo "  docker-compose down                    # Stop all services"
echo "  docker-compose up -d [service-name]    # Start specific service"

print_success "Enterprise Streaming Analytics Platform is ready!"

read -p "Open application URLs in browser? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    open "http://localhost:3000" 2>/dev/null || echo "Frontend: http://localhost:3000"
    open "http://localhost:8761" 2>/dev/null || echo "Eureka: http://localhost:8761"
    open "http://localhost:9090" 2>/dev/null || echo "Prometheus: http://localhost:9090"
    open "http://localhost:3001" 2>/dev/null || echo "Grafana: http://localhost:3001"
fi