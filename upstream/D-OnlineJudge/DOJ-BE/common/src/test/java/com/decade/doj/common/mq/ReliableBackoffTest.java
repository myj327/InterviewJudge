package com.decade.doj.common.mq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReliableBackoffTest {
    Message message() { MessageProperties p=new MessageProperties();p.setDeliveryTag(9);return new Message(new byte[0],p); }
    static class TrackingDelivery extends ReliableDelivery {
        final List<Long> waits=new ArrayList<>(); boolean interrupt;
        protected void pause(long millis) throws InterruptedException {
            waits.add(millis); if(interrupt)throw new InterruptedException("synthetic stop");
        }
    }
    @Test void transientRetriesWaitAndPermanentFailuresDoNotRetry() throws Exception {
        TrackingDelivery delivery=new TrackingDelivery();Channel channel=mock(Channel.class);AtomicInteger calls=new AtomicInteger();
        delivery.handle(message(),channel,()->{calls.incrementAndGet();throw new AmqpException("synthetic outage");});
        assertEquals(3,calls.get());assertEquals(List.of(250L,500L),delivery.waits);verify(channel).basicReject(9,false);
        delivery.waits.clear();calls.set(0);Channel permanent=mock(Channel.class);
        delivery.handle(message(),permanent,()->{calls.incrementAndGet();throw new IllegalArgumentException("bad request");});
        assertEquals(1,calls.get());assertTrue(delivery.waits.isEmpty());verify(permanent).basicReject(9,false);
    }
    @Test void interruptionWhileWaitingRequeuesWithoutAcknowledging() throws Exception {
        TrackingDelivery delivery=new TrackingDelivery();delivery.interrupt=true;Channel channel=mock(Channel.class);
        try {
            delivery.handle(message(),channel,()->{throw new AmqpException("synthetic outage");});
            assertTrue(Thread.currentThread().isInterrupted());verify(channel).basicNack(9,false,true);
            verify(channel,never()).basicAck(anyLong(),anyBoolean());verify(channel,never()).basicReject(anyLong(),anyBoolean());
        } finally { Thread.interrupted(); }
    }
}
