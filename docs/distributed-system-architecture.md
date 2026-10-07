# c2csectrade Distributed System Architecture

## System overview

c2csectrade is a **production-grade distributed second-hand trading platform** supporting online trade in many categories (electronics, clothing and bags, books, home and living, sports and outdoors and more). It uses a distributed architecture with several middleware and distributed components to improve scalability, reliability and performance.

## Distributed components

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue.js)                     │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ Controllers  │  │  Services     │  │  Mappers     │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
         ↓                    ↓                    ↓
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│     Redis      │  │   RabbitMQ     │  │    MySQL       │
│(Cache/Session) │  │  (Messaging)   │  │ (Persistence)  │
└────────────────┘  └────────────────┘  └────────────────┘
         ↓                    ↓
┌────────────────┐  ┌────────────────┐
│ Elasticsearch  │  │     MinIO      │
│  (Full-text)   │  │(Object storage)│
└────────────────┘  └────────────────┘
```

## 1. Redis distributed cache

### 1.1 Use cases

#### User session management
- **Key format**: `spring:session:sessions:{sessionId}`
- **Data type**: Hash
- **TTL**: 30 minutes (renewable)
- **Purpose**: distributed sessions for multi-instance deployment

#### Recommendation cache
```
# User browsing history
Key: history:user:{userId}
Type: Sorted Set
Score: timestamp
TTL: permanent

# User interest profile
Key: profile:user:{userId}
Type: Hash
Field: tag -> interest_score
TTL: permanent

# Collaborative filtering recommendations
Key: recommend:item-cf:{productId}
Type: Sorted Set
Score: similarity
TTL: permanent (updated daily)

# Content similarity recommendations
Key: recommend:content:{productId}
Type: Sorted Set
Score: similarity
TTL: 7 days

# Product popularity
Key: recommend:popularity
Type: Sorted Set
Score: popularity_score
TTL: permanent (updated in real time)
```

#### Distributed locks
```java
// Prevent overselling
Key: lock:product:{productId}
Type: String
Value: lockId
TTL: 10 seconds
```

#### Rate limiting
```java
// API rate limiting
Key: ratelimit:api:{userId}:{endpoint}
Type: String
Value: count
TTL: 1 minute
```

### 1.2 Configuration

```properties
# Redis configuration
spring.redis.host=redis
spring.redis.port=6379
spring.redis.database=0
spring.redis.timeout=3000ms
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.min-idle=0
```

### 1.3 Performance tuning

1. **Pipelined batch operations**: fewer network round trips
2. **Connection pooling**: reuse connections with the Lettuce pool
3. **Serialisation**: Jackson2JsonRedisSerializer
4. **Expiry**: sensible TTLs and regular cleanup

## 2. RabbitMQ message queue

### 2.1 Exchange and queue design

#### Order exchange (order.exchange)
```
Queue: order.paid.queue
Routing Key: order.paid
Purpose: handle order-paid events

Queue: order.canceled.queue
Routing Key: order.canceled
Purpose: handle order-cancelled events

Queue: order.completed.queue
Routing Key: order.completed
Purpose: handle order-completed events
```

#### Notification exchange (notification.exchange)
```
Queue: notification.email.queue
Routing Key: notification.email
Purpose: send email notifications

Queue: notification.system.queue
Routing Key: notification.system
Purpose: send system messages
```

#### Recommendation exchange (recommendation.exchange)
```
Queue: recommendation.update.queue
Routing Key: recommendation.update
Purpose: update recommendation data in real time
```

#### Product exchange (product.exchange)
```
Queue: product.created.queue
Routing Key: product.created
Purpose: handle product-created events

Queue: product.stock.low.queue
Routing Key: product.stock.low
Purpose: handle low-stock warnings
```

### 2.2 Event-driven architecture

#### Publishing events
```java
@Autowired
private EventPublisher eventPublisher;

// Order paid
eventPublisher.publishOrderPaid(new OrderPaidEvent(
    orderId, userId, amount, paymentMethod, new Date()
));
```

#### Consuming events
```java
@RabbitListener(queues = "order.paid.queue")
public void handleOrderPaid(OrderPaidEvent event) {
    // Process the order payment asynchronously
    // Send notifications, update statistics, trigger recommendation updates and so on
}
```

### 2.3 Reliability

1. **Persistent messages**: durable=true
2. **Acknowledgements**: manual ack
3. **Dead-letter queue**: handles failed messages
4. **Message TTL**: prevents messages piling up
5. **Retries**: failed messages are retried automatically

### 2.4 Configuration

```properties
# RabbitMQ configuration
spring.rabbitmq.host=rabbitmq
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=admin123
spring.rabbitmq.virtual-host=/

# Message acknowledgement
spring.rabbitmq.listener.simple.acknowledge-mode=auto
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
```

## 3. MySQL primary-replica replication (optional)

### 3.1 Read/write splitting

```java
// Writes - primary
@Transactional
public void createOrder(Order order) {
    orderMapper.insert(order);
}

// Reads - replica (read-only)
@Transactional(readOnly = true)
public List<Order> getUserOrders(Integer userId) {
    return orderMapper.findByUserId(userId);
}
```

### 3.2 Database connection pool

```properties
# HikariCP connection pool
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

## 4. Elasticsearch distributed search

### 4.1 Use cases

- Full-text product search
- Complex filters
- Aggregations and statistics
- Search suggestions

### 4.2 Index design

```json
{
  "mappings": {
    "properties": {
      "productId": { "type": "long" },
      "name": { 
        "type": "text", 
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "description": { "type": "text" },
      "category": { "type": "keyword" },
      "price": { "type": "double" },
      "location": { "type": "keyword" },
      "createdAt": { "type": "date" }
    }
  }
}
```

### 4.3 Search tuning

1. **Tokenisation**: the IK Chinese analyser
2. **Highlighting**: highlighted search results
3. **Relevance ranking**: BM25
4. **Aggregations**: price ranges and category counts

## 5. MinIO object storage

### 5.1 Storage layout

```
Bucket: mall
├── 2025/
│   ├── 01/
│   │   ├── product_xxx.jpg
│   │   └── avatar_yyy.png
│   ├── 02/
│   └── ...
```

### 5.2 Access control

- **Public**: product images, user avatars
- **Private**: order receipts, private files

### 5.3 Configuration

```properties
# MinIO configuration
minio.endpoint=http://minio:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
minio.bucket-name=mall
```

## 6. Distributed transactions

### 6.1 Local transactions
Spring @Transactional guarantees single-database transactions

### 6.2 Distributed transactions
We use **eventual consistency**:

1. **Event-driven**: processed asynchronously through the message queue
2. **Compensation**: failures are rolled back with compensating actions
3. **Idempotency**: consuming a message twice does not change the result

Example: the order payment flow
```
1. Create the order (local transaction)
2. Publish OrderPaidEvent (message queue)
3. The consumer:
   - Deducts stock
   - Sends notifications
   - Updates recommendations
   (each step in its own transaction, idempotent)
```

## 7. Distributed deployment

### 7.1 Docker Compose deployment

```yaml
services:
  backend:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - mysql
      - redis
      - rabbitmq
      - minio
    environment:
      - SPRING_PROFILES_ACTIVE=docker
    deploy:
      replicas: 3  # 3 instances
      
  nginx:
    image: nginx
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - backend
```

### 7.2 Load balancing

Nginx reverse proxy:
```nginx
upstream backend {
    server backend1:8080;
    server backend2:8080;
    server backend3:8080;
}

server {
    location /api/ {
        proxy_pass http://backend;
    }
}
```

## 8. Monitoring and operations

### 8.1 Health checks

```java
@RestController
public class HealthController {
    @GetMapping("/actuator/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

### 8.2 Log aggregation

Using the ELK Stack:
- **Elasticsearch**: stores logs
- **Logstash**: collects and processes logs
- **Kibana**: visual queries

### 8.3 Performance monitoring

- **Prometheus**: collects metrics
- **Grafana**: monitoring dashboards
- **Metrics**: API response time, QPS, error rate

## 9. Scalability

### 9.1 Horizontal scaling

Every service is stateless, so it scales by adding instances:
```bash
docker-compose up --scale backend=5
```

### 9.2 Vertical scaling

Give a single instance more resources:
```yaml
deploy:
  resources:
    limits:
      cpus: '2'
      memory: 4G
```

### 9.3 Caching strategy

Multi-level cache:
1. **Local cache** (Caffeine): hot data
2. **Redis cache**: shared data
3. **Database**: persistent data

## 10. Disaster recovery and high availability

### 10.1 Redis high availability
- **Redis Sentinel**: primary-replica failover
- **Redis Cluster**: sharded cluster

### 10.2 RabbitMQ high availability
- **Mirrored queues**: replicated across nodes
- **Federated queues**: across data centres

### 10.3 MySQL high availability
- **Primary-replica replication**: read/write splitting
- **MHA**: automatic failover

### 10.4 Backups
- **Database**: daily full backups plus incremental backups
- **Object storage**: cross-region replication
- **Configuration**: version-controlled in Git

## Performance targets

### Capacity
- **Concurrent users**: 10,000+
- **QPS**: 5,000+
- **Response time**: P95 < 500ms
- **Availability**: 99.9%

### Resource usage
- **CPU**: < 70%
- **Memory**: < 80%
- **Disk IO**: < 80%
- **Network bandwidth**: < 70%

## Future work

1. **Microservices**: split the monolith into independent microservices
2. **Service mesh**: adopt Istio for service governance
3. **Container orchestration**: move to Kubernetes
4. **Real-time streams**: process real-time data with Flink/Spark Streaming
5. **Distributed tracing**: trace request paths with Zipkin/Jaeger
6. **Configuration center**: dynamic configuration with Nacos/Apollo

