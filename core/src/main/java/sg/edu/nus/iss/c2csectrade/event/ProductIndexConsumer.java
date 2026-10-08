package sg.edu.nus.iss.c2csectrade.event;

import sg.edu.nus.iss.c2csectrade.config.RabbitMQConfig;
import sg.edu.nus.iss.c2csectrade.service.SearchService;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Keeps the search index in step with product changes published by Core.
 * Lives in Core until the Search service takes over the index; it then
 * moves to the search module unchanged.
 */
@Component
@RabbitListener(queues = RabbitMQConfig.SEARCH_PRODUCT_QUEUE)
public class ProductIndexConsumer {

    @Autowired
    private SearchService searchService;

    @RabbitHandler
    public void handleProductChanged(Events.ProductChangedEvent event) {
        searchService.indexProduct(event);
    }

    @RabbitHandler
    public void handleProductDeleted(Events.ProductDeletedEvent event) {
        searchService.deleteProduct(event.getProductId());
    }
}
