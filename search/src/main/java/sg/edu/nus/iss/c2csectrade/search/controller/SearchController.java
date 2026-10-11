package sg.edu.nus.iss.c2csectrade.search.controller;

import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;
import sg.edu.nus.iss.c2csectrade.search.service.ProductSearchQuery;
import sg.edu.nus.iss.c2csectrade.search.service.ProductSearchService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Public product search. The load balancer sends /api/search and
 * /api/search/* to this service.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    static final int MAX_PAGE_SIZE = 100;

    private final ProductSearchService searchService;

    public SearchController(ProductSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<ProductDocument> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer conditionLevel,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) throws IOException {
        ProductSearchQuery query = new ProductSearchQuery(keyword, minPrice, maxPrice, conditionLevel,
                location, category, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return searchService.search(query);
    }

    /** The index is unreachable or rejected the query: 503, so callers can fall back. */
    @ExceptionHandler({IOException.class, OpenSearchException.class})
    public ResponseEntity<Map<String, String>> searchUnavailable(Exception e) {
        log.error("Search failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Search is temporarily unavailable"));
    }
}
