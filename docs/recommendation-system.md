# c2csectrade Smart Recommendation System

## System overview

c2csectrade implements an **enterprise-grade multi-layer hybrid recommendation system** for every kind of item on a second-hand marketplace (electronics, clothing, books, home goods and more). It combines collaborative filtering, content-based recommendation, neural networks, real-time recommendation and popularity ranking to give users accurate personalised recommendations.

## Architecture

### 1. Three-layer recommendation architecture

#### 1.1 Collaborative filtering
- **Class**: `RecommendationEngineService`
- **Algorithm**: Item-Based Collaborative Filtering
- **How it works**: builds an item co-occurrence matrix from user browsing history and computes item similarity with cosine similarity
- **Weight**: 40%

**Computation**:
```
1. Collect user browsing history (Redis Sorted Set)
2. Build the item co-occurrence matrix
3. Compute cosine similarity: similarity = co-occurrence / sqrt(total1 * total2)
4. Store the top 10 similar items in Redis (recommend:item-cf:{productId})
```

#### 1.2 Content-based recommendation
- **Class**: `ContentBasedRecommendationService`
- **Algorithm**: TF-IDF + cosine similarity
- **Features**: product name, description, category, price range, condition, location
- **Weight**: 40%

**Feature extraction**:
- **Category** (high weight): `cat:category`
- **Product name** (tokenised + bigrams): `name:word`
- **Description**: `desc:word`
- **Price range**: `price:0-20`, `price:20-50`, `price:50-100` and so on
- **Condition**: `condition:1-10`
- **Location**: `loc:location`

**Similarity calculation**:
```
1. TF-IDF vectorisation
   - TF = term_freq / total_terms
   - IDF = log(total_docs / doc_freq)
   - TF-IDF = TF * IDF

2. Cosine similarity
   - cosine_sim = dot_product / (norm1 * norm2)

3. Price similarity (exponential decay)
   - price_sim = exp(-relative_diff * 2)

4. Combined score
   - final_score = 0.8 * cosine_sim + 0.2 * price_sim
```

#### 1.3 Popularity ranking
- **Class**: `HybridRecommendationService`
- **Data structure**: Redis Sorted Set
- **Weight**: 20%

**Behaviour weights**:
- View: 1.0
- Favorite: 3.0
- Add to cart: 5.0
- Review: 8.0
- Purchase (order): 10.0

**Popularity decay**: scores decay 10% a day so old items do not stay on top forever

### 2. Hybrid strategy

**Implementation**: `HybridRecommendationService.getHybridSimilarProducts()`

```
Combined score = 0.4 * CF score + 0.4 * content score + 0.2 * popularity score
```

## Data flow

### 1. User behaviour tracking

#### 1.1 Browsing history
```java
// Redis Key: history:user:{userId}
// Data Structure: Sorted Set (score = timestamp)
historyService.recordView(userId, productId);
```

#### 1.2 User interest profile
```java
// Redis Key: profile:user:{userId}
// Data Structure: Hash (tag -> interest_score)
// Tags are extracted from browsing history automatically and their interest scores accumulated
```

### 2. Recommendation cache

#### 2.1 Collaborative filtering cache
```
Key: recommend:item-cf:{productId}
Type: Sorted Set
Score: similarity score
Value: similar product ID
TTL: permanent (updated daily)
```

#### 2.2 Content similarity cache
```
Key: recommend:content:{productId}
Type: Sorted Set
Score: similarity score
Value: similar product ID
TTL: 7 days
```

#### 2.3 Popularity cache
```
Key: recommend:popularity
Type: Sorted Set
Score: popularity score
Value: product ID
TTL: permanent (real-time updates + daily decay)
```

## API

### 1. You may also like
```http
GET /api/recommendations/for-you?limit=10
```

**Logic**:
1. Get the user's interest profile (top 3 tags)
2. Search for products by those tags
3. Filter out items already viewed and the user's own items
4. Return personalised recommendations

**Signed-out users**: popular products are returned

### 2. Similar products
```http
GET /api/recommendations/products/{id}/similar?limit=10
```

**Logic**:
1. Get similar product IDs with the hybrid algorithm
2. Load product details and media
3. Filter out unavailable products
4. If there are too few results, add products from the same category

### 3. Trigger computation manually (administrator)
```http
POST /api/recommendations/compute
Authorization: Admin
```

## Scheduled jobs

### Computed automatically at 3 a.m. every day
```java
@Scheduled(cron = "0 0 3 * * ?")
public void computeRecommendations()
```

**Steps**:
1. Collaborative filtering (5-10 minutes)
2. Content similarity (10-20 minutes)
3. Popularity decay (1 minute)

## Performance

### 1. Offline computation
- Recommendations are computed by scheduled background jobs
- Online service performance is unaffected

### 2. Redis cache
- Every recommendation result is precomputed and cached
- Queries read directly from Redis with latency < 10ms

### 3. Asynchronous processing
- Behaviour tracking does not block the main flow
- Processed asynchronously through the message queue

### 4. Batch operations
- Product details are loaded in batches
- Fewer database queries

## Frontend integration

### 1. Home page recommendations
```vue
<RecommendationSection
  title="🎯 You May Also Like"
  subtitle="Based on your browsing history"
  :products="recommendedProducts"
/>
```

### 2. Product page
```vue
<RecommendationSection
  title="👀 Similar Items"
  subtitle="Other users also viewed these products"
  :products="similarProducts"
/>
```

### 3. Automatic loading
```javascript
// When the product page is mounted
onMounted(() => {
  trackProductView();      // Track the view
  fetchSimilarProducts();  // Load recommendations
});
```

## Extensibility

### 1. Adding a recommendation algorithm
Extend `HybridRecommendationService` and implement a new scoring strategy:
```java
public Map<Long, Double> getNewAlgorithmScores(Long productId) {
    // Implement the new algorithm
}
```

### 2. Adjusting weights
Change the constants in `HybridRecommendationService`:
```java
private static final double CF_WEIGHT = 0.4;
private static final double CONTENT_WEIGHT = 0.4;
private static final double POPULARITY_WEIGHT = 0.2;
```

### 3. Adding features
Add them in `ContentBasedRecommendationService.extractTerms()`:
```java
// Add a brand feature
if (product.getBrand() != null) {
    terms.add("brand:" + product.getBrand());
}
```

## Metrics

### 1. Coverage
- Share of products with recommendations
- Target: > 80%

### 2. Accuracy
- Click-through rate (CTR)
- Conversion rate (CVR)

### 3. Computation performance
- Collaborative filtering computation time
- Content similarity computation time

### 4. Cache hit rate
- Redis cache hit rate
- Target: > 95%

## Known limitations

1. **Cold start**: new products and new users have no history
   - Mitigation: use content-based recommendations and popular products

2. **Compute resources**: a large catalogue takes a long time to compute
   - Mitigation: incremental and distributed computation

3. **Freshness**: recommendations are updated once a day
   - Mitigation: add a real-time stream processing framework

## Advanced features

### 1. Deep learning model - Neural Collaborative Filtering (NCF) ✅

**Class**: `NCFRecommendationService`

**Architecture**:
- **Embedding layer**: vector representations of users and items (32 dimensions)
- **GMF part**: generalised matrix factorisation (element-wise product)
- **MLP part**: multi-layer perceptron (feature learning)
- **Fusion layer**: NeuMF combines GMF and MLP

**Core methods**:
```java
// Predict the user-item interaction score
double score = ncfService.predictScore(userId, productId);

// Online learning: update the model from user behaviour
ncfService.trainOnInteraction(userId, productId, "order");

// Get NCF recommendations
List<Long> recommendations = ncfService.getNCFRecommendations(
    userId, candidateProducts, 10
);
```

**Advantages**:
- Learns non-linear features
- Online incremental learning
- Highly personalised

### 2. Real-time recommendation ✅

**Class**: `RealtimeRecommendationService`

**Real-time capabilities**:
- User session tracking (last 20 interactions)
- 5-minute sliding time window
- Trending items computed in real time
- Instant personalised scoring

**Time-window aggregation**:
```java
// Get real-time trending items
List<Long> trending = realtimeService.getTrendingProducts(30, 20);

// Get real-time personalised recommendations
List<Long> recommendations = realtimeService.getRealtimeRecommendations(userId, 10);

// Handle a user interaction
realtimeService.processInteraction(userId, productId, "view");
```

**Characteristics**:
- Millisecond responses
- Session-aware recommendations
- Time decay
- Captures hot items

### 3. A/B testing framework ✅

**Class**: `ABTestService`

**Supported tests**:
- Multi-variant experiments (2-10 variants)
- Traffic allocation control
- Consistent-hash assignment
- Statistical significance testing

**Example**:
```java
// Create an experiment
Map<String, Double> variants = Map.of(
    "control", 0.2,      // CF algorithm
    "variant_a", 0.2,    // Content-based
    "variant_b", 0.2,    // Hybrid
    "variant_c", 0.2,    // NCF neural network
    "variant_d", 0.2     // Real-time
);
abTestService.createExperiment("rec_algo_v1", "Recommendation algorithm comparison", variants);

// Assign a user to a variant
String variant = abTestService.assignUserToVariant(userId, "rec_algo_v1");

// Track metrics
abTestService.trackClick(userId, experimentId, productId);
abTestService.trackConversion(userId, experimentId, productId, 99.99);

// Get the experiment results
Map<String, Map<String, Object>> results = abTestService.getExperimentResults("rec_algo_v1");
```

**Metrics tracked**:
- Impressions
- Click-through rate (CTR)
- Conversion rate (CVR)
- Revenue
- Significance test (Chi-square)

### 4. Multi-objective optimisation ✅

**Class**: `MultiObjectiveOptimizationService`

**Objectives**:
1. **Click-through rate (CTR)** - 25% weight
2. **Conversion rate (CVR)** - 30% weight
3. **Revenue** - 20% weight
4. **Diversity** - 15% weight
5. **Novelty** - 10% weight

**Core algorithms**:
```java
// Multi-objective optimised recommendations
List<Long> optimized = multiObjectiveService.getMultiObjectiveRecommendations(
    userId, candidates, 10
);

// MMR diversity re-ranking
List<Long> diverse = multiObjectiveService.rerankForDiversity(
    recommendations, 0.7  // lambda parameter
);

// Pareto front (non-dominated solutions)
List<ScoredCandidate> paretoFrontier = 
    multiObjectiveService.getParetoFrontier(candidates);
```

**Implementation**:
- **Weighted scoring**: a linear combination of the objectives
- **MMR re-ranking**: balances relevance and diversity
- **Pareto optimisation**: finds the set of non-dominated solutions

### 5. Redis integration ✅

**Class**: `RedisCacheService`

**Advanced features**:
```java
// Distributed lock
String lockId = UUID.randomUUID().toString();
if (redisCacheService.acquireLock("product:123", lockId, 10)) {
    try {
        // Critical section
    } finally {
        redisCacheService.releaseLock("product:123", lockId);
    }
}

// Hot item cache
redisCacheService.updateHotProducts(productId, score);
List<Long> hotProducts = redisCacheService.getHotProducts(20);

// Batch operations (pipeline)
Map<String, Object> batch = Map.of(
    "product:1", product1,
    "product:2", product2
);
redisCacheService.batchSet(batch, 60);

// Bloom filter (deduplication)
redisCacheService.bloomAdd("viewed:products", productId.toString());
boolean seen = redisCacheService.bloomContains("viewed:products", productId.toString());

// Rate limiting
Long count = redisCacheService.incrementWithExpire("ratelimit:user:" + userId, 60);
if (count > 100) {
    throw new RateLimitException("Too many requests");
}
```

## System integration architecture

```
User request
   ↓
A/B test split
   ↓
┌──────────────────────────────────────┐
│  Strategy routing                    │
│  - Control: collaborative filtering  │
│  - Variant A: content-based          │
│  - Variant B: hybrid                 │
│  - Variant C: NCF neural network     │
│  - Variant D: real-time              │
└──────────────────────────────────────┘
   ↓
Candidate generation
   ↓
Multi-objective optimisation
   ↓
MMR re-ranking (diversity)
   ↓
Return results + track metrics
```

## Performance strategy

### Layered Redis cache
1. **L1 cache** - hot data (TTL: 5 minutes)
2. **L2 cache** - recommendation results (TTL: 1 hour)
3. **L3 cache** - model parameters (TTL: 1 day)

### Asynchronous processing
- User behaviour tracking → RabbitMQ → asynchronous model updates
- Recommendation computation → scheduled background jobs
- Metrics collection → message queue → batch inserts

### Distributed computation
- Collaborative filtering: offline batch
- NCF training: online incremental learning
- Real-time recommendation: stream processing

## Monitoring metrics

### Business metrics
- **CTR**: click-through rate = clicks / impressions
- **CVR**: conversion rate = orders / clicks
- **GMV**: gross merchandise value
- **User satisfaction**: ratings, time on page

### Technical metrics
- **Response time**: P50 < 50ms, P99 < 200ms
- **Cache hit rate**: > 95%
- **Coverage**: > 85%
- **Diversity**: ILD (Intra-List Diversity)

### A/B test metrics
- **Relative lift**: (variant metric - control metric) / control metric
- **Statistical significance**: p-value < 0.05
- **Sample size**: > 1000 per group

## API reference

### 1. Get personalised recommendations
```http
GET /api/recommendations/for-you?limit=10
Authorization: Bearer <token>

Response:
{
  "products": [...],
  "strategy": "ncf_neural",
  "experiment": "rec_algo_v1"
}
```

### 2. Get similar products
```http
GET /api/recommendations/products/123/similar?limit=10

Response:
{
  "products": [...],
  "algorithm": "hybrid",
  "diversity_score": 0.82
}
```

### 3. Get real-time trends
```http
GET /api/recommendations/trending?minutes=30&limit=20

Response:
{
  "products": [...],
  "window": "30 minutes",
  "updated_at": "2025-01-15T10:30:00Z"
}
```

## Search and highlighting

The platform integrates the Elasticsearch full-text search engine and highlights matching keywords to improve the search experience.

### 1. Implementation
- **Search engine**: Elasticsearch 8.11
- **Integration**: Spring Data Elasticsearch
- **Highlighting**: Elasticsearch's highlighting feature with the custom tag `<em class='highlight'>`.

### 2. Backend (`SearchService`)
The `searchProducts` method configures highlighting when it builds the NativeQuery:
```java
HighlightFieldParameters parameters = HighlightFieldParameters.builder()
    .withPreTags(new String[]{"<em class='highlight'>"})
    .withPostTags(new String[]{"</em>"})
    .build();

Highlight highlight = new Highlight(Arrays.asList(
    new HighlightField("name", parameters),
    new HighlightField("description", parameters)
));
```
When results come back, the highlighted fragments are mapped to the `highlightedName` and `highlightedDescription` fields of `ProductDocument`.

### 3. Frontend display
The frontend renders the highlighted HTML with the `v-html` directive and styles it with CSS:
```css
:deep(.highlight) {
  color: #e74c3c;
  font-weight: bold;
  background-color: rgba(231, 76, 60, 0.1);
}
```

## Future directions

1. **Cross-domain recommendation**: combine social relationships and community recommendations
2. **Sequential recommendation**: Transformer-based session recommendation
3. **Knowledge graph**: enrich recommendations with a product knowledge graph
4. **Reinforcement learning**: optimise long-term user value with RL
5. **Federated learning**: privacy-preserving distributed model training

