package sg.edu.nus.iss.c2csectrade.service;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import sg.edu.nus.iss.c2csectrade.document.ProductDocument;
import sg.edu.nus.iss.c2csectrade.dto.SearchRequestDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.repository.ProductSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {
    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ProductService productService;

    /**
     * Sync a product to Elasticsearch (dual write)
     */
    public void indexProduct(Product product) {
        try {
            ProductDocument doc = convertToDocument(product);
            productSearchRepository.save(doc);
        } catch (Exception e) {
            // Log it without breaking the main flow
            System.err.println("Failed to index product: " + e.getMessage());
        }
    }

    /**
     * Index a product from a product.created / product.updated message
     */
    public void indexProduct(Events.ProductChangedEvent event) {
        try {
            productSearchRepository.save(convertToDocument(event));
        } catch (Exception e) {
            System.err.println("Failed to index product: " + e.getMessage());
        }
    }

    /**
     * Delete a product from Elasticsearch
     */
    public void deleteProduct(Long productId) {
        try {
            productSearchRepository.deleteById(String.valueOf(productId));
        } catch (Exception e) {
            System.err.println("Failed to delete product from ES: " + e.getMessage());
        }
    }

    /**
     * Search products
     */
    public List<ProductDocument> searchProducts(SearchRequestDTO request) {
        try {
            List<Query> mustQueries = new ArrayList<>();
            List<Query> filterQueries = new ArrayList<>();

            // 1. Full-text query (when there is a keyword)
            if (request.getKeyword() != null && !request.getKeyword().trim().isEmpty()) {
                MultiMatchQuery multiMatchQuery = MultiMatchQuery.of(m -> m
                        .query(request.getKeyword())
                        .fields("name^2", "description", "category") // name carries more weight
                        .fuzziness("AUTO"));
                mustQueries.add(Query.of(q -> q.multiMatch(multiMatchQuery)));
            }

            // 2. Price range filter
            if (request.getMinPrice() != null || request.getMaxPrice() != null) {
                // Since client 8.15 a range query is typed: number, date, term or untyped.
                NumberRangeQuery.Builder rangeBuilder = new NumberRangeQuery.Builder().field("price");
                if (request.getMinPrice() != null) {
                    rangeBuilder.gte(request.getMinPrice().doubleValue());
                }
                if (request.getMaxPrice() != null) {
                    rangeBuilder.lte(request.getMaxPrice().doubleValue());
                }
                NumberRangeQuery priceRange = rangeBuilder.build();
                filterQueries.add(Query.of(q -> q.range(r -> r.number(priceRange))));
            }

            // 3. Condition filter
            if (request.getConditionLevel() != null) {
                TermQuery termQuery = TermQuery.of(t -> t
                        .field("conditionLevel")
                        .value(request.getConditionLevel()));
                filterQueries.add(Query.of(q -> q.term(termQuery)));
            }

            // 4. Location filter
            if (request.getLocation() != null && !request.getLocation().trim().isEmpty()) {
                TermQuery locationQuery = TermQuery.of(t -> t
                        .field("location")
                        .value(request.getLocation()));
                filterQueries.add(Query.of(q -> q.term(locationQuery)));
            }

            // 5. Only products on sale
            TermQuery statusQuery = TermQuery.of(t -> t.field("status").value(1));
            filterQueries.add(Query.of(q -> q.term(statusQuery)));

            // 6. Category filter
            if (request.getCategory() != null && !request.getCategory().trim().isEmpty()) {
                String[] categories = request.getCategory().split(",");
                if (categories.length > 1) {
                    // Several categories (comma-separated): use a TermsQuery
                    List<String> categoryList = java.util.Arrays.asList(categories);
                    List<co.elastic.clients.elasticsearch._types.FieldValue> fieldValues = categoryList.stream()
                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                            .collect(Collectors.toList());

                    TermsQuery termsQuery = TermsQuery.of(t -> t
                            .field("category")
                            .terms(terms -> terms.value(fieldValues)));
                    filterQueries.add(Query.of(q -> q.terms(termsQuery)));
                } else {
                    // A single category
                    TermQuery categoryQuery = TermQuery.of(t -> t
                            .field("category")
                            .value(request.getCategory()));
                    filterQueries.add(Query.of(q -> q.term(categoryQuery)));
                }
            }

            // Build the bool query
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
            if (!mustQueries.isEmpty()) {
                boolQueryBuilder.must(mustQueries);
            }
            if (!filterQueries.isEmpty()) {
                boolQueryBuilder.filter(filterQueries);
            }

            // With no criteria at all, return every product on sale
            if (mustQueries.isEmpty() && filterQueries.size() == 1) {
                boolQueryBuilder.must(Query.of(q -> q.matchAll(MatchAllQuery.of(m -> m))));
            }

            // 6. Highlighting
            HighlightParameters highlightParameters = HighlightParameters.builder()
                    .withPreTags("<em class='highlight'>")
                    .withPostTags("</em>")
                    .build();

            List<HighlightField> highlightFields = new ArrayList<>();
            highlightFields.add(new HighlightField("name"));
            highlightFields.add(new HighlightField("description"));

            Highlight highlight = new Highlight(highlightParameters, highlightFields);

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.bool(boolQueryBuilder.build())))
                    .withPageable(PageRequest.of(request.getPage(), request.getSize()))
                    .withHighlightQuery(new HighlightQuery(highlight, ProductDocument.class))
                    .build();

            SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(searchQuery, ProductDocument.class);

            return searchHits.getSearchHits().stream()
                    .map(hit -> {
                        ProductDocument doc = hit.getContent();
                        List<String> nameHighlights = hit.getHighlightField("name");
                        if (nameHighlights != null && !nameHighlights.isEmpty()) {
                            doc.setHighlightedName(nameHighlights.get(0));
                        } else {
                            doc.setHighlightedName(doc.getName());
                        }

                        List<String> descHighlights = hit.getHighlightField("description");
                        if (descHighlights != null && !descHighlights.isEmpty()) {
                            doc.setHighlightedDescription(descHighlights.get(0));
                        } else {
                            doc.setHighlightedDescription(doc.getDescription());
                        }
                        return doc;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Search error: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Simple keyword search (for the recommender)
     * 
     * @param keyword search keyword
     * @param limit   maximum number of results
     * @return product documents
     */
    public List<ProductDocument> searchProductsByKeyword(String keyword, int limit) {
        try {
            SearchRequestDTO request = new SearchRequestDTO();
            request.setKeyword(keyword);
            request.setPage(0);
            request.setSize(limit);

            return searchProducts(request);
        } catch (Exception e) {
            System.err.println("Keyword search error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Search products by category (for the recommender)
     * 
     * @param category product category
     * @param limit    maximum number of results
     * @return product documents
     */
    public List<ProductDocument> searchProductsByCategory(String category, int limit) {
        try {
            List<Query> filterQueries = new ArrayList<>();

            // Category filter
            TermQuery categoryQuery = TermQuery.of(t -> t
                    .field("category")
                    .value(category));
            filterQueries.add(Query.of(q -> q.term(categoryQuery)));

            // Only products on sale
            TermQuery statusQuery = TermQuery.of(t -> t.field("status").value(1));
            filterQueries.add(Query.of(q -> q.term(statusQuery)));

            BoolQuery boolQuery = BoolQuery.of(b -> b.filter(filterQueries));

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(Query.of(q -> q.bool(boolQuery)))
                    .withPageable(PageRequest.of(0, limit))
                    .build();

            SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(searchQuery, ProductDocument.class);

            return searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Category search error: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Convert a Product entity into an Elasticsearch document
     */
    private ProductDocument convertToDocument(Product product) {
        ProductDocument doc = new ProductDocument();
        doc.setId(String.valueOf(product.getId()));
        doc.setProductId(Long.valueOf(product.getId()));
        doc.setUserId(product.getUserId());
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setPrice(product.getPrice());
        doc.setConditionLevel(product.getConditionLevel());
        doc.setLocation(product.getLocation());
        doc.setStatus(product.getStatus());
        doc.setCreatedAt(product.getCreatedAt());
        doc.setCategory(product.getCategory());
        return doc;
    }

    private ProductDocument convertToDocument(Events.ProductChangedEvent event) {
        ProductDocument doc = new ProductDocument();
        doc.setId(String.valueOf(event.getProductId()));
        doc.setProductId(event.getProductId());
        doc.setUserId(event.getUserId());
        doc.setName(event.getName());
        doc.setDescription(event.getDescription());
        doc.setPrice(event.getPrice());
        doc.setConditionLevel(event.getConditionLevel());
        doc.setLocation(event.getLocation());
        doc.setStatus(event.getStatus());
        doc.setCreatedAt(event.getCreatedAt());
        doc.setCategory(event.getCategory());
        return doc;
    }

    /**
     * Full sync: index every product in ES
     */
    public void syncAllProducts() {
        try {
            List<Product> allProducts = productService.listAllProducts();
            List<ProductDocument> documents = allProducts.stream()
                    .map(this::convertToDocument)
                    .collect(Collectors.toList());
            productSearchRepository.saveAll(documents);
            System.out.println("Synced " + documents.size() + " products to Elasticsearch");
        } catch (Exception e) {
            System.err.println("Failed to sync products: " + e.getMessage());
        }
    }
}
