package sg.edu.nus.iss.c2csectrade.search.service;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Keyword search with filters and highlighting, ported from Core's
 * SearchService. Errors are thrown so that callers can tell "no results"
 * from "search is down".
 */
@Service
public class ProductSearchService {

    static final String PRE_TAG = "<em class='highlight'>";
    static final String POST_TAG = "</em>";
    static final int ON_SALE = 1;

    private final OpenSearchClient client;
    private final String index;

    public ProductSearchService(OpenSearchClient client, @Value("${app.opensearch.index}") String index) {
        this.client = client;
        this.index = index;
    }

    public List<ProductDocument> search(ProductSearchQuery query) throws IOException {
        return client.search(request(query), ProductDocument.class).hits().hits().stream()
                .map(ProductSearchService::withHighlights)
                .toList();
    }

    SearchRequest request(ProductSearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (hasText(query.keyword())) {
            // The name weighs double; AUTO fuzziness tolerates typos
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(query.keyword())
                    .fields("name^2", "description", "category")
                    .fuzziness("AUTO")));
        }
        if (query.minPrice() != null || query.maxPrice() != null) {
            bool.filter(f -> f.range(r -> {
                r.field("price");
                if (query.minPrice() != null) {
                    r.gte(JsonData.of(query.minPrice().doubleValue()));
                }
                if (query.maxPrice() != null) {
                    r.lte(JsonData.of(query.maxPrice().doubleValue()));
                }
                return r;
            }));
        }
        if (query.conditionLevel() != null) {
            bool.filter(term("conditionLevel", FieldValue.of(query.conditionLevel())));
        }
        if (hasText(query.location())) {
            bool.filter(term("location", FieldValue.of(query.location())));
        }
        if (hasText(query.category())) {
            List<FieldValue> categories = Arrays.stream(query.category().split(","))
                    .map(String::trim)
                    .filter(c -> !c.isEmpty())
                    .map(FieldValue::of)
                    .toList();
            bool.filter(f -> f.terms(t -> t.field("category").terms(v -> v.value(categories))));
        }
        bool.filter(term("status", FieldValue.of(ON_SALE)));

        return SearchRequest.of(s -> s
                .index(index)
                .query(q -> q.bool(bool.build()))
                .from(query.page() * query.size())
                .size(query.size())
                .highlight(h -> h
                        .preTags(PRE_TAG)
                        .postTags(POST_TAG)
                        .fields("name", f -> f)
                        .fields("description", f -> f)));
    }

    /** Without a match in a field, its highlighted text is the plain text. */
    static ProductDocument withHighlights(Hit<ProductDocument> hit) {
        ProductDocument doc = hit.source();
        Map<String, List<String>> highlight = hit.highlight();
        doc.setHighlightedName(first(highlight, "name", doc.getName()));
        doc.setHighlightedDescription(first(highlight, "description", doc.getDescription()));
        return doc;
    }

    private static String first(Map<String, List<String>> highlight, String field, String fallback) {
        List<String> fragments = highlight.get(field);
        return fragments == null || fragments.isEmpty() ? fallback : fragments.get(0);
    }

    private static Query term(String field, FieldValue value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
