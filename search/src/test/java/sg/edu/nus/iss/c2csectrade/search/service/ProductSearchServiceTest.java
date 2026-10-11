package sg.edu.nus.iss.c2csectrade.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.search.Hit;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Checks the query sent to OpenSearch, field by field, without a cluster. */
class ProductSearchServiceTest {

    private static final JacksonJsonpMapper MAPPER = new JacksonJsonpMapper();

    private final ProductSearchService service = new ProductSearchService(mock(OpenSearchClient.class), "products");

    @Test
    void keywordMatchesNameDescriptionAndCategoryWithTypos() throws Exception {
        JsonNode bool = boolQuery(query("lamp", null, null, null, null, null));

        JsonNode multiMatch = bool.at("/must/0/multi_match");
        assertThat(multiMatch.get("query").asText()).isEqualTo("lamp");
        assertThat(multiMatch.get("fuzziness").asText()).isEqualTo("AUTO");
        assertThat(multiMatch.get("fields")).extracting(JsonNode::asText)
                .containsExactly("name^2", "description", "category");
    }

    @Test
    void onlyProductsOnSaleAreReturned() throws Exception {
        JsonNode bool = boolQuery(query(null, null, null, null, null, null));

        assertThat(bool.has("must")).isFalse();
        assertThat(bool.get("filter")).hasSize(1);
        assertThat(bool.at("/filter/0/term/status/value").asInt()).isEqualTo(1);
    }

    @Test
    void everyFilterIsApplied() throws Exception {
        JsonNode bool = boolQuery(query(null, new BigDecimal("20"), new BigDecimal("50"), 4, "Kent Ridge", "Books, Home"));

        JsonNode filters = bool.get("filter");
        assertThat(filters).hasSize(5);
        assertThat(filters.get(0).at("/range/price/gte").asDouble()).isEqualTo(20.0);
        assertThat(filters.get(0).at("/range/price/lte").asDouble()).isEqualTo(50.0);
        assertThat(filters.get(1).at("/term/conditionLevel/value").asInt()).isEqualTo(4);
        assertThat(filters.get(2).at("/term/location/value").asText()).isEqualTo("Kent Ridge");
        assertThat(filters.get(3).at("/terms/category")).extracting(JsonNode::asText)
                .containsExactly("Books", "Home");
    }

    @Test
    void openEndedPriceRangeHasOneBound() throws Exception {
        JsonNode range = boolQuery(query(null, new BigDecimal("20"), null, null, null, null))
                .at("/filter/0/range/price");

        assertThat(range.get("gte").asDouble()).isEqualTo(20.0);
        assertThat(range.has("lte")).isFalse();
    }

    @Test
    void pagesAndHighlights() throws Exception {
        JsonNode request = json(service.request(new ProductSearchQuery("lamp", null, null, null, null, null, 2, 10)));

        assertThat(request.get("from").asInt()).isEqualTo(20);
        assertThat(request.get("size").asInt()).isEqualTo(10);
        assertThat(request.at("/highlight/pre_tags/0").asText()).isEqualTo("<em class='highlight'>");
        assertThat(request.at("/highlight/post_tags/0").asText()).isEqualTo("</em>");
        assertThat(request.at("/highlight/fields").has("name")).isTrue();
        assertThat(request.at("/highlight/fields").has("description")).isTrue();
    }

    @Test
    void highlightsFallBackToThePlainText() {
        ProductDocument doc = new ProductDocument();
        doc.setName("Desk lamp");
        doc.setDescription("Barely used");
        Hit<ProductDocument> hit = Hit.of(h -> h
                .index("products")
                .id("42")
                .source(doc)
                .highlight(Map.of("name", List.of("Desk <em class='highlight'>lamp</em>"))));

        ProductDocument result = ProductSearchService.withHighlights(hit);

        assertThat(result.getHighlightedName()).isEqualTo("Desk <em class='highlight'>lamp</em>");
        assertThat(result.getHighlightedDescription()).isEqualTo("Barely used");
    }

    private static ProductSearchQuery query(String keyword, BigDecimal minPrice, BigDecimal maxPrice,
                                            Integer conditionLevel, String location, String category) {
        return new ProductSearchQuery(keyword, minPrice, maxPrice, conditionLevel, location, category, 0, 20);
    }

    private JsonNode boolQuery(ProductSearchQuery query) throws Exception {
        return json(service.request(query)).at("/query/bool");
    }

    private static JsonNode json(JsonpSerializable value) throws Exception {
        StringWriter out = new StringWriter();
        try (var generator = MAPPER.jsonProvider().createGenerator(out)) {
            value.serialize(generator, MAPPER);
        }
        return new ObjectMapper().readTree(out.toString());
    }
}
