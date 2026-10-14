package sg.edu.nus.iss.c2csectrade.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.SearchClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The home page product list: one page at a time, the total in X-Total-Count,
 * keyword search through the Search service.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerSearchTest {

    @Mock
    private ProductService productService;

    @Mock
    private SearchClient searchClient;

    @InjectMocks
    private ProductController controller;

    private ResponseEntity<List<ProductDTO>> list(String keyword, String sort, int page, int size) {
        return controller.listProducts(keyword, null, null, null, null, null, sort, page, size);
    }

    @Test
    void keywordResultsComeFromSearchInItsOrderWithHighlights() {
        when(searchClient.search("lamp", new BigDecimal("10"), null, null, null, "Home", 100)).thenReturn(List.of(
                new SearchClient.Hit(2L, "Desk <em class='highlight'>lamp</em>", "LED"),
                new SearchClient.Hit(1L, "Floor <em class='highlight'>lamp</em>", "Tall")));
        when(productService.getListedProductDTOs(List.of(2L, 1L))).thenReturn(List.of(dto(2L, "5"), dto(1L, "9")));

        ResponseEntity<List<ProductDTO>> response = controller.listProducts("lamp", new BigDecimal("10"), null,
                null, null, "Home", "default", 0, 12);

        assertThat(response.getBody()).extracting(ProductDTO::getId).containsExactly(2L, 1L);
        assertThat(response.getBody().get(0).getHighlightedName()).isEqualTo("Desk <em class='highlight'>lamp</em>");
        assertThat(response.getBody().get(0).getHighlightedDescription()).isEqualTo("LED");
        assertThat(response.getHeaders().getFirst(ProductController.TOTAL_COUNT_HEADER)).isEqualTo("2");
    }

    /** Indexed but deleted or sold out since: the database no longer lists it. */
    @Test
    void hitsNoLongerListedAreSkipped() {
        when(searchClient.search("lamp", null, null, null, null, null, 100)).thenReturn(List.of(
                new SearchClient.Hit(1L, "a", "b"), new SearchClient.Hit(99L, "c", "d")));
        when(productService.getListedProductDTOs(List.of(1L, 99L))).thenReturn(List.of(dto(1L, "1")));

        assertThat(list("lamp", "default", 0, 12).getBody()).extracting(ProductDTO::getId).containsExactly(1L);
    }

    @Test
    void searchResultsArePagedAndSortedAcrossAllHits() {
        when(searchClient.search("lamp", null, null, null, null, null, 100)).thenReturn(List.of(
                new SearchClient.Hit(1L, "a", "a"), new SearchClient.Hit(2L, "b", "b"),
                new SearchClient.Hit(3L, "c", "c")));
        when(productService.getListedProductDTOs(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(dto(1L, "30"), dto(2L, "10"), dto(3L, "20")));

        ResponseEntity<List<ProductDTO>> second = list("lamp", "price-asc", 1, 2);

        assertThat(second.getBody()).extracting(ProductDTO::getId).containsExactly(1L); // 10, 20 | 30
        assertThat(second.getHeaders().getFirst(ProductController.TOTAL_COUNT_HEADER)).isEqualTo("3");
    }

    @Test
    void searchHitsCanBeSortedByPriceDescendingOrByAge() {
        when(searchClient.search("lamp", null, null, null, null, null, 100)).thenReturn(List.of(
                new SearchClient.Hit(1L, "a", "a"), new SearchClient.Hit(2L, "b", "b"),
                new SearchClient.Hit(3L, "c", "c")));
        ProductDTO older = dto(1L, "10");
        older.setCreateTime(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        ProductDTO newest = dto(2L, "30");
        newest.setCreateTime(java.time.LocalDateTime.of(2026, 3, 1, 0, 0));
        ProductDTO middle = dto(3L, "20");
        middle.setCreateTime(java.time.LocalDateTime.of(2026, 2, 1, 0, 0));
        when(productService.getListedProductDTOs(List.of(1L, 2L, 3L))).thenReturn(List.of(older, newest, middle));

        assertThat(list("lamp", "price-desc", 0, 12).getBody()).extracting(ProductDTO::getId).containsExactly(2L, 3L, 1L);
        assertThat(list("lamp", "time-asc", 0, 12).getBody()).extracting(ProductDTO::getId).containsExactly(1L, 3L, 2L);
        assertThat(list("lamp", "time-desc", 0, 12).getBody()).extracting(ProductDTO::getId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void searchDownFallsBackToTheDatabasePage() {
        when(searchClient.search(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .thenThrow(new SearchClient.SearchUnavailableException(new ResourceAccessException("refused")));
        List<ProductDTO> dtos = List.of(dto(5L, "1"));
        when(productService.listProductsPage("lamp", null, null, 4, null, null, "default", 0, 12))
                .thenReturn(new ProductService.ProductPage(dtos, 40));

        ResponseEntity<List<ProductDTO>> response = controller.listProducts("lamp", null, null, 4, null, null,
                "default", 0, 12);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(dtos);
        assertThat(response.getHeaders().getFirst(ProductController.TOTAL_COUNT_HEADER)).isEqualTo("40");
    }

    @Test
    void withoutKeywordTheDatabaseAnswersOnePage() {
        when(productService.listProductsPage(null, null, null, null, "Clementi", null, "time-asc", 2, 12))
                .thenReturn(new ProductService.ProductPage(List.of(dto(5L, "1")), 10_001));

        ResponseEntity<List<ProductDTO>> response = controller.listProducts(null, null, null, null, "Clementi",
                null, "time-asc", 2, 12);

        assertThat(response.getHeaders().getFirst(ProductController.TOTAL_COUNT_HEADER)).isEqualTo("10001");
        verify(productService).listProductsPage(null, null, null, null, "Clementi", null, "time-asc", 2, 12);
        verifyNoInteractions(searchClient);
    }

    @Test
    void rejectsPagesThatAreTooLargeNegativeOrUnknownSorts() {
        assertThat(list(null, "default", 0, ProductController.MAX_PAGE_SIZE + 1).getStatusCode().value()).isEqualTo(400);
        assertThat(list(null, "default", -1, 12).getStatusCode().value()).isEqualTo(400);
        assertThat(list(null, "default", 0, 0).getStatusCode().value()).isEqualTo(400);
        assertThat(list(null, "price; DROP TABLE users", 0, 12).getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(productService, searchClient);
    }

    private static ProductDTO dto(Long id, String price) {
        ProductDTO dto = new ProductDTO();
        dto.setId(id);
        dto.setPrice(new BigDecimal(price));
        return dto;
    }
}
