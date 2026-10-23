package com.decade.doj.common.mq;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReliableDeliveryTest {
    private Message message() { MessageProperties props=new MessageProperties(); props.setDeliveryTag(9); return new Message(new byte[0],props); }
    @Test void acknowledgesOnlyAfterWorkAndRetriesOnlyTransientErrors() throws Exception {
        Channel channel=mock(Channel.class); AtomicInteger attempts=new AtomicInteger();
        new ReliableDelivery().handle(message(),channel,()->{ if(attempts.incrementAndGet()<3)throw new AmqpException("synthetic unavailable"); });
        assertEquals(3,attempts.get()); verify(channel).basicAck(9,false); verify(channel,never()).basicReject(anyLong(),anyBoolean());
        Channel poison=mock(Channel.class); AtomicInteger invalid=new AtomicInteger();
        new ReliableDelivery().handle(message(),poison,()->{invalid.incrementAndGet();throw new IllegalArgumentException("invalid");});
        assertEquals(1,invalid.get());verify(poison).basicReject(9,false);verify(poison,never()).basicAck(anyLong(),anyBoolean());
    }
    @Test void exhaustedRetriesGoToDlqAndInterruptLeavesNoAck() throws Exception {
        Channel channel=mock(Channel.class); AtomicInteger attempts=new AtomicInteger();
        new ReliableDelivery().handle(message(),channel,()->{attempts.incrementAndGet();throw new AmqpException("unavailable");});
        assertEquals(3,attempts.get());verify(channel).basicReject(9,false);
        Channel interrupted=mock(Channel.class);
        try {
            new ReliableDelivery().handle(message(),interrupted,()->{Thread.currentThread().interrupt();throw new AmqpException("interrupted");});
            verify(interrupted).basicNack(9,false,true);verify(interrupted,never()).basicAck(anyLong(),anyBoolean());
        } finally {Thread.interrupted();}
    }
    @Test void durableTopologyDeclaresSixDeadLetterRoutes() {
        Declarables declared=new ReliableRabbitConfiguration().reliableQueues();
        assertEquals(14,declared.getDeclarablesByType(Queue.class).size());
        assertEquals(16,declared.getDeclarablesByType(Binding.class).size());
        for(Queue queue:declared.getDeclarablesByType(Queue.class)) {
            assertTrue(queue.isDurable());
            if(queue.getName().endsWith(".reliable.queue"))assertEquals("doj.dead",queue.getArguments().get("x-dead-letter-exchange"));
        }
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(ReliableRabbitConfiguration.class)
                .withBean(org.springframework.amqp.rabbit.connection.ConnectionFactory.class,
                        () -> mock(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.class))
                .withBean(org.springframework.amqp.rabbit.core.RabbitTemplate.class,
                        () -> mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean("reliableRabbitListenerContainerFactory"));
                });
    }
}
