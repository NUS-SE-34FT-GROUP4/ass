package sg.edu.nus.iss.c2csectrade.search.event;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import sg.edu.nus.iss.c2csectrade.event.Events;
import sg.edu.nus.iss.c2csectrade.search.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.search.service.ProductIndexService;

import java.io.IOException;

/**
 * Keeps the search index in step with product changes published by Core.
 */
@Component
@RabbitListener(queues = RabbitMQConfig.SEARCH_PRODUCT_QUEUE)
public class ProductIndexConsumer {

    private final ProductIndexService indexService;

    public ProductIndexConsumer(ProductIndexService indexService) {
        this.indexService = indexService;
    }

    @RabbitHandler
    public void handleProductChanged(Events.ProductChangedEvent event) throws IOException {
        indexService.index(event);
    }

    @RabbitHandler
    public void handleProductDeleted(Events.ProductDeletedEvent event) throws IOException {
        indexService.delete(event.getProductId());
    }
}
