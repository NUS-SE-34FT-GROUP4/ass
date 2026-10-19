package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.CreditScoreResponse;
import sg.edu.nus.iss.c2csectrade.service.CreditScoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * A credit score is read as-is, or recomputed first and then read back.
 */
@ExtendWith(MockitoExtension.class)
class CreditScoreControllerTest {

    @Mock
    private CreditScoreService creditScoreService;

    @InjectMocks
    private CreditScoreController creditScoreController;

    @Test
    void aScoreIsRead() {
        CreditScoreResponse score = new CreditScoreResponse();
        when(creditScoreService.getUserCreditScore(9L)).thenReturn(score);

        assertThat((Map<Object, Object>) creditScoreController.getCreditScore(9L).getBody())
                .containsEntry("success", true).containsEntry("data", score);
    }

    @Test
    void aScoreIsRecomputedBeforeItIsReadBack() {
        CreditScoreResponse score = new CreditScoreResponse();
        when(creditScoreService.getUserCreditScore(9L)).thenReturn(score);

        assertThat((Map<Object, Object>) creditScoreController.updateCreditScore(9L).getBody()).containsEntry("data", score);

        InOrder order = inOrder(creditScoreService);
        order.verify(creditScoreService).updateCreditScore(9L);
        order.verify(creditScoreService).getUserCreditScore(9L);
    }

    @Test
    void aFailureIsA400WithTheReason() {
        when(creditScoreService.getUserCreditScore(8L)).thenThrow(new RuntimeException("db down"));
        doThrow(new RuntimeException("db down")).when(creditScoreService).updateCreditScore(8L);

        assertThat(creditScoreController.getCreditScore(8L).getStatusCode().value()).isEqualTo(400);
        assertThat((Map<Object, Object>) creditScoreController.updateCreditScore(8L).getBody()).containsEntry("message", "db down");
    }
}
