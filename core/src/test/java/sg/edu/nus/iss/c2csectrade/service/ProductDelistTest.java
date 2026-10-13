package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.event.ProductUpdatedEvent;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Search only returns products on sale, so it has to hear about a delisting:
 * without the event the index keeps the product at its old status.
 */
@ExtendWith(MockitoExtension.class)
class ProductDelistTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    void delistingPublishesTheProductWithItsNewStatus() {
        when(productMapper.selectById(42L)).thenReturn(productOnSale());

        productService.delistProduct(42L);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).update(saved.capture());
        assertThat(saved.getValue().getStatus()).isZero();

        ArgumentCaptor<ProductUpdatedEvent> published = ArgumentCaptor.forClass(ProductUpdatedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().getProduct().getId()).isEqualTo(42L);
        assertThat(published.getValue().getProduct().getStatus()).isZero();
    }

    @Test
    void delistingStillSucceedsWhenTheEventCannotBePublished() {
        when(productMapper.selectById(42L)).thenReturn(productOnSale());
        doThrow(new IllegalStateException("no listener")).when(eventPublisher).publishEvent(any(Object.class));

        productService.delistProduct(42L);

        verify(productMapper).update(any(Product.class));
    }

    @Test
    void delistingAnUnknownProductDoesNothing() {
        when(productMapper.selectById(42L)).thenReturn(null);

        productService.delistProduct(42L);

        verify(productMapper, never()).update(any(Product.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private static Product productOnSale() {
        Product product = new Product();
        product.setId(42L);
        product.setName("Desk lamp");
        product.setStatus(1);
        return product;
    }
}
