package com.decade.doj.submission.config;

import com.decade.doj.common.mq.ReliableRabbitConfiguration;
import com.rabbitmq.client.ChannelContinuationTimeoutException;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.TrafficListener;
import com.rabbitmq.client.impl.AMQConnection;
import com.rabbitmq.client.impl.ChannelN;
import com.rabbitmq.client.impl.ConsumerWorkService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PublisherTransportTimeoutTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withUserConfiguration(MqConfig.class, ReliableRabbitConfiguration.class)
            .withPropertyValues("spring.rabbitmq.dynamic=false", "spring.rabbitmq.listener.simple.auto-startup=false");

    @Test
    void bootAppliesFiniteTransportTimeoutsBeforeOpeningAnyConnection() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            CachingConnectionFactory cached = context.getBean(CachingConnectionFactory.class);
            ConnectionFactory raw = cached.getRabbitConnectionFactory();
            assertEquals(5000, raw.getConnectionTimeout());
            assertEquals(5000, raw.getHandshakeTimeout());
            assertEquals(5000, raw.getChannelRpcTimeout());
            assertEquals(10, raw.getRequestedHeartbeat());
            assertTrue(cached.isPublisherConfirms());
            assertFalse(cached.isSimplePublisherConfirms());
            assertTrue(cached.isPublisherReturns());
            assertFalse(raw.isAutomaticRecoveryEnabled());
        });
    }

    @Test
    void realConfirmSelectRpcWithoutReplyReturnsWithinTheConfiguredBound() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            int timeout = context.getBean(CachingConnectionFactory.class).getRabbitConnectionFactory().getChannelRpcTimeout();
            // Refuse the old ten-minute default before starting a blocking fixture.
            assertTrue(timeout > 0 && timeout <= 5000, "publisher RPC must have a short finite deadline");
            AMQConnection connection = mock(AMQConnection.class);
            when(connection.getChannelRpcTimeout()).thenReturn(timeout);
            when(connection.getTrafficListener()).thenReturn(TrafficListener.NO_OP);
            AtomicInteger transmitted = new AtomicInteger();
            doAnswer(call -> { transmitted.incrementAndGet(); return null; }).when(connection).writeFrame(any());
            ConsumerWorkService work = new ConsumerWorkService(mock(ExecutorService.class), Executors.defaultThreadFactory(), 0);
            try {
                ChannelN channel = new ChannelN(connection, 1, work);
                assertTimeout(Duration.ofSeconds(7), () ->
                        assertThrows(ChannelContinuationTimeoutException.class, channel::confirmSelect));
                assertTrue(transmitted.get() > 0, "exercise the real client RPC rather than throwing from a mock");
            } finally {
                work.shutdown();
            }
        });
    }
}
