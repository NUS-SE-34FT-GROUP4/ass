package sg.edu.nus.iss.c2csectrade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.iss.c2csectrade.service.SearchClient;

import java.util.Map;

/**
 * The administrator's full rebuild of the search index. The Search service
 * does the work from the product table; Core only checks the role, which
 * Search cannot see in the token.
 */
@RestController
@RequestMapping("/api/admin/search")
public class SearchIndexController {

    private final SearchClient searchClient;

    public SearchIndexController(SearchClient searchClient) {
        this.searchClient = searchClient;
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reindex() {
        try {
            return ResponseEntity.ok(searchClient.reindex());
        } catch (SearchClient.ReindexInProgressException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (SearchClient.SearchUnavailableException e) {
            System.err.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "The search index could not be rebuilt"));
        }
    }
}
