# 🎮 StreamSense - Enterprise-Scale Real-Time Stream Analytics

[![MIT License](https://img.shields.io/badge/License-MIT-green.svg)](https://choosealicense.com/licenses/mit/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue)](https://www.docker.com/)
[![Microservices](https://img.shields.io/badge/Architecture-Microservices-orange)](https://microservices.io/)
[![Enterprise](https://img.shields.io/badge/Scale-Enterprise-red)](https://microservices.io/)

## 🚀 Overview

StreamSense is a platform that provides real-time analytics for live streaming content. Originally developed as a hackathon project for **Catapult Hacks**, it has been completely refactored by **Ujjawal Prasad** using modern distributed systems architecture inspired by Netflix's microservices architecture. The platform demonstrates the full power of enterprise-scale technology including service discovery, circuit breakers, API gateways, and event-driven communication.

## ✨ Key Features

- **🎯 Real-time Sentiment Analysis** - Advanced NLP for chat sentiment detection
- **🔍 Sponsor Detection** - AI-powered brand and product recognition
- **📺 Video Analytics** - Object detection and logo recognition in video frames
- **💬 Live Chat Processing** - Real-time chat analysis and moderation
- **📊 Interactive Dashboard** - Material-UI React frontend with live charts
- **🌐 API Gateway** - Centralized routing with Zuul proxy patterns
- **🔄 Service Discovery** - Netflix Eureka for automatic service registration
- **⚡ Circuit Breakers** - Hystrix patterns for fault tolerance
- **📈 Monitoring Stack** - Prometheus, Grafana, and Jaeger tracing
- **🐳 Container-First** - Full Docker orchestration with Kubernetes support

## 🏗️ Architecture

### Enterprise Technology Stack
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   React UI      │    │   API Gateway    │    │  Eureka Server  │
│   (Frontend)    │◄───┤   (Zuul Proxy)   │◄───┤ (Discovery)     │
│   Port 3000     │    │   Port 8080      │    │  Port 8761      │
└─────────────────┘    └──────────────────┘    └─────────────────┘
                              │
                ┌─────────────┼─────────────┐
                │             │             │
        ┌───────▼────┐ ┌──────▼──────┐ ┌───▼────────┐
        │ ML Engine  │ │Video Service│ │Chat Service│
        │ (Python)   │ │  (Python)   │ │   (Java)   │
        │ Port 5002  │ │ Port 5003   │ │ Port 8081  │
        └────────────┘ └─────────────┘ └────────────┘
                │             │             │
        ┌───────▼─────────────▼─────────────▼────────┐
        │          Infrastructure Layer              │
        │  Redis │ Kafka │ PostgreSQL │ Prometheus   │
        └────────────────────────────────────────────┘
```

### Core Services

| Service | Technology | Port | Description |
|---------|------------|------|-------------|
| **API Gateway** | Zuul + Python | 8080 | Request routing, load balancing, circuit breaker |
| **Service Discovery** | Eureka Server | 8761 | Service registration and discovery |
| **ML Engine** | Python + Transformers | 5002 | Sentiment analysis, emotion detection |
| **Video Service** | Python + YOLO | 5003 | Object detection, logo recognition |
| **Chat Service** | Spring Boot + WebFlux | 8081 | Real-time chat processing |
| **GraphQL API** | Spring Boot + GraphQL | 8082 | Unified data layer |
| **Frontend** | React + Material-UI | 3000 | Interactive dashboard |

### Infrastructure

| Component | Technology | Port | Purpose |
|-----------|------------|------|---------|
| **PostgreSQL** | Database | 5432 | Data persistence |
| **Redis** | Cache | 6379 | Caching layer |
| **Apache Kafka** | Event Streaming | 9092 | Message processing |
| **Prometheus** | Monitoring | 9090 | Metrics collection |
| **Grafana** | Visualization | 3001 | Monitoring dashboards |
| **Jaeger** | Tracing | 16686 | Distributed tracing |

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- 8GB+ RAM available
- Ports 3000-9200 available

### One-Command Deployment
```bash
# Clone the repository
git clone https://github.com/your-username/StreamSense.git
cd StreamSense

# Make startup script executable
chmod +x start-netflix-stack.sh

# Deploy full enterprise stack
./start-netflix-stack.sh
```

### Individual Service Development
```bash
# Standard microservices stack
chmod +x start.sh
./start.sh start

# View service status
./start.sh status

# Access logs
./start.sh logs [service-name]

# Stop services
./start.sh stop
```

## 📊 Service URLs

Once deployed, access these services:

### Core Services
- 🌐 **API Gateway**: http://localhost:8080
- 🖥️ **Frontend Dashboard**: http://localhost:3000
- 🔧 **Eureka Discovery**: http://localhost:8761
- 📊 **GraphQL Playground**: http://localhost:8080/graphql

### Monitoring & Observability
- 📈 **Grafana Dashboards**: http://localhost:3001 (admin/admin)
- 📊 **Prometheus Metrics**: http://localhost:9090
- 🔍 **Jaeger Tracing**: http://localhost:16686
- ⚡ **Hystrix Dashboard**: http://localhost:8080/hystrix

### Development Services
- 🤖 **ML Engine**: http://localhost:5002
- 📹 **Video Service**: http://localhost:5003
- 💬 **Chat Service**: http://localhost:8081

## 🔧 API Examples

### Sentiment Analysis
```bash
curl -X POST http://localhost:8080/sentiment \
  -H "Content-Type: application/json" \
  -d '{"text": "This stream is absolutely amazing!"}'
```

### Sponsor Detection
```bash
curl -X POST http://localhost:8080/sponsors \
  -H "Content-Type: application/json" \
  -d '{"text": "Check out this awesome Razer keyboard!"}'
```

### Video Frame Analysis
```bash
curl -X POST http://localhost:5003/analyze/frame \
  -H "Content-Type: application/json" \
  -d '{"image_data": "base64_encoded_frame"}'
```

### GraphQL Query
```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "{ liveStreams(limit: 10) { id streamerName viewerCount } }"}'
```

## 🛠️ Technology Stack

### Backend Services
- **Python**: Flask, Transformers, YOLO, OpenCV
- **Java**: Spring Boot, Spring Cloud, WebFlux
- **Node.js**: Express, GraphQL

### Enterprise OSS Components
- **Eureka**: Service discovery and registration
- **Zuul**: API gateway and intelligent routing
- **Hystrix**: Circuit breaker and fault tolerance
- **Ribbon**: Client-side load balancing

### Data & Messaging
- **PostgreSQL**: Primary database with advanced features
- **Redis**: Distributed caching and session storage
- **Apache Kafka**: Event streaming and message processing
- **Elasticsearch**: Search and log aggregation

### Frontend & UI
- **React**: Modern component-based UI
- **Material-UI**: Professional design system
- **Recharts**: Real-time data visualization
- **WebSocket**: Live data streaming

### DevOps & Monitoring
- **Docker**: Containerization and orchestration
- **Kubernetes**: Production container orchestration
- **Prometheus**: Metrics collection and alerting
- **Grafana**: Monitoring dashboards and visualization
- **Jaeger**: Distributed request tracing

## 📁 Project Structure

```
StreamSense/
├── api-gateway/                 # Enterprise Zuul Gateway (Java)
├── eureka-server/              # Service Discovery (Java)
├── chat-service/               # Chat Processing (Java)
├── graphql-service/            # GraphQL API (Java)
├── ml-engine/                  # Sentiment & Sponsor Detection (Python)
├── video-service/              # Video Analytics (Python)
├── sentiment-service/          # Standalone Sentiment (Python)
├── frontend/                   # React Dashboard
├── database/                   # PostgreSQL schemas
├── k8s/                       # Kubernetes deployments
├── monitoring/                 # Prometheus & Grafana configs
├── docker-compose.yml         # Development stack
├── docker-compose-netflix.yml # Production enterprise stack
├── start.sh                   # Development startup
├── start-enterprise-stack.sh  # Production startup
└── README.md                  # This file
```

## 🔍 Monitoring & Observability

### Prometheus Metrics
- Service health and performance metrics
- Custom business metrics (sentiment scores, detection rates)
- Infrastructure monitoring (CPU, memory, disk)
- Request rates and error tracking

### Grafana Dashboards
- Real-time service performance
- Business metrics visualization
- Infrastructure health monitoring
- Custom StreamSense analytics

### Jaeger Distributed Tracing
- End-to-end request tracing
- Service dependency mapping
- Performance bottleneck identification
- Error propagation analysis

## 🚨 Health Checks

Monitor service health:
```bash
# API Gateway health
curl http://localhost:8080/actuator/health

# Service discovery status
curl http://localhost:8761/actuator/health

# Individual service health
curl http://localhost:5002/health  # ML Engine
curl http://localhost:5003/health  # Video Service
curl http://localhost:8081/actuator/health  # Chat Service
```

## 🐛 Troubleshooting

### Common Issues

**Port Conflicts**
```bash
# Check port usage
lsof -i :8080
# Kill conflicting processes
kill -9 $(lsof -t -i:8080)
```

**Service Discovery Issues**
```bash
# Check Eureka dashboard
curl http://localhost:8761/eureka/apps
```

**Container Problems**
```bash
# View container logs
docker-compose logs -f [service-name]
# Restart specific service
docker-compose restart [service-name]
```

### Performance Optimization

**Memory Settings**
- Ensure 8GB+ RAM available
- Adjust Docker memory limits in compose files
- Monitor resource usage via Grafana

**Network Configuration**
- Verify all required ports are available
- Check firewall settings
- Ensure Docker networking is properly configured

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Enterprise open-source community for the amazing microservices toolkit
- Spring Boot community for excellent framework support
- Hugging Face for state-of-the-art NLP models
- React and Material-UI teams for excellent frontend tools
- **Catapult Hacks** for providing the initial hackathon opportunity
---

**Built with ❤️ for the streaming community**
