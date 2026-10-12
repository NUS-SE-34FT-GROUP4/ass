package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SearchClientTest {

    private MockRestServiceServer server;
    private SearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://search:8080");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SearchClient(builder.build());
    }

    @Test
    void sendsTheFiltersAndReadsTheHits() {
        server.expect(requestTo("http://search:8080/api/search?keyword=desk%20lamp&minPrice=10&maxPrice=30.5"
                        + "&conditionLevel=4&location=Kent%20Ridge&category=Home,Books&size=100"))
                .andRespond(withSuccess("""
                        [{"id":"42","productId":42,"name":"Desk lamp","price":12.5,
                          "highlightedName":"Desk <em class='highlight'>lamp</em>",
                          "highlightedDescription":"Barely used"}]""", MediaType.APPLICATION_JSON));

        List<SearchClient.Hit> hits = client.search("desk lamp", new BigDecimal("10"), new BigDecimal("30.5"),
                4, "Kent Ridge", "Home,Books", 100);

        assertThat(hits).containsExactly(
                new SearchClient.Hit(42L, "Desk <em class='highlight'>lamp</em>", "Barely used"));
        server.verify();
    }

    @Test
    void leavesOutFiltersThatAreNotSet() {
        server.expect(requestTo("http://search:8080/api/search?keyword=lamp&size=100"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.search("lamp", null, null, null, null, null, 100)).isEmpty();
        server.verify();
    }

    /** Search answers 503 when OpenSearch is down. */
    @Test
    void errorStatusMeansUnavailable() {
        server.expect(requestTo("http://search:8080/api/search?keyword=lamp&size=100"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.search("lamp", null, null, null, null, null, 100))
                .isInstanceOf(SearchClient.SearchUnavailableException.class);
    }

    @Test
    void reindexReturnsWhatSearchRebuilt() {
        server.expect(requestTo("http://search:8080/internal/reindex"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"indexed\":12,\"removed\":2}", MediaType.APPLICATION_JSON));

        assertThat(client.reindex()).isEqualTo(new SearchClient.ReindexResult(12, 2));
        server.verify();
    }

    @Test
    void reindexAlreadyRunning() {
        server.expect(requestTo("http://search:8080/internal/reindex"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.reindex()).isInstanceOf(SearchClient.ReindexInProgressException.class);
    }

    @Test
    void reindexFailureMeansUnavailable() {
        server.expect(requestTo("http://search:8080/internal/reindex"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.reindex()).isInstanceOf(SearchClient.SearchUnavailableException.class);
    }

    @Test
    void unreachableServiceMeansUnavailable() {
        SearchClient unreachable = new SearchClient(RestClient.builder(), "http://127.0.0.1:1",
                Duration.ofMillis(500), Duration.ofMillis(500));

        assertThatThrownBy(() -> unreachable.search("lamp", null, null, null, null, null, 100))
                .isInstanceOf(SearchClient.SearchUnavailableException.class);
    }
}
