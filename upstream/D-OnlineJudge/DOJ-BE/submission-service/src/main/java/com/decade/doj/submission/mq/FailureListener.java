package com.decade.doj.submission.mq;

import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.service.ProcessingFailures;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class FailureListener {
    private static final Set<String> ROUTES = Set.of("judging.task", "judging.result", "review.requested");
    private final ProcessingFailures failures;
    private final ReliableDelivery delivery;
    private final RabbitTemplate rabbit;

    @RabbitListener(queues="processing.failure.queue", containerFactory="reliableRabbitListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        delivery.handle(message,channel,()->failures.record(route(message.getMessageProperties().getHeaders()),
                new String(message.getBody(), StandardCharsets.UTF_8)));
    }

    // Only repair the projection copy; original judging/model DLQs remain untouched.
    @Scheduled(fixedDelay=5000)
    public void reconcile() {
        try {
            rabbit.execute(channel -> {
                var message=channel.basicGet("processing.failure.dlq",false);
                if(message==null) return null;
                long tag=message.getEnvelope().getDeliveryTag();
                try {
                    failures.record(route(message.getProps().getHeaders()),
                            new String(message.getBody(),StandardCharsets.UTF_8));
                } catch (RuntimeException failure) {
                    if(delivery.isTransient(failure) || failure instanceof com.decade.doj.common.mq.DeferredDeliveryException)
                        channel.basicNack(tag,false,true);
                    else channel.basicReject(tag,false);
                    return null;
                }
                channel.basicAck(tag,false);
                return null;
            });
        } catch (RuntimeException failure) {
            log.warn("Failure projection unavailable: {}",failure.getClass().getSimpleName());
        }
    }

    private String route(Map<String,Object> headers) {
        Object first=headers==null ? null : headers.get("x-first-death-queue");
        String queue=String.valueOf(first);
        for(String route:ROUTES) if(queue.equals(route+".reliable.queue")) return route;
        return "";
    }
}
