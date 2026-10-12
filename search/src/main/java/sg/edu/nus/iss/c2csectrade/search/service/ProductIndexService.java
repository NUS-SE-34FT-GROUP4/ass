package sg.edu.nus.iss.c2csectrade.search.service;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;

import java.io.IOException;

/**
 * Writes product changes to the index. Failures are thrown, not logged and
 * dropped, so the listener retries the message (spring.rabbitmq.listener.simple.retry).
 */
@Service
public class ProductIndexService {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexService.class);

    // Same patterns as the mapping Spring Data Elasticsearch created in Core,
    // so an index Core created keeps working
    static final String CREATED_AT_FORMAT = "yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd HH:mm:ss"
            + "||yyyy-MM-dd'T'HH:mm:ss.SSSX||yyyy-MM-dd'T'HH:mm:ss.SSS||yyyy-MM-dd||epoch_millis";

    private final OpenSearchClient client;
    private final String index;
    private volatile boolean indexReady;

    public ProductIndexService(OpenSearchClient client, @Value("${app.opensearch.index}") String index) {
        this.client = client;
        this.index = index;
    }

    public void index(Events.ProductChangedEvent event) throws IOException {
        ensureIndex();
        ProductDocument doc = toDocument(event);
        client.index(i -> i.index(index).id(doc.getId()).document(doc));
    }

    /** Deleting a product that is not in the index is not an error. */
    public void delete(Long productId) throws IOException {
        ensureIndex();
        client.delete(d -> d.index(index).id(String.valueOf(productId)));
    }

    /** Creates the index if it does not exist; returns true if it was created now. */
    boolean ensureIndex() throws IOException {
        if (indexReady) {
            return false;
        }
        boolean created = false;
        if (!client.indices().exists(e -> e.index(index)).value()) {
            client.indices().create(c -> c.index(index).mappings(mapping()));
            log.info("Created index {}", index);
            created = true;
        }
        indexReady = true;
        return created;
    }

    static TypeMapping mapping() {
        return TypeMapping.of(m -> m
                .properties("productId", p -> p.long_(l -> l))
                .properties("userId", p -> p.long_(l -> l))
                .properties("name", p -> p.text(t -> t.analyzer("standard")))
                .properties("description", p -> p.text(t -> t.analyzer("standard")))
                .properties("price", p -> p.double_(d -> d))
                .properties("conditionLevel", p -> p.integer(i -> i))
                .properties("location", p -> p.keyword(k -> k))
                .properties("status", p -> p.integer(i -> i))
                .properties("createdAt", p -> p.date(d -> d.format(CREATED_AT_FORMAT)))
                .properties("category", p -> p.keyword(k -> k)));
    }

    static ProductDocument toDocument(Events.ProductChangedEvent event) {
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
}
