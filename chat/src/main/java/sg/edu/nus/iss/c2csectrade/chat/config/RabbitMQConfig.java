package sg.edu.nus.iss.c2csectrade.chat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration for Chat Service
 * Handles fanout-based cross-instance chat message delivery
 */
@Configuration
public class RabbitMQConfig {

    // ============ Chat Fanout (cross-instance delivery) ============
    public static final String CHAT_FANOUT_EXCHANGE = "chat.fanout.exchange";

    /**
     * JSON Message Converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("sg.edu.nus.iss.c2csectrade.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    /**
     * RabbitTemplate with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ============ Chat Fanout Exchange and Queue ============

    @Bean
    public FanoutExchange chatFanoutExchange() {
        return new FanoutExchange(CHAT_FANOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue chatFanoutQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding chatFanoutBinding() {
        return BindingBuilder.bind(chatFanoutQueue())
                .to(chatFanoutExchange());
    }
}
