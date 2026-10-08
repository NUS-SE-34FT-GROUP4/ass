package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ProductCreateDTO;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import sg.edu.nus.iss.c2csectrade.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sg.edu.nus.iss.c2csectrade.service.SearchService;
import sg.edu.nus.iss.c2csectrade.dto.SearchRequestDTO;
import sg.edu.nus.iss.c2csectrade.document.ProductDocument;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.function.Function;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductService productService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createProduct(
            @RequestPart("productData") String productDataJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {
        try {
            ProductCreateDTO createDTO = objectMapper.readValue(productDataJson, ProductCreateDTO.class);

            // Resolve the user ID safely
            Long userId = null;
            Object principal = authentication.getPrincipal();

            if (principal instanceof UserDetailsImpl) {
                userId = ((UserDetailsImpl) principal).getId();
            } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                // Look up the user ID from the username
                String username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
                var user = productService.getUserByUsername(username);
                if (user != null) {
                    userId = user.getId();
                }
            } else if (principal instanceof String) {
                // The principal is a plain username string
                var user = productService.getUserByUsername((String) principal);
                if (user != null) {
                    userId = user.getId();
                }
            }

            if (userId == null) {
                return new ResponseEntity<>("Unable to resolve the current user", HttpStatus.UNAUTHORIZED);
            }

            var createdProduct = productService.createProduct(createDTO, files, userId);
            return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
        } catch (ClassCastException e) {
            System.err.println("Unexpected authentication type: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>("Unexpected authentication type", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            System.err.println("Failed to create product: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>("Failed to create product. Please try again later", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping
    public ResponseEntity<List<ProductDTO>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) Integer conditionLevel,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String categories) {
        try {
            List<ProductDTO> productDTOs;

            // Use Elasticsearch only when there is a keyword (for highlighting)
            if (keyword != null && !keyword.trim().isEmpty()) {

                SearchRequestDTO searchRequest = new SearchRequestDTO();
                searchRequest.setKeyword(keyword);
                searchRequest.setMinPrice(minPrice);
                searchRequest.setMaxPrice(maxPrice);
                searchRequest.setConditionLevel(conditionLevel);
                searchRequest.setLocation(location);
                searchRequest.setCategory(categories); // TODO: Add category support to
                // SearchService
                searchRequest.setPage(0);
                searchRequest.setSize(100);

                List<ProductDocument> searchResults = searchService.searchProducts(searchRequest);

                if (searchResults.isEmpty()) {
                    return ResponseEntity.ok(new ArrayList<>());
                }

                // Extract IDs
                List<Long> productIds = searchResults.stream()
                        .map(ProductDocument::getProductId)
                        .collect(Collectors.toList());

                List<Product> products = new ArrayList<>();
                for (Long id : productIds) {
                    Product p = productService.getProductById(id);
                    if (p != null) {
                        products.add(p);
                    }
                }

                productDTOs = productService.convertToDTOList(products);

                // Overlay highlighting
                Map<Long, ProductDocument> docMap = searchResults.stream()
                        .collect(Collectors.toMap(ProductDocument::getProductId, Function.identity()));

                for (ProductDTO dto : productDTOs) {
                    ProductDocument doc = docMap.get(dto.getId());
                    if (doc != null) {
                        dto.setHighlightedName(doc.getHighlightedName());
                        dto.setHighlightedDescription(doc.getHighlightedDescription());
                    }
                }

            } else {
                // Without a keyword, query the database (supports every filter)
                List<Product> products = productService.listProductsWithFilters(keyword, minPrice, maxPrice,
                        conditionLevel, location, categories);
                productDTOs = productService.convertToDTOList(products);
            }

            return ResponseEntity.ok(productDTOs);
        } catch (Exception e) {
            // Log the details instead of exposing them to the client
            System.err.println("Failed to load product list: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        try {
            ProductDTO productDTO = productService.getProductDTOById(id);
            if (productDTO == null) {
                return new ResponseEntity<>("Product not found", HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok(productDTO);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching product: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id, Authentication authentication) {
        try {
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>("Unable to resolve the current user", HttpStatus.UNAUTHORIZED);
            }

            // Verify ownership or admin role
            Product existingProduct = productService.getProductById(id);
            if (existingProduct == null) {
                return new ResponseEntity<>("Product not found", HttpStatus.NOT_FOUND);
            }

            // Check if user is admin or product owner
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

            if (!existingProduct.getUserId().equals(userId) && !isAdmin) {
                return new ResponseEntity<>("You are not allowed to delete this product", HttpStatus.FORBIDDEN);
            }

            productService.deleteProduct(id);
            return ResponseEntity.ok("Product deleted successfully");
        } catch (Exception e) {
            return new ResponseEntity<>("Error deleting product: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/my-products")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyProducts(Authentication authentication) {
        try {
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>("Unable to resolve the current user", HttpStatus.UNAUTHORIZED);
            }

            List<Product> products = productService.getProductsByUserId(userId);
            List<ProductDTO> productDTOs = productService.convertToDTOList(products);
            return ResponseEntity.ok(productDTOs);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching products: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProduct(
            @PathVariable Long id,
            @RequestPart("productData") String productDataJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            Authentication authentication) {
        try {
            Long userId = extractUserId(authentication);
            if (userId == null) {
                return new ResponseEntity<>("Unable to resolve the current user", HttpStatus.UNAUTHORIZED);
            }

            // Verify ownership
            Product existingProduct = productService.getProductById(id);
            if (existingProduct == null) {
                return new ResponseEntity<>("Product not found", HttpStatus.NOT_FOUND);
            }
            if (!existingProduct.getUserId().equals(userId)) {
                return new ResponseEntity<>("You are not allowed to edit this product", HttpStatus.FORBIDDEN);
            }

            ProductCreateDTO updateDTO = objectMapper.readValue(productDataJson, ProductCreateDTO.class);
            Product updatedProduct = productService.updateProduct(id, updateDTO, files);
            return ResponseEntity.ok(updatedProduct);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            System.err.println("JSON parsing failed: " + e.getMessage());
            return new ResponseEntity<>("Invalid product data format", HttpStatus.BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            return new ResponseEntity<>("Invalid argument: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            System.err.println("Failed to update product: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>("Failed to update product. Please try again later", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) principal).getId();
        } else if (principal instanceof org.springframework.security.core.userdetails.User) {
            String username = ((org.springframework.security.core.userdetails.User) principal).getUsername();
            var user = productService.getUserByUsername(username);
            return user != null ? user.getId() : null;
        } else if (principal instanceof String) {
            var user = productService.getUserByUsername((String) principal);
            return user != null ? user.getId() : null;
        }
        return null;
    }

}
