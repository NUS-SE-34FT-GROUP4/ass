package sg.edu.nus.iss.c2csectrade.search.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.search.document.ProductDocument;
import sg.edu.nus.iss.c2csectrade.search.service.ProductSearchQuery;
import sg.edu.nus.iss.c2csectrade.search.service.ProductSearchService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductSearchService searchService;

    @Test
    void passesEveryParameterToTheService() throws Exception {
        ProductDocument doc = new ProductDocument();
        doc.setProductId(42L);
        doc.setHighlightedName("Desk <em class='highlight'>lamp</em>");
        when(searchService.search(any())).thenReturn(List.of(doc));

        mvc.perform(get("/api/search")
                        .param("keyword", "lamp")
                        .param("minPrice", "10")
                        .param("maxPrice", "30.5")
                        .param("conditionLevel", "4")
                        .param("location", "Kent Ridge")
                        .param("category", "Home,Books")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value(42))
                .andExpect(jsonPath("$[0].highlightedName").value("Desk <em class='highlight'>lamp</em>"));

        verify(searchService).search(new ProductSearchQuery("lamp", new BigDecimal("10"), new BigDecimal("30.5"),
                4, "Kent Ridge", "Home,Books", 1, 5));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        when(searchService.search(any())).thenReturn(List.of());

        mvc.perform(get("/api/search").param("size", "10000").param("page", "-3"))
                .andExpect(status().isOk());

        verify(searchService).search(new ProductSearchQuery(null, null, null, null, null, null,
                0, SearchController.MAX_PAGE_SIZE));
    }

    @Test
    void unreachableIndexIsServiceUnavailable() throws Exception {
        when(searchService.search(any())).thenThrow(new IOException("Connection refused"));

        mvc.perform(get("/api/search").param("keyword", "lamp"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Search is temporarily unavailable"));
    }

    @Test
    void malformedPriceIsBadRequest() throws Exception {
        mvc.perform(get("/api/search").param("minPrice", "cheap"))
                .andExpect(status().isBadRequest());
    }
}
