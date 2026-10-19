package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.config.MinioProperties;
import sg.edu.nus.iss.c2csectrade.dto.ProductCreateDTO;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.event.ProductCreatedEvent;
import sg.edu.nus.iss.c2csectrade.event.ProductDeletedEvent;
import sg.edu.nus.iss.c2csectrade.event.ProductUpdatedEvent;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMediaMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Listing, editing and removing products. The list page and search hits load
 * media and sellers with one query for the whole page, and every change is
 * published so the search index follows it. The seller is user 9.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductMediaMapper productMediaMapper;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private MinioProperties minioProperties;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    void creatingAProductSavesItsMediaAndPublishesAnEvent() throws Exception {
        MockMultipartFile photo = file("lamp.jpg");
        MockMultipartFile clip = file("lamp.mp4");
        MockMultipartFile empty = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);
        when(fileStorageService.uploadFile(photo)).thenReturn("http://minio/c2c/lamp.jpg");
        when(fileStorageService.uploadFile(clip)).thenReturn("http://minio/c2c/lamp.mp4");
        when(productMapper.insert(any(Product.class))).thenAnswer(call -> {
            call.<Product>getArgument(0).setId(42L);
            return 1;
        });
        when(productMediaMapper.insert(any(ProductMedia.class))).thenReturn(1);

        Product product = productService.createProduct(lampDto(null), List.of(photo, empty, clip), 9L);

        assertThat(product.getId()).isEqualTo(42L);
        assertThat(product.getUserId()).isEqualTo(9L);
        assertThat(product.getStatus()).isEqualTo(1);
        assertThat(product.getStock()).isEqualTo(1);
        assertThat(product.getMedia()).extracting(ProductMedia::getMediaType).containsExactly(1, 2);
        assertThat(product.getMedia()).extracting(ProductMedia::getSortOrder).containsExactly(0, 1);
        verify(fileStorageService, never()).uploadFile(empty);
        verify(eventPublisher).publishEvent(any(ProductCreatedEvent.class));
    }

    @Test
    void aFailedSaveRemovesTheFilesAlreadyUploaded() throws Exception {
        MockMultipartFile photo = file("lamp.jpg");
        when(fileStorageService.uploadFile(photo)).thenReturn("http://minio/c2c/2026/lamp.jpg");
        when(minioProperties.getBucketName()).thenReturn("c2c");
        when(productMapper.insert(any(Product.class))).thenReturn(0);

        assertThatThrownBy(() -> productService.createProduct(lampDto(3), List.of(photo), 9L))
                .isInstanceOf(RuntimeException.class);
        verify(fileStorageService).deleteFile("2026/lamp.jpg");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aPageBeyondTheLastReturnsOnlyTheTotal() {
        when(productMapper.countWithFilters(any(), any(), any(), any(), any(), any())).thenReturn(5L);

        ProductService.ProductPage page = productService.listProductsPage(null, null, null, null, null, null, null, 1, 5);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isEqualTo(5);
        verify(productMapper, never()).selectWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aPageLoadsMediaAndSellersOnceForAllItsProducts() {
        when(productMapper.countWithFilters(any(), any(), any(), any(), any(), any())).thenReturn(12L);
        when(productMapper.selectWithFilters(any(), any(), any(), any(), any(), any(), any(), eq(10), eq(10L)))
                .thenReturn(List.of(product(1L, 9L), product(2L, 9L)));
        when(productMediaMapper.selectByProductIds(List.of(1L, 2L)))
                .thenReturn(List.of(media(5L, 1L, "a.jpg", 1), media(6L, 1L, "a.mp4", 2)));
        when(userMapper.selectByIds(any())).thenReturn(List.of(seller()));

        ProductService.ProductPage page = productService.listProductsPage("lamp", null, null, null, null, null, "newest", 1, 10);

        assertThat(page.total()).isEqualTo(12);
        ProductDTO first = page.items().get(0);
        assertThat(first.getUsername()).isEqualTo("seller9");
        assertThat(first.getCoverImage()).isEqualTo("a.jpg");
        assertThat(first.getImageUrls()).containsExactly("a.jpg");
        assertThat(first.getVideoUrls()).containsExactly("a.mp4");
        assertThat(first.getMedia()).hasSize(2);
        assertThat(page.items().get(1).getMedia()).isNull();
        verify(userMapper).selectByIds(any());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    void searchHitsKeepTheirRankAndSkipProductsNoLongerListed() {
        when(productMapper.selectByIds(List.of(3L, 1L, 2L))).thenReturn(List.of(product(1L, 9L), product(3L, 9L)));
        when(productMediaMapper.selectByProductIds(List.of(3L, 1L))).thenReturn(List.of());
        when(userMapper.selectByIds(any())).thenReturn(List.of(seller()));

        assertThat(productService.getListedProductDTOs(List.of(3L, 1L, 2L)))
                .extracting(ProductDTO::getId).containsExactly(3L, 1L);
    }

    @Test
    void noSearchHitsNeedNoQueries() {
        assertThat(productService.getListedProductDTOs(List.of())).isEmpty();
        assertThat(productService.getListedProductDTOs(null)).isEmpty();
        verifyNoInteractions(productMapper, productMediaMapper, userMapper);
    }

    @Test
    void oneProductIsLoadedWithItsMediaAndSeller() {
        when(productMapper.selectById(1L)).thenReturn(product(1L, 9L));
        when(productMediaMapper.selectByProductId(1L)).thenReturn(List.of(media(5L, 1L, "a.jpg", 1)));
        when(userMapper.selectById(9L)).thenReturn(seller());

        ProductDTO dto = productService.getProductDTOById(1L);

        assertThat(dto.getName()).isEqualTo("Desk lamp");
        assertThat(dto.getUsername()).isEqualTo("seller9");
        assertThat(dto.getCoverImage()).isEqualTo("a.jpg");
    }

    @Test
    void anUnknownProductConvertsToNull() {
        when(productMapper.selectById(1L)).thenReturn(null);

        assertThat(productService.getProductDTOById(1L)).isNull();
        verifyNoInteractions(productMediaMapper, userMapper);
    }

    @Test
    void aProductWithoutASellerIsConvertedWithoutLookingOneUp() {
        Product product = product(1L, null);

        assertThat(productService.convertToDTO(product).getUsername()).isNull();
        assertThat(productService.convertToDTOList(List.of(product))).hasSize(1);
        assertThat(productService.convertToDTOList(List.of())).isEmpty();
        verifyNoInteractions(userMapper);
    }

    @Test
    void listingAllAndBySellerLoadsEachProductsMedia() {
        when(productMapper.selectAll()).thenReturn(List.of(product(1L, 9L)));
        when(productMapper.selectByUserId(9L)).thenReturn(List.of(product(2L, 9L)));
        when(productMediaMapper.selectByProductId(1L)).thenReturn(List.of(media(5L, 1L, "a.jpg", 1)));
        when(productMediaMapper.selectByProductId(2L)).thenReturn(List.of());

        assertThat(productService.listAllProducts().get(0).getMedia()).hasSize(1);
        assertThat(productService.getProductsByUserId(9L).get(0).getMedia()).isEmpty();
    }

    @Test
    void deletingAProductRemovesItFromTheSearchIndex() {
        productService.deleteProduct(42L);

        verify(productMapper).deleteById(42L);
        verify(eventPublisher).publishEvent(any(ProductDeletedEvent.class));
    }

    @Test
    void deletingStillSucceedsWhenTheEventCannotBePublished() {
        doThrow(new IllegalStateException("no listener")).when(eventPublisher).publishEvent(any(ProductDeletedEvent.class));

        productService.deleteProduct(42L);

        verify(productMapper).deleteById(42L);
    }

    @Test
    void updatingAnUnknownProductIsRejected() {
        when(productMapper.selectById(42L)).thenReturn(null);

        assertThatThrownBy(() -> productService.updateProduct(42L, lampDto(2), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updatingWithoutFilesKeepsTheMediaAndStock() {
        Product existing = product(42L, 9L);
        existing.setStock(4);
        when(productMapper.selectById(42L)).thenReturn(existing);
        when(productMapper.update(existing)).thenReturn(1);
        when(productMediaMapper.selectByProductId(42L)).thenReturn(List.of(media(5L, 42L, "a.jpg", 1)));

        Product updated = productService.updateProduct(42L, lampDto(null), List.of());

        assertThat(updated.getStock()).isEqualTo(4);
        assertThat(updated.getLocation()).isEqualTo("Kent Ridge");
        assertThat(updated.getMedia()).hasSize(1);
        verify(productMediaMapper, never()).deleteById(any());
        verify(eventPublisher).publishEvent(any(ProductUpdatedEvent.class));
    }

    @Test
    void updatingWithNewFilesReplacesTheMedia() throws Exception {
        Product existing = product(42L, 9L);
        when(productMapper.selectById(42L)).thenReturn(existing);
        when(productMapper.update(existing)).thenReturn(1);
        when(productMediaMapper.selectByProductId(42L)).thenReturn(List.of(media(5L, 42L, "old.jpg", 1)));
        MockMultipartFile clip = file("lamp.MOV");
        when(fileStorageService.uploadFile(clip)).thenReturn("http://minio/c2c/lamp.mov");
        when(productMediaMapper.insert(any(ProductMedia.class))).thenReturn(1);

        Product updated = productService.updateProduct(42L, lampDto(2), List.of(clip));

        verify(productMediaMapper).deleteById(5L);
        assertThat(updated.getMedia()).extracting(ProductMedia::getMediaType).containsExactly(2);
        assertThat(updated.getStock()).isEqualTo(2);
    }

    @Test
    void aFailedUploadFailsTheUpdate() throws Exception {
        when(productMapper.selectById(42L)).thenReturn(product(42L, 9L));
        when(productMapper.update(any(Product.class))).thenReturn(1);
        when(productMediaMapper.selectByProductId(42L)).thenReturn(List.of());
        MockMultipartFile photo = file("lamp.jpg");
        when(fileStorageService.uploadFile(photo)).thenThrow(new IllegalStateException("storage down"));

        assertThatThrownBy(() -> productService.updateProduct(42L, lampDto(2), List.of(photo)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("storage down");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aFailedRowUpdateFailsTheUpdate() {
        when(productMapper.selectById(42L)).thenReturn(product(42L, 9L));
        when(productMapper.update(any(Product.class))).thenReturn(0);

        assertThatThrownBy(() -> productService.updateProduct(42L, lampDto(2), null))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void aSellerIsLookedUpByUsername() {
        when(userMapper.selectByUsername("seller9")).thenReturn(seller());

        assertThat(productService.getUserByUsername("seller9").getId()).isEqualTo(9L);
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/octet-stream", new byte[] {1});
    }

    private static ProductCreateDTO lampDto(Integer stock) {
        ProductCreateDTO dto = new ProductCreateDTO();
        dto.setName("Desk lamp");
        dto.setDescription("Warm white, barely used");
        dto.setPrice(new BigDecimal("15.00"));
        dto.setConditionLevel(2);
        dto.setLocation("Kent Ridge");
        dto.setCategory("Home");
        dto.setStock(stock);
        return dto;
    }

    private static Product product(Long id, Long sellerId) {
        Product product = new Product();
        product.setId(id);
        product.setUserId(sellerId);
        product.setName("Desk lamp");
        product.setPrice(new BigDecimal("15.00"));
        product.setStock(1);
        product.setStatus(1);
        return product;
    }

    private static ProductMedia media(Long id, Long productId, String url, int mediaType) {
        Product ref = new Product();
        ref.setId(productId);
        ProductMedia media = new ProductMedia();
        media.setId(id);
        media.setProduct(ref);
        media.setUrl(url);
        media.setMediaType(mediaType);
        media.setSortOrder(0);
        return media;
    }

    private static User seller() {
        User user = new User();
        user.setId(9L);
        user.setUsername("seller9");
        user.setDisplayName("Seller Nine");
        return user;
    }
}
