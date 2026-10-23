package com.decade.doj.common.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.amqp.support.converter.SimpleMessageConverter;

@Configuration
@AutoConfigureAfter(RabbitAutoConfiguration.class)
public class ReliableRabbitConfiguration {
    @Bean public ConfirmedPublisher confirmedPublisher(RabbitTemplate rabbit, ConnectionFactory connection) {
        CachingConnectionFactory cached = (CachingConnectionFactory) connection;
        cached.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        cached.setPublisherReturns(true);
        rabbit.setMandatory(true);
        return new ConfirmedPublisher(rabbit);
    }
    @Bean public ReliableDelivery reliableDelivery() { return new ReliableDelivery(); }
    @Bean public SimpleRabbitListenerContainerFactory reliableRabbitListenerContainerFactory(
            ConnectionFactory connection, ObjectProvider<MessageConverter> converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connection); factory.setMessageConverter(converter.getIfAvailable(SimpleMessageConverter::new));
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
    @Bean public Declarables reliableQueues() {
        List<Declarable> declarations = new ArrayList<>();
        TopicExchange exchange = new TopicExchange("doj.topic");
        DirectExchange dead = new DirectExchange("doj.dead");
        declarations.add(exchange); declarations.add(dead);
        for (String route : List.of("judging.task", "judging.result", "review.requested", "review.completed",
                "submission.created", "problem.solved")) {
            // New names avoid incompatible arguments on pre-existing R1 queues.
            Queue queue = QueueBuilder.durable(route + ".reliable.queue")
                    .deadLetterExchange("doj.dead").deadLetterRoutingKey(route).build();
            Queue dlq = QueueBuilder.durable(route + ".dlq").build();
            declarations.add(queue); declarations.add(dlq);
            declarations.add(BindingBuilder.bind(queue).to(exchange).with(route));
            declarations.add(BindingBuilder.bind(dlq).to(dead).with(route));
        }
        Queue failures = QueueBuilder.durable("processing.failure.queue")
                .deadLetterExchange("doj.dead").deadLetterRoutingKey("processing.failure").build();
        Queue failuresDlq = QueueBuilder.durable("processing.failure.dlq").build();
        declarations.add(failures); declarations.add(failuresDlq);
        declarations.add(BindingBuilder.bind(failuresDlq).to(dead).with("processing.failure"));
        for (String route : List.of("judging.task", "judging.result", "review.requested"))
            declarations.add(BindingBuilder.bind(failures).to(dead).with(route));
        return new Declarables(declarations);
    }
}
