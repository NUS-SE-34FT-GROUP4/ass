package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ProductEventListenerTest {

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ProductEventListener listener;

    @Test
    void createdProductIsPublishedWithEveryIndexedField() {
        Product product = product();

        listener.handleProductCreated(new ProductCreatedEvent(this, product));

        ArgumentCaptor<Events.ProductChangedEvent> sent = ArgumentCaptor.forClass(Events.ProductChangedEvent.class);
        verify(eventPublisher).publishProductCreated(sent.capture());
        verifyNoMoreInteractions(eventPublisher);
        assertThat(sent.getValue()).isEqualTo(new Events.ProductChangedEvent(
                42L, 7L, "Desk lamp", "Barely used", new BigDecimal("12.50"),
                9, "Kent Ridge", "Home", 1, product.getCreatedAt()));
    }

    @Test
    void updatedProductGoesOutOnTheUpdatedKey() {
        listener.handleProductUpdated(new ProductUpdatedEvent(this, product()));

        verify(eventPublisher).publishProductUpdated(ProductEventListener.toChangedEvent(product()));
        verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void deletedProductCarriesOnlyItsId() {
        listener.handleProductDeleted(new ProductDeletedEvent(this, 42L));

        verify(eventPublisher).publishProductDeleted(new Events.ProductDeletedEvent(42L));
        verifyNoMoreInteractions(eventPublisher);
    }

    private static Product product() {
        Product product = new Product();
        product.setId(42L);
        product.setUserId(7L);
        product.setName("Desk lamp");
        product.setDescription("Barely used");
        product.setPrice(new BigDecimal("12.50"));
        product.setConditionLevel(9);
        product.setLocation("Kent Ridge");
        product.setCategory("Home");
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.of(2026, 10, 8, 9, 30));
        return product;
    }
}
