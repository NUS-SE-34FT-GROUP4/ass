package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.entity.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards in-process product events to RabbitMQ so the Search service can
 * keep its index up to date. Events go out only after the transaction
 * commits, so Search never indexes a change that was rolled back;
 * fallbackExecution covers callers that run without a transaction.
 */
@Component
public class ProductEventListener {

    @Autowired
    private EventPublisher eventPublisher;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void handleProductCreated(ProductCreatedEvent event) {
        eventPublisher.publishProductCreated(toChangedEvent(event.getProduct()));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void handleProductUpdated(ProductUpdatedEvent event) {
        eventPublisher.publishProductUpdated(toChangedEvent(event.getProduct()));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void handleProductDeleted(ProductDeletedEvent event) {
        eventPublisher.publishProductDeleted(new Events.ProductDeletedEvent(event.getProductId()));
    }

    static Events.ProductChangedEvent toChangedEvent(Product product) {
        return new Events.ProductChangedEvent(
                product.getId(),
                product.getUserId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getConditionLevel(),
                product.getLocation(),
                product.getCategory(),
                product.getStatus(),
                product.getCreatedAt());
    }
}
