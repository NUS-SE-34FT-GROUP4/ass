# C2C SecTrade - Smart Second-hand Marketplace

C2C SecTrade is a full-featured consumer-to-consumer (C2C) second-hand marketplace built on a **distributed architecture** with an **intelligent recommendation system**, giving users a safe, convenient and smart way to trade items they no longer need.

The platform covers more than ten categories, including **electronics, clothing and bags, books and media, home and living, sports and outdoors, beauty and personal care, baby and kids, and food and health**. It uses a modern separated frontend/backend architecture, integrates distributed components such as **Redis caching, RabbitMQ messaging and Elasticsearch full-text search**, and offers **instant messaging, smart recommendations, bargaining, credit ratings and real-time notifications**.

## 🌟 Highlights

- **🤖 Smart recommendations**: combines collaborative filtering, neural collaborative filtering (NCF) and real-time recommendation
- **📊 A/B testing framework**: data-driven algorithm tuning with multi-variant experiments
- **⚡ Distributed architecture**: Redis caching, RabbitMQ messaging, Elasticsearch search (with keyword highlighting)
- **🎯 Multi-objective optimisation**: balances click-through rate, conversion rate, revenue, diversity and novelty
- **💬 Real-time messaging**: WebSocket chat and system message push
- **🔨 Bargaining**: social commerce where friends help cut the price
- **⭐ Credit system**: reviews and automatic credit score calculation

## ✨ Features

- **Users**: registration, login, JWT authentication, and back-office user management (ban/unban).
- **Products**: publish, edit and delist items, with multi-image upload stored in MinIO object storage.
- **Home and browsing**: a feed of items on sale, searchable by category, location and keyword.
- **Social trading**:
  - **Bargaining**: users start a bargain on an item and invite friends to help lower the price.
  - **Instant messaging**: buyers and sellers chat in real time over WebSocket before buying.
- **End-to-end purchasing**:
  - **Cart**: add, remove and change quantities, then check out in one go.
  - **Orders**: create orders from the cart or a product page; orders with several sellers are split automatically.
  - **Simulated payment**: built-in account balance and payment password for a realistic payment flow.
  - **Order center**:
    - **Bought**: track every order as a buyer, with pay, confirm receipt and review actions
    - **Sold**: see every order as a seller in real time
    - **Bargains**: manage the bargains you started or helped
- **Credit and reviews**:
  - **Buyer reviews**: after a trade, buyers rate and comment on the product and seller, with up to 5 images and an anonymous option.
  - **Credit score**: the system calculates each seller's score and level from trade volume, positive rate and more.
- **Personal features**:
  - **Favorites**: save and follow items you are interested in.
  - **Reports**: users report items or users that break the rules; administrators review them.
  - **Related items**: the product page recommends similar items by category.
- **Administration**: user management, report management and system messages.

## 📸 Screenshots

Screenshots of the user interface and core features:

### 1. Home page
![Home page](images/1.png)

### 2. Publishing and managing products
<img src="images/2.png" width="45%" /> <img src="images/3.png" width="45%" />
<img src="images/4.png" width="45%" /> <img src="images/5.png" width="45%" />

### 3. Product details
<img src="images/6.png" width="45%" /> <img src="images/7.png" width="45%" />

### 4. Orders and purchasing
<img src="images/9.png" width="45%" /> <img src="images/10.png" width="45%" />

### 5. More features
<details>
<summary>Click to see more screenshots</summary>

|  |  |
|:---:|:---:|
| ![11](images/11.png) | ![12](images/12.png) |
| ![13](images/13.png) | ![14](images/14.png) |
| ![15](images/15.png) | ![16](images/16.png) |
| ![17](images/17.png) | ![18](images/18.png) |
| ![19](images/19.png) | ![20](images/20.png) |

</details>

### Backend
- **Framework**: Spring Boot 3.2.3
- **Authentication and security**: Spring Security + JWT
- **Database**: MySQL 8.0 + MyBatis
- **Distributed cache**: Redis 7 (sessions, distributed locks, recommendation cache)
- **Messaging**: RabbitMQ 3.12 (event-driven architecture)
- **Search**: Elasticsearch 8.11 (full-text search)
- **Object storage**: MinIO (images and videos)
- **Real-time communication**: Spring WebSocket + STOMP
- **Build**: Maven

### Frontend
- **Framework**: Vue.js 3.x
- **Routing**: Vue Router 4.x
- **State management**: Pinia
- **HTTP client**: Axios
- **UI**: custom components + responsive CSS + SweetAlert2 notifications
- **Real-time communication**: SockJS + StompJS

### Recommendation system

The platform implements a **multi-layer hybrid recommender** that aims to give users accurate, real-time and varied recommendations. It combines several algorithms and is tuned continuously through an A/B testing framework.

- **Multi-layer architecture**:
  - **Item-based collaborative filtering**: uses browsing history to compute item similarity from a co-occurrence matrix and cosine similarity.
  - **Content-based recommendation**: uses TF-IDF and cosine similarity over product name, description, category and price.
  - **Popularity ranking**: combines views, favorites, add-to-cart and purchases with time decay to rank items by popularity.

- **Advanced models**:
  - **Neural collaborative filtering (NCF)**: a deep learning model combining an embedding layer, GMF and MLP to learn non-linear user-item relationships, with online incremental learning.
  - **Real-time recommendation**: a 5-minute sliding window captures in-session interest and site-wide trends for session-aware recommendations.

- **System-level optimisation**:
  - **A/B testing**: multi-variant experiments with dynamic traffic allocation and significance testing to pick the best strategy.
  - **Multi-objective optimisation (MTO)**: ranking balances **click-through rate (CTR), conversion rate (CVR), revenue, diversity and novelty**.
  - **Diversity re-ranking (MMR)**: Maximal Marginal Relevance keeps the final list both relevant and varied, avoiding filter bubbles.

- **Performance**:
  - **Three-level cache**: Redis caches for hot data, recommendation results and model parameters, with P99 response time under 200 ms.
  - **Asynchronous and distributed processing**: behaviour tracking, recommendation computation and metrics collection run through RabbitMQ; the core algorithms support offline batch, online incremental and streaming computation.
  - **Redis integration**: distributed locks, Bloom filters and rate limiting keep the system stable and consistent under high concurrency.

### DevOps
- **Containers**: Docker + Docker Compose
- **Web server**: Nginx (serves the frontend and reverse-proxies the API)
- **Monitoring**: Spring Actuator
- **Logging**: SLF4J + Logback

## 🏛️ Architecture

The project uses a classic separated frontend/backend architecture:

1.  **Browser**: users open the single-page application (SPA) built with Vue.js.
2.  **Nginx**: hosts the frontend static files and reverse-proxies every request under `/api` to the Spring Boot backend.
3.  **Frontend (Vue.js)**: renders pages, handles user interaction and calls the backend API.
4.  **Backend (Spring Boot)**: business logic, database access, the RESTful API and WebSocket connections.
5.  **Data layer**:
    - **MySQL**: core business data such as users, products and orders.
    - **MinIO**: media files such as product images and avatars.

## ⚡ Quick Start

Docker Compose starts every service with one command, which is the quickest way to run the project.

### Requirements
- [Docker](https://www.docker.com/get-started/) and [Docker Compose](https://docs.docker.com/compose/install/)
- [Java 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) (for local builds)
- [Maven 3.8+](https://maven.apache.org/download.cgi) (for local builds)
- [Node.js 16+](https://nodejs.org/) (for local builds)

### Deployment

#### Option 1: Makefile (recommended)

The Makefile builds and deploys everything in one step:

```bash
# Clone the repository
git clone https://github.com/NUS-SE-34FT-GROUP4/ass.git
cd ass

# Create .env with a JWT signing key (it is not committed)
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')" > .env

# Build the backend and frontend and start every service
make up

# Or step by step
make build-backend    # Build the backend
make build-frontend   # Build the frontend
make up               # Start all services

# View logs
make logs

# Stop the services
make down

# Restart the services
make restart
```

#### Option 2: Build and deploy manually

1.  **Clone the repository**
    ```bash
    git clone https://github.com/NUS-SE-34FT-GROUP4/ass.git
    cd ass
    ```

2.  **Build the project**
    -   **Backend (Spring Boot)**
        ```bash
        mvn clean package -DskipTests
        ```
        This builds every module. Each service jar lands in its own module, for example `core/target/c2c-core-0.0.1-SNAPSHOT.jar`; `search` and `chat` follow the same pattern.

    -   **Frontend (Vue.js)**
        ```bash
        cd frontend
        npm install
        npm run build
        cd ..
        ```
        This produces the production static files in `frontend/dist`.

3.  **Start the services**
    Create `.env` with a JWT signing key, then start everything with Docker Compose. Running the backend outside Docker needs `JWT_SECRET` exported in the shell instead.
    ```bash
    echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')" > .env
    ```
    ```bash
    docker compose up --build -d
    ```

4.  **Open the application**
    -   **Frontend**: [http://localhost](http://localhost) (port 80)
    -   **Backend API**: [http://localhost/api](http://localhost/api)
    -   **MinIO console**: [http://localhost:9001](http://localhost:9001)
        - AccessKey: `minioadmin`
        - SecretKey: `minioadmin`
    -   **RabbitMQ management**: [http://localhost:15672](http://localhost:15672)
        - Username: `admin`
        - Password: `admin123`

### Default accounts

The database is seeded with these test accounts:

| Role | Username | Password | Notes |
|------|--------|------|------|
| Administrator | admin | admin123 | Full administrative access |
| Seller (Lv1) | seller_lvl1 | admin123 | Credit score 100, level 1 |
| Seller (Lv2) | seller_lvl2 | admin123 | Credit score 300, level 2 |
| Seller (Lv3) | seller_lvl3 | admin123 | Credit score 600, level 3 |
| Seller (Lv4) | seller_lvl4 | admin123 | Credit score 1000, level 4 |
| Seller (Lv5) | seller_lvl5 | admin123 | Credit score 2000, level 5 |

## 📚 Documentation

Each core module has its own Markdown document in `docs/` describing its design and implementation:

- [Project overview](docs/project-overview.md)
- [Docker configuration guide](docs/docker-configuration-guide.md)
- [Distributed system architecture](docs/distributed-system-architecture.md)
- [Deployment and usage guide](docs/deployment-and-usage-guide.md)
- [User authentication and management](docs/user-authentication-and-management.md)
- [Product listing and management](docs/product-listing-and-management.md)
- [Shopping cart](docs/shopping-cart.md)
- [Favorites](docs/favorites.md)
- [Orders and payments](docs/orders-and-payments.md)
- [Instant messaging](docs/instant-messaging.md)
- [Reporting](docs/reporting.md)
- [Recommendation system](docs/recommendation-system.md)
- [Reviews and credit scores](docs/reviews-and-credit-scores.md)
- [Bargain testing guide](docs/bargain-testing-guide.md)
- [Innovations and highlights](docs/innovations-and-highlights.md)

## License

This project is released under the [MIT License](LICENSE).
