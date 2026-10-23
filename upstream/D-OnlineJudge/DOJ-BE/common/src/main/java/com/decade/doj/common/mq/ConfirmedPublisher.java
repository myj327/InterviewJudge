package com.decade.doj.common.mq;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.concurrent.TimeUnit;

public class ConfirmedPublisher {
    private final RabbitTemplate rabbit;
    public ConfirmedPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }

    public void publish(String routingKey, Object body) {
        CorrelationData correlation = new CorrelationData();
        rabbit.convertAndSend("doj.topic", routingKey, body, message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(correlation.getId());
            return message;
        }, correlation);
        try {
            if (!correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() || correlation.getReturned() != null)
                throw new AmqpException("Message was not confirmed and routed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Publisher interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException unavailable) {
            throw new AmqpException("Publisher confirmation unavailable", unavailable);
        }
    }
}
