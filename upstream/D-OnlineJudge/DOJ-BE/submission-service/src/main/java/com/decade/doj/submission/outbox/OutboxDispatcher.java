package com.decade.doj.submission.outbox;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.mq.ConfirmedPublisher;
import com.decade.doj.common.mq.ReliableDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {
    private final Outbox outbox;
    private final ConfirmedPublisher publisher;
    private final ReliableDelivery failures;

    @Scheduled(fixedDelay = 500)
    public void dispatch() {
        for (int batch = 0; batch < 20 && !Thread.currentThread().isInterrupted(); batch++) {
            Outbox.Event event = outbox.claim();
            if (event == null) return;
            try {
                publisher.publish(event.route(), JSON.parse(event.payload()));
                outbox.sent(event);
            } catch (RuntimeException failure) {
                outbox.failed(event, failures.isTransient(failure));
                log.warn("Outbox id={} attempt={} failureType={}", event.id(), event.attempt(), failure.getClass().getSimpleName());
            }
        }
    }
}
