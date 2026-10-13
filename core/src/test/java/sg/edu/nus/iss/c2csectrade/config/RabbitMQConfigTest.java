package sg.edu.nus.iss.c2csectrade.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    /**
     * Search declares this queue too (search RabbitMQConfig). RabbitMQ refuses
     * a second declaration whose arguments differ, so both sides assert the
     * same ones: durable, single active consumer (#24).
     */
    @Test
    void searchQueueMatchesTheSearchServiceDeclaration() {
        Queue queue = new RabbitMQConfig().searchProductQueue();

        assertThat(queue.getName()).isEqualTo("search.product.queue");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).isEqualTo(Map.of("x-single-active-consumer", true));
    }
}
