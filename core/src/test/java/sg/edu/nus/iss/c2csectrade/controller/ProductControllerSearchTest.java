package sg.edu.nus.iss.c2csectrade.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.SearchClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The home page product list: keyword search through the Search service. */
@ExtendWith(MockitoExtension.class)
class ProductControllerSearchTest {

    @Mock
    private ProductService productService;

    @Mock
    private SearchClient searchClient;

    @InjectMocks
    private ProductController controller;

    @Test
    void keywordResultsComeFromSearchInItsOrderWithHighlights() {
        when(searchClient.search("lamp", new BigDecimal("10"), null, null, null, "Home", 100)).thenReturn(List.of(
                new SearchClient.Hit(2L, "Desk <em class='highlight'>lamp</em>", "LED"),
                new SearchClient.Hit(1L, "Floor <em class='highlight'>lamp</em>", "Tall")));
        stubProduct(1L);
        stubProduct(2L);

        ResponseEntity<List<ProductDTO>> response = controller.listProducts("lamp", new BigDecimal("10"), null,
                null, null, "Home");

        assertThat(response.getBody()).extracting(ProductDTO::getId).containsExactly(2L, 1L);
        assertThat(response.getBody().get(0).getHighlightedName()).isEqualTo("Desk <em class='highlight'>lamp</em>");
        assertThat(response.getBody().get(0).getHighlightedDescription()).isEqualTo("LED");
    }

    /** Indexed but deleted since: the database has no such product any more. */
    @Test
    void hitsMissingFromTheDatabaseAreSkipped() {
        when(searchClient.search("lamp", null, null, null, null, null, 100)).thenReturn(List.of(
                new SearchClient.Hit(1L, "a", "b"), new SearchClient.Hit(99L, "c", "d")));
        stubProduct(1L);
        when(productService.getProductById(99L)).thenReturn(null);

        ResponseEntity<List<ProductDTO>> response = controller.listProducts("lamp", null, null, null, null, null);

        assertThat(response.getBody()).extracting(ProductDTO::getId).containsExactly(1L);
    }

    @Test
    void searchDownFallsBackToTheDatabase() {
        when(searchClient.search(any(), any(), any(), any(), any(), any(), any(Integer.class)))
                .thenThrow(new SearchClient.SearchUnavailableException(new ResourceAccessException("refused")));
        List<Product> fromDatabase = List.of(product(5L));
        List<ProductDTO> dtos = List.of(dto(5L));
        when(productService.listProductsWithFilters("lamp", null, null, 4, null, null)).thenReturn(fromDatabase);
        when(productService.convertToDTOList(fromDatabase)).thenReturn(dtos);

        ResponseEntity<List<ProductDTO>> response = controller.listProducts("lamp", null, null, 4, null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(dtos);
    }

    @Test
    void withoutKeywordTheDatabaseAnswers() {
        List<Product> fromDatabase = List.of(product(5L));
        when(productService.listProductsWithFilters(null, null, null, null, "Clementi", null)).thenReturn(fromDatabase);
        when(productService.convertToDTOList(fromDatabase)).thenReturn(List.of(dto(5L)));

        controller.listProducts(null, null, null, null, "Clementi", null);

        verify(productService).listProductsWithFilters(null, null, null, null, "Clementi", null);
        verifyNoInteractions(searchClient);
    }

    private void stubProduct(Long id) {
        Product product = product(id);
        when(productService.getProductById(id)).thenReturn(product);
        when(productService.convertToDTO(product)).thenReturn(dto(id));
    }

    private static Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        return product;
    }

    private static ProductDTO dto(Long id) {
        ProductDTO dto = new ProductDTO();
        dto.setId(id);
        return dto;
    }
}
