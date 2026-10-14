package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.ProductCreateDTO;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMediaMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.config.MinioProperties;
import sg.edu.nus.iss.c2csectrade.event.ProductCreatedEvent;
import sg.edu.nus.iss.c2csectrade.event.ProductUpdatedEvent;
import sg.edu.nus.iss.c2csectrade.event.ProductDeletedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductMediaMapper productMediaMapper;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private UserMapper userMapper;

    public Product createProduct(ProductCreateDTO dto, List<MultipartFile> files, Long userId) {
        List<String> uploadedUrls = new ArrayList<>();
        try {
            // Outside the transaction: upload files to MinIO first (if any)
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String url = fileStorageService.uploadFile(file);
                        uploadedUrls.add(url);
                    }
                }
            }

            // Inside the transaction: save to the database
            Product product = saveProductWithMedia(dto, uploadedUrls, userId);

            // Publish an event so the Search service indexes it (async, avoids a circular dependency)
            eventPublisher.publishEvent(new ProductCreatedEvent(this, product));

            return product;
        } catch (Exception e) {
            System.err.println("Failed to create product: " + e.getMessage());
            e.printStackTrace();

            // Compensate: delete the files already uploaded
            for (String url : uploadedUrls) {
                try {
                    String bucket = minioProperties.getBucketName();
                    int idx = url.indexOf(bucket + "/");
                    String objectName = idx >= 0 ? url.substring(idx + bucket.length() + 1) : null;
                    if (objectName != null) {
                        fileStorageService.deleteFile(objectName);
                    }
                } catch (Exception deleteException) {
                    System.err.println("Failed to clean up uploaded files: " + deleteException.getMessage());

                }
            }
            throw new RuntimeException("Failed to create product: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected Product saveProductWithMedia(ProductCreateDTO dto, List<String> urls, Long userId) {
        System.out.println("Saving product, user ID: " + userId);
        System.out.println(
                "Product: name=" + dto.getName() + ", category=" + dto.getCategory() + ", price=" + dto.getPrice());

        Product product = new Product();
        product.setUserId(userId);
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setConditionLevel(dto.getConditionLevel());
        product.setLocation(dto.getLocation());
        product.setCategory(dto.getCategory());
        product.setStock(dto.getStock() != null ? dto.getStock() : 1);
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());

        System.out.println("Inserting product...");
        int insertResult = productMapper.insert(product);
        if (insertResult == 0) {
            throw new RuntimeException("Failed to insert product");
        }
        System.out.println("Product inserted, ID: " + product.getId());

        List<ProductMedia> mediaList = new ArrayList<>();
        if (urls != null && !urls.isEmpty()) {
            int sortOrder = 0;
            for (String url : urls) {
                System.out.println("Saving media file: " + url);
                ProductMedia media = new ProductMedia();

                // Build a Product reference
                Product productRef = new Product();
                productRef.setId(product.getId());
                media.setProduct(productRef);

                media.setUrl(url);
                media.setMediaType(url.matches("(?i).*\\.(mp4|mov|avi|wmv|flv|mkv)$") ? 2 : 1);
                media.setSortOrder(sortOrder++);

                int mediaInsertResult = productMediaMapper.insert(media);
                if (mediaInsertResult > 0) {
                    mediaList.add(media);
                    System.out.println("Media saved, ID: " + media.getId());
                } else {
                    throw new RuntimeException("Failed to insert media record");
                }
            }
        } else {
            System.out.println("No media files to save");
        }

        product.setMedia(mediaList);
        System.out.println("Product saved");
        return product;
    }

    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product != null) {
            List<ProductMedia> media = productMediaMapper.selectByProductId(id);
            product.setMedia(media);
        }
        return product;
    }

    public List<Product> listAllProducts() {
        List<Product> products = productMapper.selectAll();
        // Load media and seller details for each product
        for (Product product : products) {
            List<ProductMedia> media = productMediaMapper.selectByProductId(product.getId());
            product.setMedia(media);
        }
        return products;
    }

    /** One page of the product list, newest first unless {@code sort} says otherwise. */
    public record ProductPage(List<ProductDTO> items, long total) {
    }

    /**
     * One page of products matching the filters, plus how many match in total.
     * Loading a page costs four queries whatever its size: the count, the page,
     * its media and its sellers. Listing everything at once took 8.4 s for
     * 10,000 products in the WP10 load test.
     */
    public ProductPage listProductsPage(String keyword, java.math.BigDecimal minPrice,
            java.math.BigDecimal maxPrice, Integer conditionLevel, String location, String category,
            String sort, int page, int size) {
        long total = productMapper.countWithFilters(keyword, minPrice, maxPrice, conditionLevel, location, category);
        if (total == 0 || (long) page * size >= total) {
            return new ProductPage(List.of(), total);
        }
        List<Product> products = productMapper.selectWithFilters(keyword, minPrice, maxPrice, conditionLevel,
                location, category, sort, size, (long) page * size);
        return new ProductPage(convertToDTOList(withMedia(products)), total);
    }

    /**
     * The products with these ids that are still listed, in the order given,
     * with media and sellers loaded in one query each. Used for search hits.
     */
    public List<ProductDTO> getListedProductDTOs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        List<Product> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return convertToDTOList(withMedia(ordered));
    }

    /** Attaches each product's media using a single query for the whole list. */
    private List<Product> withMedia(List<Product> products) {
        if (products.isEmpty()) {
            return products;
        }
        Map<Long, List<ProductMedia>> byProduct = productMediaMapper
                .selectByProductIds(products.stream().map(Product::getId).toList()).stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()));
        products.forEach(p -> p.setMedia(byProduct.getOrDefault(p.getId(), new ArrayList<>())));
        return products;
    }

    /**
     * Convert Product entity to ProductDTO
     */
    public ProductDTO convertToDTO(Product product) {
        if (product == null) {
            return null;
        }
        User seller = product.getUserId() == null ? null : userMapper.selectById(product.getUserId());
        return convertToDTO(product, seller);
    }

    /** As {@link #convertToDTO(Product)}, with the seller already loaded by the caller. */
    private ProductDTO convertToDTO(Product product, User seller) {

        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId()); // id is already a Long
        dto.setUserId(product.getUserId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setConditionLevel(product.getConditionLevel());
        dto.setCategory(product.getCategory()); // Product category
        dto.setLocation(product.getLocation());
        dto.setStatus(product.getStatus());
        dto.setCreateTime(product.getCreatedAt());
        dto.setUpdateTime(product.getUpdatedAt());

        // Seller info
        if (seller != null) {
            dto.setUsername(seller.getUsername());
            dto.setDisplayName(seller.getDisplayName());
            dto.setAvatarUrl(seller.getAvatarUrl()); // Avatar URL
        }

        // Convert media list
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            List<String> imageUrls = new ArrayList<>();
            List<String> videoUrls = new ArrayList<>();
            List<ProductDTO.MediaItem> mediaItems = new ArrayList<>();

            for (ProductMedia media : product.getMedia()) {
                // Add to the media list (for multi-image display on the frontend)
                ProductDTO.MediaItem mediaItem = new ProductDTO.MediaItem(
                        media.getId(), // id is already a Long
                        media.getUrl(),
                        media.getMediaType(),
                        media.getSortOrder());
                mediaItems.add(mediaItem);

                // Also add to imageUrls or videoUrls (backward compatibility)
                if (media.getMediaType() == 1) {
                    imageUrls.add(media.getUrl());
                } else if (media.getMediaType() == 2) {
                    videoUrls.add(media.getUrl());
                }
            }

            dto.setMedia(mediaItems); // Set the media field
            dto.setImageUrls(imageUrls);
            dto.setVideoUrls(videoUrls);

            // Set cover image as first image
            if (!imageUrls.isEmpty()) {
                dto.setCoverImage(imageUrls.get(0));
            }
        }

        return dto;
    }

    /**
     * Convert Product entity by ID to ProductDTO
     */
    public ProductDTO getProductDTOById(Long id) {
        Product product = getProductById(id);
        return convertToDTO(product);
    }

    /**
     * Convert list of Products to list of ProductDTOs
     */
    public List<ProductDTO> convertToDTOList(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return new ArrayList<>();
        }
        // All sellers in one query, instead of one per product
        Collection<Long> sellerIds = products.stream().map(Product::getUserId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, User> sellers = sellerIds.isEmpty() ? Map.of() : userMapper.selectByIds(sellerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        return products.stream()
                .map(p -> convertToDTO(p, p.getUserId() == null ? null : sellers.get(p.getUserId())))
                .collect(Collectors.toList());
    }

    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
        // Sync to the search index
        try {
            eventPublisher.publishEvent(new ProductDeletedEvent(this, id));
        } catch (Exception e) {
            System.err.println("Failed to publish ProductDeletedEvent: " + e.getMessage());
        }
    }

    public void delistProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product != null) {
            product.setStatus(0);
            productMapper.update(product);

            // Sync to the search index, which only returns products on sale
            try {
                eventPublisher.publishEvent(new ProductUpdatedEvent(this, product));
            } catch (Exception e) {
                System.err.println("Failed to publish ProductUpdatedEvent: " + e.getMessage());
            }
        }
    }

    /**
     * Get products by user ID
     */
    public List<Product> getProductsByUserId(Long userId) {
        List<Product> products = productMapper.selectByUserId(userId);
        // Load media for each product
        for (Product product : products) {
            List<ProductMedia> media = productMediaMapper.selectByProductId(product.getId());
            product.setMedia(media);
        }
        return products;
    }

    /**
     * Update product
     */
    @Transactional
    public Product updateProduct(Long id, ProductCreateDTO dto, List<MultipartFile> files) {
        try {
            System.out.println("Updating product, ID: " + id);

            Product product = productMapper.selectById(id);
            if (product == null) {
                throw new IllegalArgumentException("Product not found");
            }

            // Update basic info
            product.setName(dto.getName());
            product.setDescription(dto.getDescription());
            product.setPrice(dto.getPrice());
            product.setCategory(dto.getCategory());
            product.setConditionLevel(dto.getConditionLevel());
            product.setLocation(dto.getLocation());
            product.setStock(dto.getStock() != null ? dto.getStock() : product.getStock());
            product.setUpdatedAt(LocalDateTime.now());

            System.out.println("Updating product details...");
            int updateResult = productMapper.update(product);
            if (updateResult == 0) {
                throw new RuntimeException("Failed to update product details");
            }

            // If new files provided, replace all media
            if (files != null && !files.isEmpty()) {
                System.out.println("Processing " + files.size() + " newly uploaded files");

                // Delete old media from database (keep files in MinIO for now)
                List<ProductMedia> oldMedia = productMediaMapper.selectByProductId(id);
                System.out.println("Deleting " + oldMedia.size() + " old media records");

                for (ProductMedia media : oldMedia) {
                    try {
                        productMediaMapper.deleteById(media.getId()); // id is already a Long
                    } catch (Exception e) {
                        System.err.println("Failed to delete old media record: " + e.getMessage());
                    }
                }

                // Upload and save new media
                List<ProductMedia> newMediaList = new ArrayList<>();
                int sortOrder = 0;

                for (MultipartFile file : files) {
                    try {
                        System.out.println("Uploading file: " + file.getOriginalFilename());
                        String url = fileStorageService.uploadFile(file);

                        ProductMedia media = new ProductMedia();

                        // Use productId directly
                        Product productRef = new Product();
                        productRef.setId(id); // id is a Long now, use it directly
                        media.setProduct(productRef);

                        media.setUrl(url);

                        // Work out the file type
                        String filename = file.getOriginalFilename();
                        if (filename != null && filename.matches("(?i).*\\.(mp4|mov|avi|wmv|flv|mkv)$")) {
                            media.setMediaType(2); // Video
                        } else {
                            media.setMediaType(1); // Image
                        }

                        media.setSortOrder(sortOrder++);

                        int insertResult = productMediaMapper.insert(media);
                        if (insertResult > 0) {
                            newMediaList.add(media);
                            System.out.println("File uploaded and saved: " + url);
                        } else {
                            throw new RuntimeException("Failed to insert media record into the database");
                        }
                    } catch (Exception e) {
                        System.err.println("File upload failed: " + e.getMessage());
                        e.printStackTrace();
                        throw new RuntimeException("File upload failed: " + file.getOriginalFilename() + ", reason: " + e.getMessage());
                    }
                }
                product.setMedia(newMediaList);
            } else {
                // Keep existing media
                System.out.println("Keeping existing media files");
                List<ProductMedia> media = productMediaMapper.selectByProductId(id);
                product.setMedia(media);
            }

            System.out.println("Product updated");

            // Sync to the search index
            try {
                eventPublisher.publishEvent(new ProductUpdatedEvent(this, product));
            } catch (Exception e) {
                System.err.println("Failed to publish ProductUpdatedEvent: " + e.getMessage());
            }
            return product;

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid argument: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Failed to update product: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to update product: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by username
     */
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }
}
