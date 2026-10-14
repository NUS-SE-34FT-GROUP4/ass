package sg.edu.nus.iss.c2csectrade.search.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core declares the same queue (core RabbitMQConfig.searchProductQueue); its
 * test asserts the same arguments, since RabbitMQ refuses a second
 * declaration whose arguments differ.
 */
class RabbitMQConfigTest {

    private final Declarables topology = new RabbitMQConfig().productEventTopology();

    /** One Search task consumes at a time, so a product's events stay in order (#24). */
    @Test
    void queueHasASingleActiveConsumer() {
        Queue queue = topology.getDeclarablesByType(Queue.class).get(0);

        assertThat(queue.getName()).isEqualTo("search.product.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).isEqualTo(Map.of("x-single-active-consumer", true));
    }

    @Test
    void createdUpdatedAndDeletedAllReachTheQueue() {
        assertThat(topology.getDeclarablesByType(TopicExchange.class))
                .extracting(TopicExchange::getName).containsExactly("product.exchange");
        assertThat(topology.getDeclarablesByType(Binding.class))
                .allMatch(b -> b.getDestination().equals("search.product.queue")
                        && b.getExchange().equals("product.exchange"))
                .extracting(Binding::getRoutingKey)
                .containsExactly("product.created", "product.updated", "product.deleted");
    }
}
