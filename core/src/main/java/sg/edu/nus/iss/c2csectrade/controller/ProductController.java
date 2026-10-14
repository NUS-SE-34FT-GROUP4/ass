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
import sg.edu.nus.iss.c2csectrade.service.SearchClient;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductService productService;
    @Autowired
    private SearchClient searchClient;
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

    /** Largest page the list accepts; the home page asks for 12. */
    static final int MAX_PAGE_SIZE = 100;
    /** Response header carrying how many products match, so the body stays a plain array. */
    static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    private static final java.util.Set<String> SORTS =
            java.util.Set.of("default", "price-asc", "price-desc", "time-asc", "time-desc");

    /**
     * One page of the product list. The body is the page itself; the total
     * number of matches is in the X-Total-Count header. page counts from 0.
     */
    @GetMapping
    public ResponseEntity<List<ProductDTO>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) Integer conditionLevel,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String categories,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || !SORTS.contains(sort)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // A keyword goes to the Search service (relevance, typo tolerance,
            // highlighting); without one, or when Search is down, the database answers
            if (keyword != null && !keyword.trim().isEmpty()) {
                try {
                    return page(searchByKeyword(keyword, minPrice, maxPrice, conditionLevel, location, categories, sort),
                            page, size);
                } catch (SearchClient.SearchUnavailableException e) {
                    System.err.println(e.getMessage() + "; searching the database instead");
                }
            }
            ProductService.ProductPage result = productService.listProductsPage(keyword, minPrice, maxPrice,
                    conditionLevel, location, categories, sort, page, size);
            return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, String.valueOf(result.total())).body(result.items());
        } catch (Exception e) {
            // Log the details instead of exposing them to the client
            System.err.println("Failed to load product list: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** A page cut from search results, which are at most 100 and already loaded. */
    private static ResponseEntity<List<ProductDTO>> page(List<ProductDTO> all, int page, int size) {
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, String.valueOf(all.size()))
                .body(new ArrayList<>(all.subList(from, to)));
    }

    /**
     * Product ids and highlights come from the Search service, everything else
     * from the database in one batch. A product deleted or sold out since it
     * was indexed is skipped. Search orders by relevance; any other sort is
     * applied here, across all hits, before the page is cut.
     */
    private List<ProductDTO> searchByKeyword(String keyword, java.math.BigDecimal minPrice,
            java.math.BigDecimal maxPrice, Integer conditionLevel, String location, String categories, String sort) {
        List<SearchClient.Hit> hits = searchClient.search(keyword, minPrice, maxPrice, conditionLevel,
                location, categories, 100);

        java.util.Map<Long, SearchClient.Hit> byId = new java.util.HashMap<>();
        hits.forEach(hit -> byId.putIfAbsent(hit.productId(), hit));
        List<ProductDTO> productDTOs = productService.getListedProductDTOs(
                hits.stream().map(SearchClient.Hit::productId).distinct().toList());
        for (ProductDTO dto : productDTOs) {
            SearchClient.Hit hit = byId.get(dto.getId());
            dto.setHighlightedName(hit.highlightedName());
            dto.setHighlightedDescription(hit.highlightedDescription());
        }
        java.util.Comparator<ProductDTO> order = switch (sort) {
            case "price-asc" -> java.util.Comparator.comparing(ProductDTO::getPrice,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "price-desc" -> java.util.Comparator.comparing(ProductDTO::getPrice,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
            case "time-asc" -> java.util.Comparator.comparing(ProductDTO::getCreateTime,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "time-desc" -> java.util.Comparator.comparing(ProductDTO::getCreateTime,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
            default -> null; // keep Search's relevance order
        };
        if (order != null) {
            productDTOs = new ArrayList<>(productDTOs);
            productDTOs.sort(order);
        }
        return productDTOs;
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
