package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import org.springframework.context.ApplicationEvent;

public class ProductUpdatedEvent extends ApplicationEvent {
    private final Product product;

    public ProductUpdatedEvent(Object source, Product product) {
        super(source);
        this.product = product;
    }

    public Product getProduct() {
        return product;
    }
}
