package com.decade.doj.common.mq;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ConfirmedPublisherTest {
    @Test void persistentPublishWaitsForConfirmedRoutingAndRejectsReturns() {
        RabbitTemplate rabbit=mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData correlation=call.getArgument(4);
            Message message=((MessagePostProcessor)call.getArgument(3)).postProcessMessage(new Message(new byte[0],new MessageProperties()));
            assertEquals(MessageDeliveryMode.PERSISTENT,message.getMessageProperties().getDeliveryMode());
            assertNotNull(message.getMessageProperties().getMessageId());
            correlation.getFuture().set(new CorrelationData.Confirm(true,null));return null;
        }).when(rabbit).convertAndSend(eq("doj.topic"),eq("route"),any(),any(MessagePostProcessor.class),any(CorrelationData.class));
        assertDoesNotThrow(()->new ConfirmedPublisher(rabbit).publish("route","synthetic"));
        doAnswer(call -> {
            CorrelationData correlation=call.getArgument(4);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0],new MessageProperties()),312,"NO_ROUTE","doj.topic","route"));
            correlation.getFuture().set(new CorrelationData.Confirm(true,null));return null;
        }).when(rabbit).convertAndSend(eq("doj.topic"),eq("route"),any(),any(MessagePostProcessor.class),any(CorrelationData.class));
        assertThrows(AmqpException.class,()->new ConfirmedPublisher(rabbit).publish("route","synthetic"));
    }
}
