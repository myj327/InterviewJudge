package com.decade.doj.common.mq;

import com.rabbitmq.client.Channel;
import feign.FeignException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpStatusCodeException;
import java.io.IOException;

public class ReliableDelivery {
    public void handle(Message message, Channel channel, Runnable action) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try { action.run(); }
            catch (RuntimeException failure) {
                if (Thread.currentThread().isInterrupted()) {
                    channel.basicNack(tag, false, true);
                    return;
                }
                if (failure instanceof DeferredDeliveryException) {
                    try { pause(500); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    channel.basicNack(tag, false, true);
                    return;
                }
                if (!isTransient(failure) || attempt == 3) {
                    channel.basicReject(tag, false);
                    return;
                }
                try { pause(250L << (attempt - 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    channel.basicNack(tag, false, true);
                    return;
                }
                continue;
            }
            // A failed ACK must leave the delivery unconfirmed, rather than repeat its action locally.
            channel.basicAck(tag, false);
            return;
        }
    }

    protected void pause(long millis) throws InterruptedException { Thread.sleep(millis); }

    public boolean isTransient(Throwable failure) {
        if (failure instanceof FeignException feign) return feign.status() == -1 || feign.status() == 429 || feign.status() >= 500;
        if (failure instanceof HttpStatusCodeException http) return http.getRawStatusCode() == 429 || http.getRawStatusCode() >= 500;
        if (failure instanceof AmqpException || failure instanceof TransientDataAccessException
                || failure instanceof DataAccessResourceFailureException || failure instanceof ResourceAccessException
                || failure instanceof IOException)
            return true;
        return failure.getCause() != null && isTransient(failure.getCause());
    }
}
