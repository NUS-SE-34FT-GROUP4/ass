package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.service.FavoriteService;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private ProductService productService;

    @PostMapping("/add/{productId}")
    public ResponseEntity<?> addFavorite(@PathVariable Long productId, Authentication authentication) {
        String username = authentication.getName();
        boolean success = favoriteService.addFavorite(username, productId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Added to favorites" : "Failed to add to favorites");

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<?> removeFavorite(@PathVariable Long productId, Authentication authentication) {
        String username = authentication.getName();
        boolean success = favoriteService.removeFavorite(username, productId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Removed from favorites" : "Failed to remove from favorites");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<?> checkFavorite(@PathVariable Long productId, Authentication authentication) {
        String username = authentication.getName();
        boolean isFavorite = favoriteService.isFavorite(username, productId);

        Map<String, Object> response = new HashMap<>();
        response.put("isFavorite", isFavorite);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<?> getFavorites(Authentication authentication) {
        String username = authentication.getName();
        List<Long> productIds = favoriteService.getFavoriteProductIds(username);

        // Load product details
        List<ProductDTO> products = productIds.stream()
                .map(productService::getProductDTOById)
                .filter(product -> product != null)
                .toList();

        return ResponseEntity.ok(products);
    }

    @GetMapping("/count")
    public ResponseEntity<?> getFavoriteCount(Authentication authentication) {
        String username = authentication.getName();
        int count = favoriteService.getFavoriteCount(username);

        Map<String, Object> response = new HashMap<>();
        response.put("count", count);

        return ResponseEntity.ok(response);
    }
}

