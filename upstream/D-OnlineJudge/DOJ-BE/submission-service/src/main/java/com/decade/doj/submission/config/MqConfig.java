package com.decade.doj.submission.config;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.ConnectionFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class MqConfig {
    @Bean public ConnectionFactoryCustomizer boundedPublisherTransport() {
        return factory -> {
            factory.setConnectionTimeout(5000);
            factory.setHandshakeTimeout(5000);
            factory.setChannelRpcTimeout(5000);
            factory.setRequestedHeartbeat(10);
        };
    }
    @Bean public MessageConverter jsonMessageConverter() { return new Jackson2JsonMessageConverter(); }
}
