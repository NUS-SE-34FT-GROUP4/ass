# Docker Configuration Guide

## 📦 Docker architecture overview

The project runs as a set of services:

### Core services
1. **MySQL 8.0** - main database
2. **Redis 7** - cache layer (captchas, sessions and more)
3. **RabbitMQ 3.12** - message queue
4. **Elasticsearch 8.11** - search engine
5. **MinIO** - object storage
6. **Spring Boot Backend** - backend application
7. **Vue.js Frontend** - frontend application (Nginx)

---

## 📁 Dockerfiles

### 1. Backend Dockerfile (`/Dockerfile`)

```dockerfile
# Multi-stage build (switched to Amazon Corretto)
Stage 1: Maven + Amazon Corretto JDK 17 (build)
Stage 2: Amazon Corretto JRE 17 Alpine (run)

Features:
✅ Cached Maven dependencies
✅ Runs as a non-root user (security)
✅ Container-aware JVM settings
✅ Health check
✅ Minimal image size
✅ The more stable Amazon Corretto image
```

**Key settings:**
- Base image: `amazoncorretto:17-alpine` (more stable)
- Exposed port: 8080
- Health check: `/actuator/health`
- JVM options: container-aware, at most 75% of RAM
- User: non-root user `appuser`

### 2. Frontend Dockerfile (`/frontend/Dockerfile`)

```dockerfile
# Multi-stage build
Stage 1: Node 18 Alpine (build Vue.js)
Stage 2: Nginx Alpine (serve static files)

Features:
✅ Exact dependency install with npm ci
✅ Nginx tuned for production
✅ SPA routing support
✅ API reverse proxy
✅ WebSocket support
```

**Key settings:**
- Build output: `/app/dist`
- Nginx config: supports Vue Router, the API proxy and WebSocket
- Exposed port: 80

---

## 🔧 compose.yaml configuration

### Removed problems
- ❌ Removed the obsolete `version: '3.8'` field
## 🔧 compose.yaml configuration

### Removed problems
- ❌ Removed the obsolete `version: '3.8'` field
- ✅ Uses the current Docker Compose specification

### Service dependencies

```
frontend (80)
    ↓
backend (8080)
    ↓
├── mysql (3306)
├── redis (6379)
├── rabbitmq (5672, 15672)
├── elasticsearch (9200, 9300)
└── minio (9000, 9001)
```

### Health checks

Every service has a health check, so services start only once their dependencies are fully ready:

| Service | Health check command | Interval | Retries |
|------|-------------|------|------|
| MySQL | `mysqladmin ping` | default | 10 |
| Redis | `redis-cli ping` | 5s | 5 |
| RabbitMQ | `rabbitmq-diagnostics ping` | 10s | 5 |
| Elasticsearch | `curl /_cluster/health` | 30s | 5 |
| MinIO | `curl /minio/health/live` | 30s | 3 |
| Backend | `curl /actuator/health` | 30s | 3 |

---

## 🚀 Quick start

### Option 1: the one-command start script (recommended) ⭐

```bash
# Use the fixed full start script
cd /Users/Kiyu/IdeaProjects/temaple/c2csectrade
./scripts/start-fixed.sh
```

The script automatically:
1. ✅ Checks and installs frontend dependencies
2. ✅ Builds the backend Maven project
3. ✅ Builds the frontend Vue.js app
4. ✅ Stops existing containers
5. ✅ Starts every Docker service
6. ✅ Shows service status and URLs

### Option 2: the Makefile

```bash
# Start everything
make up

# Stop the services
make down

# Check status
make ps

# View logs
make logs
```

### Option 3: manual start

#### 3.1 Build and start every service

```bash
# Build the images
docker compose build

# Start every service
docker compose up -d

# View logs
docker compose logs -f
```

#### 3.2 Start a single service

```bash
# Start only the backend and its dependencies
docker compose up -d backend

# Start only the frontend
docker compose up -d frontend
```

#### 3.3 Scale out the backend (load balancing)

```bash
# Start 3 backend instances
docker compose up -d --scale backend=3
```

Note: remove `container_name` from compose.yaml and configure a load balancer first

---

## 🔍 Port mappings

| Service | Internal port | External port | Notes |
|------|---------|---------|------|
| Frontend | 80 | 80 | Web UI |
| Backend | 8080 | 8080 | REST API |
| MySQL | 3306 | 3306 | Database |
| Redis | 6379 | 6379 | Cache |
| RabbitMQ | 5672 | 5672 | AMQP |
| RabbitMQ UI | 15672 | 15672 | Management UI |
| Elasticsearch | 9200 | 9200 | REST API |
| Elasticsearch | 9300 | 9300 | Node communication |
| MinIO | 9000 | 9000 | S3 API |
| MinIO UI | 9001 | 9001 | Management UI |

---

## 🌐 URLs

- **Frontend**: http://localhost
- **Backend API**: http://localhost:8080
- **RabbitMQ management**: http://localhost:15672 (admin/admin123)
- **MinIO console**: http://localhost:9001 (minioadmin/minioadmin)
- **Elasticsearch**: http://localhost:9200

---

## 📊 Data persistence

All data is persisted in Docker volumes:

```yaml
volumes:
  mysql-data         # MySQL database files
  redis-data         # Redis AOF persistence files
  rabbitmq-data      # RabbitMQ queue data
  elasticsearch-data # Elasticsearch index data
```

MinIO uses a bind mount: `./minio/data`

---

## 🔧 Environment variables

### Backend environment variables

```yaml
SPRING_PROFILES_ACTIVE: docker
# Fixed MySQL connection string (solves the utf8mb4 encoding problem)
SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/trade?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useServerPrepStmts=true&cachePrepStmts=true&rewriteBatchedStatements=true
SPRING_DATASOURCE_USERNAME: tradeuser
SPRING_DATASOURCE_PASSWORD: tradepass
SPRING_DATA_REDIS_HOST: redis
SPRING_DATA_REDIS_PORT: 6379
SPRING_RABBITMQ_HOST: rabbitmq
SPRING_RABBITMQ_PORT: 5672
SPRING_RABBITMQ_USERNAME: admin
SPRING_RABBITMQ_PASSWORD: admin123
SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
MINIO_ENDPOINT: http://minio:9000
MINIO_ACCESS_KEY: minioadmin
MINIO_SECRET_KEY: minioadmin
```

### Service-to-service communication

All services communicate on the `c2csectrade-network` bridge network, using service names as host names.

---

## 🛠️ Common commands

### Check service status
```bash
docker compose ps
```

### Follow logs
```bash
docker compose logs -f backend
docker compose logs -f frontend
```

### Restart a service
```bash
docker compose restart backend
```

### Stop and remove every container
```bash
docker compose down
```

### Stop and remove everything, including volumes
```bash
docker compose down -v
```

### Open a shell in a container
```bash
docker compose exec backend sh
docker compose exec mysql mysql -utradeuser -ptradepass trade
docker compose exec redis redis-cli
```

---

## 🐛 Troubleshooting

### 1. MySQL character encoding error ⭐ (fixed)

**Problem**: `Unsupported character encoding 'utf8mb4'`

**Cause**: a compatibility issue between MySQL Connector/J 8.0.33 and the character set settings

**Solution**:
Fixed in `application.properties` and `compose.yaml`:
```properties
# Use characterEncoding=UTF-8 instead of useUnicode=true
spring.datasource.url=jdbc:mysql://mysql:3306/trade?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&useServerPrepStmts=true&cachePrepStmts=true&rewriteBatchedStatements=true
```

### 2. Captcha not showing or not refreshing

**Problem**: the frontend captcha image does not load

**Possible causes**:
1. Redis is not running or the connection failed
2. The backend has not fully started
3. A CORS configuration problem
4. Browser caching

**Steps**:
```bash
# 1. Check the Redis service
docker compose ps redis
docker compose logs redis

# 2. Test the Redis connection
docker compose exec redis redis-cli ping
# Should return: PONG

# 3. Check the backend logs
docker compose logs backend | grep -i "redis\|captcha"

# 4. Test the captcha API manually
curl -i http://localhost:8080/api/captcha/generate
# Check the response headers for Captcha-ID

# 5. Clear the browser cache and hard refresh (Ctrl+Shift+R / Cmd+Shift+R)
```

**Captcha settings**:
- Captcha expiry: 300 seconds (5 minutes)
- Stored in: Redis (key: captcha:{captchaId})
- Captcha length: 4 letters and digits

### 3. The backend cannot connect to the database

**Problem**: `Connection refused` or `Unknown database`

**Fix**:
```bash
# Check MySQL health
docker compose ps mysql

# View the MySQL logs
docker compose logs mysql

# Initialise the database manually
docker compose exec mysql mysql -uroot -prootpassword < init.sql

# Test the database connection
docker compose exec mysql mysql -utradeuser -ptradepass -e "SELECT 1"
```

### 4. The frontend cannot reach the backend API

**Problem**: `502 Bad Gateway`

**Fix**:
```bash
# Check that the proxy address in nginx.conf is http://backend:8080
docker compose exec frontend cat /etc/nginx/conf.d/default.conf

# Confirm the backend is healthy
curl http://localhost:8080/actuator/health

# View the Nginx logs
docker compose logs frontend
```

### 5. Redis connection failure

**Problem**: `Unable to connect to Redis`

**Fix**:
```bash
# Test the Redis connection
docker compose exec redis redis-cli ping

# Check the backend configuration
docker compose exec backend env | grep REDIS

# View the Redis logs
docker compose logs redis

# Restart Redis
docker compose restart redis
```

### 6. Docker image pull failure ⭐ (fixed)

**Problem**: `eclipse-temurin:17-jre-jammy: not found`

**Solution**:
The base image was switched to the more stable Amazon Corretto:
```dockerfile
FROM amazoncorretto:17-alpine
```

### 7. Build failure

**Problem**: Maven dependencies fail to download

**Fix**:
```bash
# Clean and rebuild
docker compose build --no-cache backend

# Clear the Maven cache
rm -rf ~/.m2/repository

# Use a closer Maven mirror (edit pom.xml)
```

### 8. Port already in use

**Problem**: `Bind for 0.0.0.0:6379 failed: port is already allocated`

**Fix**:
```bash
# Find the process using the port
lsof -i :6379  # macOS/Linux
netstat -ano | findstr :6379  # Windows

# Stop the local Redis service
brew services stop redis  # macOS
sudo systemctl stop redis  # Linux

# Or change the port mapping in compose.yaml
ports:
  - "6380:6379"  # Use a different external port
```

---

## 🎯 Best practices

### 1. Development
```bash
# Override settings with docker-compose.override.yml
# Mount the source directory for hot reload
```

### 2. Production
- Use Docker Swarm or Kubernetes
- Use an external database and cache
- Enable TLS/SSL
- Set resource limits (CPU, memory)
- Manage sensitive data with secrets

### 3. Performance
```yaml
# Set resource limits for services
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 2G
    reservations:
      cpus: '1'
      memory: 1G
```

---

## 📝 Versions

- **Docker Compose**: no version field needed (current specification)
- **MySQL**: 8.0
- **Redis**: 7-alpine
- **RabbitMQ**: 3.12-management-alpine
- **Elasticsearch**: 8.11.0
- **MinIO**: latest
- **Java**: Eclipse Temurin 17
- **Node.js**: 18-alpine
- **Nginx**: alpine

---

## 🔐 Security recommendations

1. **Change the default passwords**
   - MySQL root: `rootpassword`
   - MySQL user: `tradepass`
   - RabbitMQ: `admin123`
   - MinIO: `minioadmin`

2. **Use Docker Secrets**
   ```yaml
   secrets:
     db_password:
       file: ./secrets/db_password.txt
   ```

3. **Limit exposed ports**
   ```yaml
   # Expose only on the internal network
   expose:
     - "8080"
   # No ports mapping
   ```

4. **Isolate networks**
   ```yaml
   networks:
     frontend-network:
     backend-network:
   ```

---

## 📚 Related documents

- [Deployment and usage guide](./deployment-and-usage-guide.md)
- [Project overview](./project-overview.md)
- [Distributed system architecture](./distributed-system-architecture.md)

