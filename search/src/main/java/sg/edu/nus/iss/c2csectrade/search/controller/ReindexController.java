package sg.edu.nus.iss.c2csectrade.search.controller;

import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.iss.c2csectrade.search.service.ProductReindexService;

import java.io.IOException;
import java.util.Map;

/**
 * Full index rebuild, for Core's administrator endpoint
 * POST /api/admin/search/reindex. Not under /api/search, so the load balancer
 * never routes it here: only services inside the VPC reach it. Search has no
 * user roles to check, so Core does the administrator check.
 */
@RestController
public class ReindexController {

    private static final Logger log = LoggerFactory.getLogger(ReindexController.class);

    private final ProductReindexService reindexService;

    public ReindexController(ProductReindexService reindexService) {
        this.reindexService = reindexService;
    }

    @PostMapping("/internal/reindex")
    public ProductReindexService.Result reindex() throws IOException {
        return reindexService.rebuild();
    }

    @ExceptionHandler(ProductReindexService.ReindexInProgressException.class)
    public ResponseEntity<Map<String, String>> alreadyRunning(ProductReindexService.ReindexInProgressException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    /** OpenSearch or MySQL is unreachable: 503, and the next rebuild starts over. */
    @ExceptionHandler({IOException.class, OpenSearchException.class, DataAccessException.class})
    public ResponseEntity<Map<String, String>> unavailable(Exception e) {
        log.error("Rebuilding the search index failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "The search index could not be rebuilt"));
    }
}
