# Development Guide - Enterprise Streaming Analytics Platform

## 🚀 Quick Start

### Prerequisites
- Docker Desktop 4.15+ with Docker Compose
- Node.js 18+ (for frontend development)
- Java 17+ (for Spring Boot services)
- Python 3.9+ (for ML services)
- Git

### One-Command Setup
```bash
./start.sh
```

This script will:
1. Check system prerequisites
2. Build all Docker images
3. Start infrastructure services (PostgreSQL, Redis, Kafka)
4. Deploy microservices in correct order
5. Start monitoring stack (Prometheus, Grafana)
6. Perform health checks
7. Display access URLs

## 🏗️ Architecture Overview

### Microservices Stack
- **API Gateway** (Spring Boot + Zuul) - Port 8080
- **Eureka Server** (Service Discovery) - Port 8761  
- **Chat Service** (Spring WebFlux) - Port 8081
- **GraphQL Service** (Spring GraphQL) - Port 8082
- **ML Engine** (Python + Transformers) - Port 5000
- **Video Service** (Python + YOLO) - Port 5001
- **Frontend** (React + Material-UI) - Port 3000

### Infrastructure
- **PostgreSQL** - Primary database (Port 5432)
- **Redis** - Caching and pub/sub (Port 6379)
- **Kafka** - Event streaming (Port 9092)
- **Prometheus** - Metrics collection (Port 9090)
- **Grafana** - Monitoring dashboards (Port 3001)

## 🛠️ Development Workflow

### Building Individual Services

#### Java Services (API Gateway, Eureka, Chat, GraphQL)
```bash
cd [service-directory]
./mvnw clean package
docker build -t streaming-analytics/[service-name] .
```

#### Python Services (ML Engine, Video Service)
```bash
cd [service-directory]
pip install -r requirements.txt
docker build -t streaming-analytics/[service-name] .
```

#### Frontend
```bash
cd frontend
npm install
npm run build
docker build -t streaming-analytics/frontend .
```

### Hot Reload Development

#### Java Services
```bash
cd [service-directory]
./mvnw spring-boot:run
```

#### Python Services
```bash
cd [service-directory]
pip install -r requirements.txt
python app.py
```

#### Frontend
```bash
cd frontend
npm start
```

### Database Management

#### Connect to PostgreSQL
```bash
docker exec -it postgres psql -U postgres -d streaming_analytics
```

#### Run migrations
```bash
docker exec -it postgres psql -U postgres -d streaming_analytics -f /docker-entrypoint-initdb.d/init.sql
```

#### Redis CLI
```bash
docker exec -it redis redis-cli
```

## 🧪 Testing

### Unit Tests
```bash
# Java services
cd [java-service]
./mvnw test

# Python services
cd [python-service]
pytest tests/

# Frontend
cd frontend
npm test
```

### Integration Tests
```bash
# Start test environment
docker-compose -f docker-compose.test.yml up -d

# Run integration tests
npm run test:integration
```

### Load Testing
```bash
# Install k6
brew install k6

# Run load tests
k6 run scripts/load-test.js
```

## 📊 Monitoring & Debugging

### Viewing Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f ml-engine

# Follow logs with grep
docker-compose logs -f | grep ERROR
```

### Health Checks
```bash
# API Gateway
curl http://localhost:8080/actuator/health

# ML Engine
curl http://localhost:5000/health

# GraphQL
curl http://localhost:8082/actuator/health
```

### Metrics Access
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/grafana)
- **Eureka Dashboard**: http://localhost:8761

### Performance Profiling

#### Java Services (JProfiler/VisualVM)
```bash
# Enable JMX
export JAVA_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=1099 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
```

#### Python Services (cProfile)
```python
import cProfile
import pstats

profiler = cProfile.Profile()
# ... your code ...
profiler.disable()
stats = pstats.Stats(profiler)
stats.sort_stats('cumulative').print_stats(10)
```

## 🔧 Configuration Management

### Environment Variables
Create `.env` file in project root:
```env
# Database
DATABASE_URL=postgresql://postgres:password@localhost:5432/streaming_analytics
REDIS_HOST=localhost
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Security
JWT_SECRET=your-secret-key
ENCRYPTION_KEY=your-encryption-key

# External APIs
TWITCH_CLIENT_ID=your-twitch-client-id
TWITCH_CLIENT_SECRET=your-twitch-secret
YOUTUBE_API_KEY=your-youtube-key

# ML Models
HUGGINGFACE_TOKEN=your-huggingface-token
MODEL_CACHE_DIR=/app/models
```

### Service Configuration
Each service uses Spring Boot profiles or Python config classes:

#### Spring Boot (application-{profile}.yml)
```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:development}
  datasource:
    url: ${DATABASE_URL}
  redis:
    host: ${REDIS_HOST}
```

#### Python (config.py)
```python
import os

class Config:
    DATABASE_URL = os.getenv('DATABASE_URL')
    REDIS_HOST = os.getenv('REDIS_HOST', 'localhost')
    DEBUG = os.getenv('DEBUG', 'false').lower() == 'true'
```

## 🚀 Deployment

### Local Development
```bash
# Start with rebuild
docker-compose up --build

# Start specific services
docker-compose up postgres redis kafka
```

### Staging Environment
```bash
# Use staging compose file
docker-compose -f docker-compose.staging.yml up -d
```

### Production (Kubernetes)
```bash
# Apply Kubernetes manifests
kubectl apply -f k8s/deployment.yaml

# Check deployment status
kubectl get pods -n streaming-analytics

# View logs
kubectl logs -f deployment/api-gateway -n streaming-analytics
```

## 🐛 Troubleshooting

### Common Issues

#### Service Discovery Problems
```bash
# Check Eureka dashboard
curl http://localhost:8761/eureka/apps

# Restart service registration
docker-compose restart api-gateway
```

#### Database Connection Issues
```bash
# Check PostgreSQL status
docker-compose ps postgres

# Test connection
docker exec -it postgres pg_isready -U postgres
```

#### Memory Issues
```bash
# Check container resources
docker stats

# Increase memory limits in docker-compose.yml
mem_limit: 2g
```

#### Port Conflicts
```bash
# Check port usage
lsof -i :8080

# Kill process using port
kill -9 $(lsof -t -i:8080)
```

### Debug Commands
```bash
# Enter container shell
docker exec -it api-gateway bash

# Check Java heap dump
docker exec api-gateway jcmd 1 GC.run_finalization

# Python debugging
docker exec -it ml-engine python -c "import sys; print(sys.path)"
```

## 📚 API Documentation

### REST Endpoints
- **API Gateway**: http://localhost:8080/swagger-ui.html
- **Chat Service**: http://localhost:8081/swagger-ui.html
- **ML Engine**: http://localhost:5000/docs (FastAPI auto-docs)

### GraphQL
- **Playground**: http://localhost:8082/graphql
- **Schema**: http://localhost:8082/graphql/schema

### WebSocket Endpoints
- **Chat**: ws://localhost:8081/chat
- **Video Stream**: ws://localhost:5001/video
- **Analytics**: ws://localhost:8080/analytics

## 🔐 Security

### Authentication Flow
1. User login via API Gateway
2. JWT token generation
3. Token validation in microservices
4. Role-based access control

### HTTPS Configuration
```bash
# Generate certificates
openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout nginx.key -out nginx.crt

# Update nginx configuration
# (Use nginx.conf.ssl template)
```

### Secret Management
```bash
# Kubernetes secrets
kubectl create secret generic app-secrets \
  --from-literal=database-password=your-password \
  --from-literal=jwt-secret=your-jwt-secret
```

## 📈 Performance Optimization

### Database Optimization
- Connection pooling (HikariCP)
- Read replicas for analytics
- Proper indexing strategies
- Query optimization

### Caching Strategy
- Redis for session storage
- Application-level caching
- CDN for static assets
- Database query caching

### Microservice Communication
- Circuit breakers (Hystrix)
- Load balancing
- Async messaging (Kafka)
- Connection pooling

## 🤝 Contributing

### Code Style
- Java: Google Java Style Guide
- Python: PEP 8 with Black formatter
- JavaScript: Airbnb Style Guide with Prettier

### Commit Convention
```
type(scope): description

feat(chat): add real-time emoji reactions
fix(ml): resolve memory leak in sentiment analysis
docs(readme): update installation instructions
```

### Pull Request Process
1. Create feature branch
2. Write tests
3. Update documentation
4. Submit PR with description
5. Code review and approval
6. Merge to main

## 📞 Support

### Getting Help
- **Issues**: GitHub Issues
- **Discussions**: GitHub Discussions
- **Chat**: Discord Server
- **Email**: team@streaminganalytics.dev

### Monitoring Alerts
- **Slack**: #streaming-alerts
- **PagerDuty**: Production incidents
- **Email**: Non-critical alerts