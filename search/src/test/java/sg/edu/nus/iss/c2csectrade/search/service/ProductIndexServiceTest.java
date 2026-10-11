package sg.edu.nus.iss.c2csectrade.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIndexServiceTest {

    private static final Events.ProductChangedEvent EVENT = new Events.ProductChangedEvent(
            42L, 7L, "Desk lamp", "Barely used", new BigDecimal("12.50"),
            9, "Kent Ridge", "Home", 1, LocalDateTime.of(2026, 10, 8, 9, 30, 15, 123_456_000));

    @Test
    void documentCarriesEveryEventField() {
        ProductDocument doc = ProductIndexService.toDocument(EVENT);

        assertThat(doc.getId()).isEqualTo("42");
        assertThat(doc.getProductId()).isEqualTo(42L);
        assertThat(doc.getUserId()).isEqualTo(7L);
        assertThat(doc.getName()).isEqualTo("Desk lamp");
        assertThat(doc.getDescription()).isEqualTo("Barely used");
        assertThat(doc.getPrice()).isEqualByComparingTo("12.50");
        assertThat(doc.getConditionLevel()).isEqualTo(9);
        assertThat(doc.getLocation()).isEqualTo("Kent Ridge");
        assertThat(doc.getCategory()).isEqualTo("Home");
        assertThat(doc.getStatus()).isEqualTo(1);
        assertThat(doc.getCreatedAt()).isEqualTo(EVENT.getCreatedAt());
    }

    /**
     * What goes into the index, written with the same mapper settings as
     * OpenSearchConfig: createdAt in whole seconds (the mapping's first
     * format) and no highlight fields.
     */
    @Test
    void storedDocumentMatchesTheMapping() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        JsonNode stored = mapper.readTree(mapper.writeValueAsString(ProductIndexService.toDocument(EVENT)));

        assertThat(stored.get("createdAt").asText()).isEqualTo("2026-10-08T09:30:15");
        assertThat(stored.has("highlightedName")).isFalse();
        assertThat(stored.has("highlightedDescription")).isFalse();
    }

    /** A document Core wrote through Spring Data Elasticsearch, as stored in the index. */
    @Test
    void readsDocumentsCoreWrote() throws Exception {
        String stored = "{\"_class\":\"sg.edu.nus.iss.c2csectrade.document.ProductDocument\",\"id\":\"1\","
                + "\"productId\":1,\"userId\":106,\"name\":\"Calculus textbook\",\"price\":45.0,"
                + "\"status\":1,\"createdAt\":\"2026-10-10T21:24:13\",\"category\":\"Books\"}";
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        ProductDocument doc = mapper.readValue(stored, ProductDocument.class);

        assertThat(doc.getProductId()).isEqualTo(1L);
        assertThat(doc.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 10, 21, 24, 13));
    }

    /** Same field types as the index Core created through Spring Data Elasticsearch. */
    @Test
    void mappingKeepsCoresFieldTypes() throws Exception {
        JacksonJsonpMapper jsonp = new JacksonJsonpMapper();
        StringWriter out = new StringWriter();
        try (var generator = jsonp.jsonProvider().createGenerator(out)) {
            ProductIndexService.mapping().serialize(generator, jsonp);
        }
        JsonNode properties = new ObjectMapper().readTree(out.toString()).get("properties");

        assertThat(properties.at("/productId/type").asText()).isEqualTo("long");
        assertThat(properties.at("/userId/type").asText()).isEqualTo("long");
        assertThat(properties.at("/name/type").asText()).isEqualTo("text");
        assertThat(properties.at("/name/analyzer").asText()).isEqualTo("standard");
        assertThat(properties.at("/description/type").asText()).isEqualTo("text");
        assertThat(properties.at("/price/type").asText()).isEqualTo("double");
        assertThat(properties.at("/conditionLevel/type").asText()).isEqualTo("integer");
        assertThat(properties.at("/location/type").asText()).isEqualTo("keyword");
        assertThat(properties.at("/status/type").asText()).isEqualTo("integer");
        assertThat(properties.at("/category/type").asText()).isEqualTo("keyword");
        assertThat(properties.at("/createdAt/type").asText()).isEqualTo("date");
        assertThat(properties.at("/createdAt/format").asText()).startsWith("yyyy-MM-dd'T'HH:mm:ss||");
    }
}
