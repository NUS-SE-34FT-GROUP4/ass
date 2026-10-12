package sg.edu.nus.iss.c2csectrade.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Calls the Search service: GET /api/search for the home page, and
 * POST /internal/reindex for the administrator's full rebuild. Any failure
 * (unreachable, timeout, error status) is reported as
 * SearchUnavailableException, so the home page can fall back to the database.
 */
@Component
public class SearchClient {

    private static final ParameterizedTypeReference<List<Hit>> HITS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    // A rebuild reads every product, so it may take far longer than a search
    private final RestClient reindexClient;

    @Autowired
    public SearchClient(RestClient.Builder builder,
                        @Value("${app.search.url}") String url,
                        @Value("${app.search.timeout:2s}") Duration timeout,
                        @Value("${app.search.reindex-timeout:60s}") Duration reindexTimeout) {
        this.restClient = builder.clone().baseUrl(url).requestFactory(requestFactory(timeout, timeout)).build();
        this.reindexClient = builder.clone().baseUrl(url).requestFactory(requestFactory(timeout, reindexTimeout)).build();
    }

    SearchClient(RestClient restClient) {
        this.restClient = restClient;
        this.reindexClient = restClient;
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }

    /** Products on sale that match, best match first. */
    public List<Hit> search(String keyword, BigDecimal minPrice, BigDecimal maxPrice, Integer conditionLevel,
                            String location, String categories, int size) {
        try {
            List<Hit> hits = restClient.get()
                    .uri(uri -> uri.path("/api/search")
                            .queryParamIfPresent("keyword", Optional.ofNullable(keyword))
                            .queryParamIfPresent("minPrice", Optional.ofNullable(minPrice))
                            .queryParamIfPresent("maxPrice", Optional.ofNullable(maxPrice))
                            .queryParamIfPresent("conditionLevel", Optional.ofNullable(conditionLevel))
                            .queryParamIfPresent("location", Optional.ofNullable(location))
                            .queryParamIfPresent("category", Optional.ofNullable(categories))
                            .queryParam("size", size)
                            .build())
                    .retrieve()
                    .body(HITS);
            return hits == null ? List.of() : hits;
        } catch (RestClientException e) {
            throw new SearchUnavailableException(e);
        }
    }

    /**
     * Rebuilds the whole index from the product table and waits for it to finish.
     *
     * @throws ReindexInProgressException if a rebuild is already running
     */
    public ReindexResult reindex() {
        try {
            return reindexClient.post().uri("/internal/reindex").retrieve().body(ReindexResult.class);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ReindexInProgressException();
        } catch (RestClientException e) {
            throw new SearchUnavailableException(e);
        }
    }

    public record ReindexResult(int indexed, long removed) {
    }

    /** The fields Core uses from a search result; the rest comes from the database. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(Long productId, String highlightedName, String highlightedDescription) {
    }

    public static class SearchUnavailableException extends RuntimeException {
        public SearchUnavailableException(Throwable cause) {
            super("Search service unavailable: " + cause.getMessage(), cause);
        }
    }

    public static class ReindexInProgressException extends RuntimeException {
        public ReindexInProgressException() {
            super("A rebuild of the search index is already running");
        }
    }
}
