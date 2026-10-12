package sg.edu.nus.iss.c2csectrade.search.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.search.service.ProductReindexService;

import java.io.IOException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReindexController.class)
class ReindexControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductReindexService reindexService;

    @Test
    void reportsWhatWasRebuilt() throws Exception {
        when(reindexService.rebuild()).thenReturn(new ProductReindexService.Result(12, 2));

        mvc.perform(post("/internal/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(12))
                .andExpect(jsonPath("$.removed").value(2));
    }

    @Test
    void secondRebuildWhileOneRunsIsRefused() throws Exception {
        when(reindexService.rebuild()).thenThrow(new ProductReindexService.ReindexInProgressException());

        mvc.perform(post("/internal/reindex")).andExpect(status().isConflict());
    }

    @Test
    void unreachableIndexIsUnavailable() throws Exception {
        when(reindexService.rebuild()).thenThrow(new IOException("connection refused"));

        mvc.perform(post("/internal/reindex")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void unreachableDatabaseIsUnavailable() throws Exception {
        when(reindexService.rebuild()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        mvc.perform(post("/internal/reindex")).andExpect(status().isServiceUnavailable());
    }
}
