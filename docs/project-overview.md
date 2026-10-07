# c2csectrade - Smart Second-hand Marketplace

## 🎯 Positioning

c2csectrade is an **enterprise-grade distributed second-hand trading platform** focused on giving users a safe, convenient and smart way to trade items they no longer need. It supports **many product categories**, including but not limited to:

- 📱 **Electronics**: phones, tablets, computers, cameras and more
- 👕 **Clothing, shoes and bags**: clothing, footwear, bags and accessories
- 📚 **Books and textbooks**: textbooks, novels, professional books
- 🏠 **Home and living**: furniture, appliances, household items
- ⚽ **Sports and outdoors**: sports equipment, outdoor gear
- 🎮 **Entertainment and collectibles**: games, instruments, figures
- 🚗 **Vehicles**: bicycles, e-bikes (on campus)
- 🎨 **Other**: beauty, baby, pet supplies and more

## 🌟 Key features

### 1. Smart recommendations
- **5 recommendation algorithms**: collaborative filtering, content-based, NCF neural network, real-time and hybrid
- **A/B testing framework**: data-driven algorithm tuning
- **Multi-objective optimisation**: balances CTR, CVR, revenue, diversity and novelty
- **Real-time recommendations**: millisecond responses based on the user's session

### 2. End-to-end trading
- **Product listing**: rich-text editor, multi-image upload, many categories
- **Smart search**: Elasticsearch full-text search with multiple filters
- **Cart**: bulk management and combined checkout
- **Orders**: automatic splitting and status tracking
- **Payments**: account balance, payment password, simulated payment
- **Order tracking**: order status updated in real time

### 3. Social commerce
- **Instant messaging**: WebSocket chat between buyers and sellers
- **Bargaining**: start a bargain and invite friends to help
- **Favorites**: save items you like, with price change alerts
- **Two-way reviews**: both sides rate and review after a trade
- **Credit system**: smart credit scores and visible credit levels

### 4. Distributed architecture
- **Redis cache**: distributed cache, session management, distributed locks
- **RabbitMQ**: event-driven, asynchronous processing, smoothing traffic peaks
- **Elasticsearch**: high-performance full-text search and aggregations
- **MinIO**: distributed object storage for images and videos
- **Load balancing**: horizontal scaling with multiple instances

### 5. Security and administration
- **Authentication**: JWT tokens, secure encryption
- **Access control**: RBAC permission model
- **Reports**: users report, administrators review
- **User management**: ban/unban, behaviour monitoring
- **Data backup**: regular backups and disaster recovery

## 📊 Technical architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Vue.js 3)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Browse   │  │ Cart     │  │ Orders   │  │ Chat     │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                              ↓ REST API + WebSocket
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot Application (multi-instance)       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │Recommend │  │ Orders   │  │ Payments │  │ Chat     │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
           ↓              ↓              ↓              ↓
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐
│   Redis    │  │  RabbitMQ  │  │   MySQL    │  │    MinIO   │
│   Cache    │  │ Messaging  │  │Persistence │  │Object store│
└────────────┘  └────────────┘  └────────────┘  └────────────┘
           ↓
┌────────────────┐
│ Elasticsearch  │
│Full-text search│
└────────────────┘
```

## 🎨 Recommendation system architecture

```
User request
   ↓
[A/B test split] → 5 recommendation strategies
   ├─ Control: collaborative filtering (20%)
   ├─ Variant A: content-based (20%)
   ├─ Variant B: hybrid (20%)
   ├─ Variant C: NCF neural network (20%)
   └─ Variant D: real-time (20%)
   ↓
[Candidate generation]
   ├─ Collaborative filtering (Item-Based CF)
   ├─ Content similarity (TF-IDF)
   ├─ NCF neural network (GMF + MLP)
   ├─ Real-time trend analysis
   └─ User interest profile
   ↓
[Multi-objective optimisation]
   ├─ CTR prediction (25%)
   ├─ CVR prediction (30%)
   ├─ Revenue optimisation (20%)
   ├─ Diversity (15%)
   └─ Novelty (10%)
   ↓
[MMR re-ranking] → more diversity
   ↓
[Redis cache] → millisecond responses
   ↓
Return recommendations
```

## 📈 Performance targets

### System performance
- **Concurrent users**: 10,000+
- **QPS**: 5,000+
- **Response time**: P95 < 500ms, P99 < 1s
- **Availability**: 99.9%

### Recommendation performance
- **Coverage**: > 85%
- **Cache hit rate**: > 95%
- **Response time**: P99 < 200ms
- **Real-time updates**: < 5 minutes

### Business metrics
- **Click-through rate (CTR)**: target > 3%
- **Conversion rate (CVR)**: target > 0.5%
- **User satisfaction**: target > 4.0/5.0

## 🚀 Quick start

### Requirements
- Docker & Docker Compose
- JDK 17+
- Node.js 16+
- 8GB+ RAM

### One-command start
```bash
# Clone the project
git clone <repository-url>
cd c2csectrade

# Start every service
docker-compose up -d

# Open the application
# Frontend: http://localhost:80
# Backend: http://localhost:8080
# RabbitMQ: http://localhost:15672 (admin/admin123)
# MinIO: http://localhost:9001 (minioadmin/minioadmin)
```

See the full deployment guide: [Deployment and usage guide](docs/deployment-and-usage-guide.md)

## 📚 Documentation

- [Recommendation system](docs/recommendation-system.md) - the recommendation algorithms in detail
- [Distributed system architecture](docs/distributed-system-architecture.md) - the complete architecture design
- [Deployment and usage guide](docs/deployment-and-usage-guide.md) - production deployment
- [Product listing and management](docs/product-listing-and-management.md)
- [Orders and payments](docs/orders-and-payments.md)
- [Instant messaging](docs/instant-messaging.md)
- [Bargain testing guide](docs/bargain-testing-guide.md)
- [Reviews and credit scores](docs/reviews-and-credit-scores.md)
- [Reporting](docs/reporting.md)

## 🎯 Use cases

### 1. Students
- Textbooks: sell textbooks you no longer need at the end of term
- Electronics: sell old devices after upgrading
- Everyday items: clear out quickly at graduation

### 2. Community users
- Furniture and appliances: second-hand furniture when moving house
- Baby items: things the children have outgrown
- Hobbies: swap collectibles and sports equipment

### 3. Businesses
- Stock clearance: sell leftover stock and recover cash quickly
- Second-hand recycling: professional valuation at scale
- Brand promotion: show products and build trust

## 🔐 Security

- **Data encryption**: sensitive data stored with AES encryption
- **Password security**: BCrypt hashing, separate payment password
- **Abuse prevention**: Redis rate limiting against malicious requests
- **Distributed locks**: prevent overselling and keep stock consistent
- **XSS protection**: frontend input validation, backend filtering
- **CSRF protection**: token validation
- **SQL injection protection**: MyBatis parameterised queries

## 🌈 Roadmap

- [ ] **Mobile app**: React Native / Flutter
- [ ] **Payment integration**: Alipay, WeChat Pay
- [ ] **Logistics integration**: courier tracking services
- [ ] **AI customer service**: a smart Q&A bot
- [ ] **Image recognition**: classify product images automatically
- [ ] **Blockchain**: trade traceability and authenticity checks
- [ ] **Social features**: follow users and share updates
- [ ] **Live selling**: show products over live video

## 👥 Contributing

Issues and pull requests are welcome!

## 📄 License

This project is released under the MIT License

## 📧 Contact

For questions or suggestions, you can:
- Open an issue
- Send an email
- Join the discussion group

---

**⭐ If this project helps you, please give it a star!**

