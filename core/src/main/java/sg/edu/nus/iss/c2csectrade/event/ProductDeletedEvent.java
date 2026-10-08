package sg.edu.nus.iss.c2csectrade.event;

import org.springframework.context.ApplicationEvent;

public class ProductDeletedEvent extends ApplicationEvent {
    private final Long productId;

    public ProductDeletedEvent(Object source, Long productId) {
        super(source);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
