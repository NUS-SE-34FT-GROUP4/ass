# c2csectrade Smart Second-hand Marketplace - Deployment and Usage Guide

## System overview

c2csecTrade is a complete **distributed second-hand trading platform** supporting trade in electronics, clothing and bags, books, home and living, sports and outdoors and other categories. The system has been upgraded from a monolith to a distributed architecture and integrates several technologies:

### Core technology stack
- **Backend framework**: Spring Boot 3.2.3
- **Database**: MySQL 8.0 (with primary-replica replication support)
- **Cache**: Redis 7 (distributed cache + session management)
- **Message queue**: RabbitMQ 3.12 (event-driven architecture)
- **Search engine**: Elasticsearch 8.11
- **Object storage**: MinIO
- **Frontend framework**: Vue.js 3
- **Containers**: Docker + Docker Compose

### Advanced recommendation features
✅ **Collaborative filtering** (Item-Based CF)
✅ **Content-based recommendation** (TF-IDF + cosine similarity)
✅ **Hybrid recommendation** (Hybrid: CF + Content + Popularity)
✅ **Neural collaborative filtering** (NCF: GMF + MLP)
✅ **Real-time recommendation** (Real-time Stream Processing)
✅ **A/B testing framework** (Multi-variant Testing)
✅ **Multi-objective optimisation** (CTR + CVR + Revenue + Diversity)

## Quick start

### 1. Requirements

- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **JDK**: 17+
- **Node.js**: 16+ (development)
- **Memory**: at least 8 GB (16 GB recommended)
- **Disk**: 20 GB+

### 2. One-command start (production)

**Recommended - use the fixed start script:**
```bash
# Clone the project
cd /Users/Kiyu/IdeaProjects/temaple/c2csectrade

# Use the full start script (recommended) ⭐
./scripts/start-fixed.sh

# Or use the Makefile
make up
```

**Manual start:**
```bash
# Start every service
docker compose up -d

# Check service status
docker compose ps

# View logs
docker compose logs -f backend
```

**What the start script does:**
- ✅ Checks and installs frontend dependencies (including the WebSocket libraries)
- ✅ Builds the backend Maven project
- ✅ Builds the frontend Vue.js app
- ✅ Stops existing containers to avoid conflicts
- ✅ Starts every Docker service
- ✅ Shows service status and URLs

### 3. Service URLs

- **Frontend**: http://localhost:80
- **Backend API**: http://localhost:8080
- **RabbitMQ management**: http://localhost:15672 (admin/admin123)
- **MinIO console**: http://localhost:9001 (minioadmin/minioadmin)
- **Elasticsearch**: http://localhost:9200

### 4. Local development

```bash
# Start the dependencies (MySQL, Redis, RabbitMQ and so on)
docker-compose up -d mysql redis rabbitmq elasticsearch minio

# Backend development
cd /Users/Kiyu/IdeaProjects/temaple/c2csectrade
mvn clean install
mvn -pl core spring-boot:run      # Search: -pl search, Chat: -pl chat

# Frontend development
cd frontend
npm install
npm run serve
```

## Recommendation system guide

### 1. Initialise the recommender

The recommendation models initialise automatically the first time the system starts. You can also trigger it manually:

```bash
# Call this after signing in as an administrator
curl -X POST http://localhost:8080/api/recommendations/compute \
  -H "Authorization: Bearer <admin-token>"
```

### 2. A/B test configuration

Five recommendation variants are configured by default:

- **Control (20%)**: collaborative filtering
- **Variant A (20%)**: content-based
- **Variant B (20%)**: hybrid
- **Variant C (20%)**: NCF neural network
- **Variant D (20%)**: real-time

Users are assigned to a variant by their ID, and the system tracks each group's performance metrics.

### 3. View A/B test results

```bash
# Get the experiment results
curl http://localhost:8080/api/recommendations/abtest/results/recommendation_algorithm_v1
```

Example response:
```json
{
  "control": {
    "impressions": 10000,
    "clicks": 250,
    "conversions": 25,
    "ctr": 0.025,
    "cvr": 0.1,
    "totalRevenue": 2500.0
  },
  "variant_c": {
    "impressions": 10000,
    "clicks": 320,
    "conversions": 35,
    "ctr": 0.032,
    "cvr": 0.109,
    "totalRevenue": 3500.0
  }
}
```

### 4. Real-time recommendation tracking

Every user interaction is processed in real time:

```javascript
// Tracked automatically by the frontend
// Product view
axios.post('/api/history/view', { productId: 123 });

// Add to favorites
axios.post('/api/favorites/add/123');

// Add to cart
axios.post('/api/cart/add', { productId: 123, quantity: 1 });
```

The backend automatically:
- Updates the user interest profile
- Trains the NCF model
- Updates popularity scores
- Publishes to the message queue

## Redis usage

### Key data structures

```
# User browsing history
history:user:{userId} -> Sorted Set (score=timestamp)

# User interest profile
profile:user:{userId} -> Hash (tag -> score)

# Collaborative filtering recommendations
recommend:item-cf:{productId} -> Sorted Set (score=similarity)

# Content similarity
recommend:content:{productId} -> Sorted Set (score=similarity)

# Product popularity
recommend:popularity -> Sorted Set (score=popularity)

# NCF model parameters
ncf:embedding:user:{userId} -> Array[32]
ncf:embedding:item:{productId} -> Array[32]

# Real-time session
realtime:session:{userId} -> List (last 20 interactions)

# Real-time trends
realtime:trending -> Sorted Set (score=timestamp)

# A/B test groups
abtest:user:{experimentId}:{userId} -> String (variant)

# Distributed locks
lock:{resource} -> String (lockId)
```

### Redis monitoring

```bash
# Check Redis status
docker exec c2csectrade-redis redis-cli INFO

# Check memory usage
docker exec c2csectrade-redis redis-cli INFO memory

# Check the number of keys
docker exec c2csectrade-redis redis-cli DBSIZE

# Check slow queries
docker exec c2csectrade-redis redis-cli SLOWLOG GET 10
```

## RabbitMQ message queue

### Exchanges and queues

**Order exchange** (order.exchange):
- `order.paid.queue` - order paid
- `order.canceled.queue` - order cancelled
- `order.completed.queue` - order completed

**Notification exchange** (notification.exchange):
- `notification.email.queue` - email notifications
- `notification.system.queue` - system messages

**Recommendation exchange** (recommendation.exchange):
- `recommendation.update.queue` - recommendation updates

**Product exchange** (product.exchange):
- `product.created.queue` - product created
- `product.stock.low.queue` - low stock

### Message examples

```java
// Publish an order-paid event
OrderPaidEvent event = new OrderPaidEvent(
    orderId, userId, amount, "alipay", new Date()
);
eventPublisher.publishOrderPaid(event);

// Handled automatically by the consumer
@RabbitListener(queues = "order.paid.queue")
public void handleOrderPaid(OrderPaidEvent event) {
    // Send notifications
    // Update recommendations
    // Log
}
```

### RabbitMQ management

Management UI: http://localhost:15672

- Username: admin
- Password: admin123

You can see:
- Queue backlog
- Consumption rate
- Message throughput

## Performance tuning

### 1. Redis

```properties
# Enlarge the connection pool
spring.data.redis.lettuce.pool.max-active=20
spring.data.redis.lettuce.pool.max-idle=10

# Use pipelining
# Use redisCacheService.batchSet() for batch operations
```

### 2. RabbitMQ

```properties
# Increase the prefetch count
spring.rabbitmq.listener.simple.prefetch=10

# Enable concurrent consumers
spring.rabbitmq.listener.simple.concurrency=5
spring.rabbitmq.listener.simple.max-concurrency=10
```

### 3. Database

```sql
-- Add indexes
CREATE INDEX idx_product_category ON pms_product(category);
CREATE INDEX idx_product_status_stock ON pms_product(status, stock);
CREATE INDEX idx_product_created_at ON pms_product(created_at);

-- Clean up expired data regularly
DELETE FROM browsing_history WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
```

### 4. Recommender

```bash
# Adjust how often recommendations are computed (by product count)
# Products < 1000: hourly
# Products 1000-10000: every 6 hours
# Products > 10000: daily

# Adjust the candidate set size
# Collaborative filtering: top 50
# Content similarity: top 50
# Real-time recommendation: top 30
```

## Monitoring and alerting

### 1. Application monitoring

```bash
# Check health
curl http://localhost:8080/actuator/health

# Check metrics
curl http://localhost:8080/actuator/metrics
```

### 2. Logs

```bash
# Application logs
docker-compose logs -f backend

# Redis logs
docker-compose logs -f redis

# RabbitMQ logs
docker-compose logs -f rabbitmq
```

### 3. Performance metrics

Monitor these key metrics:
- **QPS**: queries per second
- **Response time**: P50, P95, P99
- **Error rate**: 4xx and 5xx errors
- **Cache hit rate**: > 95%
- **Message backlog**: < 1000

## Troubleshooting

### Problem 1: no recommendations

**Cause**: not enough initial data, or computation has not finished

**Fix**:
```bash
# Check whether Redis has recommendation data
docker exec c2csectrade-redis redis-cli KEYS "recommend:*"

# Trigger computation manually
curl -X POST http://localhost:8080/api/recommendations/compute \
  -H "Authorization: Bearer <admin-token>"
```

### Problem 2: RabbitMQ message backlog

**Cause**: consumers are too slow

**Fix**:
```bash
# Increase consumer concurrency
# Adjust it in application.properties
spring.rabbitmq.listener.simple.concurrency=10

# Or purge the queue temporarily (test environments)
docker exec c2csectrade-rabbitmq rabbitmqctl purge_queue order.paid.queue
```

### Problem 3: Redis out of memory

**Cause**: too much cached data

**Fix**:
```bash
# Check memory usage
docker exec c2csectrade-redis redis-cli INFO memory

# Clear expired keys
docker exec c2csectrade-redis redis-cli --scan --pattern "cache:*" | xargs redis-cli DEL

# Set a maximum memory limit
docker exec c2csectrade-redis redis-cli CONFIG SET maxmemory 2gb
docker exec c2csectrade-redis redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

## Scaling out

### Horizontal scaling

```bash
# Scale the backend to 3 instances
docker-compose up -d --scale backend=3

# Configure Nginx load balancing
# Edit nginx.conf
upstream backend {
    server backend1:8080;
    server backend2:8080;
    server backend3:8080;
}
```

### Redis cluster

```yaml
# docker-compose.yml
redis-master:
  image: redis:7-alpine
  
redis-slave-1:
  image: redis:7-alpine
  command: redis-server --slaveof redis-master 6379
  
redis-slave-2:
  image: redis:7-alpine
  command: redis-server --slaveof redis-master 6379
```

## Security recommendations

1. **Change the default passwords**
   - MySQL: root password
   - RabbitMQ: admin password
   - Redis: enable password authentication

2. **Enable HTTPS**
   ```bash
   # Get a certificate from Let's Encrypt
   certbot certonly --standalone -d yourdomain.com
   ```

3. **Rate limiting**
   ```java
   // Rate limiting with Redis
   Long count = redisCacheService.incrementWithExpire(
       "ratelimit:user:" + userId, 60
   );
   if (count > 100) {
       throw new RateLimitException();
   }
   ```

4. **Backups**
   ```bash
   # MySQL backup
   docker exec c2csectrade-mysql mysqldump -uroot -p trade > backup.sql
   
   # Redis backup
   docker exec c2csectrade-redis redis-cli BGSAVE
   ```

## Summary

The system now has:
✅ All compilation errors fixed
✅ Deep Redis integration (cache, locks, Bloom filters)
✅ RabbitMQ messaging
✅ Neural collaborative filtering (NCF)
✅ Real-time recommendations
✅ An A/B testing framework
✅ Multi-objective optimisation
✅ A complete distributed architecture
✅ Production-grade configuration and monitoring

The system is ready for production deployment! 🎉

