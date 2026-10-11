package sg.edu.nus.iss.c2csectrade.search.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * The product events Search consumes. Names and arguments must match Core's
 * RabbitMQConfig: both services declare the queue and its bindings, so that
 * neither depends on the other starting first, and a declaration that differs
 * fails at startup.
 */
@Configuration
public class RabbitMQConfig {

    public static final String PRODUCT_EXCHANGE = "product.exchange";
    public static final String SEARCH_PRODUCT_QUEUE = "search.product.queue";
    static final String[] ROUTING_KEYS = {"product.created", "product.updated", "product.deleted"};

    /**
     * One queue for all three kinds of message keeps them in order, so a
     * delete never overtakes an earlier update. The listener picks the
     * handler from the type header; trust only the event package.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("sg.edu.nus.iss.c2csectrade.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public Declarables productEventTopology() {
        TopicExchange exchange = new TopicExchange(PRODUCT_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(SEARCH_PRODUCT_QUEUE).build();
        List<Declarable> declarables = new ArrayList<>(List.of(exchange, queue));
        for (String key : ROUTING_KEYS) {
            declarables.add(BindingBuilder.bind(queue).to(exchange).with(key));
        }
        return new Declarables(declarables);
    }
}
