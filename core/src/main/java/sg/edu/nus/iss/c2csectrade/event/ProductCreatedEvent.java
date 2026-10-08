package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import org.springframework.context.ApplicationEvent;

public class ProductCreatedEvent extends ApplicationEvent {
    private final Product product;

    public ProductCreatedEvent(Object source, Product product) {
        super(source);
        this.product = product;
    }

    public Product getProduct() {
        return product;
    }
}

