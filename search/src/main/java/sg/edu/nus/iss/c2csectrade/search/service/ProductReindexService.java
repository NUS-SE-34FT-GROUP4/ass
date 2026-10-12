package sg.edu.nus.iss.c2csectrade.search.service;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rebuilds the whole index from pms_product. Product events keep the index up
 * to date day to day, but an event is dropped after its retries, and a new or
 * emptied index has nothing in it; this is the way back to a correct index.
 */
@Service
public class ProductReindexService {

    private static final Logger log = LoggerFactory.getLogger(ProductReindexService.class);
    static final int BATCH_SIZE = 500;
    static final int MAX_STALE = 10_000;

    private final ProductCatalog catalog;
    private final ProductIndexService indexService;
    private final OpenSearchClient client;
    private final String index;
    private final AtomicBoolean running = new AtomicBoolean();

    public ProductReindexService(ProductCatalog catalog, ProductIndexService indexService,
                                 OpenSearchClient client, @Value("${app.opensearch.index}") String index) {
        this.catalog = catalog;
        this.indexService = indexService;
        this.client = client;
        this.index = index;
    }

    /**
     * Creates the index at startup, and fills it if it was just created.
     * OpenSearch or MySQL may not be reachable yet, so a failure here only
     * logs; the first write creates the index, and an administrator can rebuild.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexAtStartup() {
        try {
            if (indexService.ensureIndex()) {
                rebuild();
            }
        } catch (Exception e) {
            log.warn("Could not prepare index {} at startup: {}", index, e.getMessage());
        }
    }

    /**
     * Writes every product to the index, then removes documents whose product
     * no longer exists.
     *
     * @throws ReindexInProgressException if a rebuild is already running here
     */
    public Result rebuild() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new ReindexInProgressException();
        }
        try {
            indexService.ensureIndex();
            List<Events.ProductChangedEvent> products = catalog.findAll();
            for (int from = 0; from < products.size(); from += BATCH_SIZE) {
                write(products.subList(from, Math.min(from + BATCH_SIZE, products.size())));
            }
            long removed = removeMissing(products);
            log.info("Rebuilt index {}: {} products indexed, {} stale documents removed",
                    index, products.size(), removed);
            return new Result(products.size(), removed);
        } finally {
            running.set(false);
        }
    }

    private void write(List<Events.ProductChangedEvent> batch) throws IOException {
        BulkRequest.Builder bulk = new BulkRequest.Builder().index(index);
        for (Events.ProductChangedEvent product : batch) {
            ProductDocument doc = ProductIndexService.toDocument(product);
            bulk.operations(op -> op.index(i -> i.id(doc.getId()).document(doc)));
        }
        BulkResponse response = client.bulk(bulk.build());
        if (response.errors()) {
            BulkResponseItem failed = response.items().stream()
                    .filter(item -> item.error() != null)
                    .findFirst()
                    .orElseThrow();
            throw new IOException("Indexing product " + failed.id() + " failed: " + failed.error().reason());
        }
    }

    /**
     * Deletes documents whose product is not in the database. A document
     * missing from the rows just read may belong to a product created while
     * the rebuild ran (indexed from its event), so the database is asked again
     * before anything is deleted.
     */
    private long removeMissing(List<Events.ProductChangedEvent> products) throws IOException {
        List<String> read = products.stream().map(p -> String.valueOf(p.getProductId())).toList();
        client.indices().refresh(r -> r.index(index));
        List<Long> candidates = client.search(notInRequest(read), Void.class).hits().hits().stream()
                .map(hit -> Long.valueOf(hit.id()))
                .toList();
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<Long> stillThere = catalog.existingIds(candidates);
        List<Long> stale = candidates.stream().filter(id -> !stillThere.contains(id)).toList();
        if (stale.isEmpty()) {
            return 0;
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder().index(index);
        stale.forEach(id -> bulk.operations(op -> op.delete(d -> d.id(String.valueOf(id)))));
        client.bulk(bulk.build());
        return stale.size();
    }

    /** Ids of documents not among the given ones; stale documents are few, one page is enough. */
    SearchRequest notInRequest(List<String> ids) {
        return SearchRequest.of(s -> s
                .index(index)
                .query(q -> q.bool(b -> b.mustNot(m -> m.ids(i -> i.values(ids)))))
                .source(src -> src.fetch(false))
                .size(MAX_STALE));
    }

    public record Result(int indexed, long removed) {
    }

    public static class ReindexInProgressException extends RuntimeException {
        public ReindexInProgressException() {
            super("A rebuild of the search index is already running");
        }
    }
}
