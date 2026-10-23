package com.decade.doj.submission.mq;
import com.alibaba.fastjson.JSON;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.service.ReviewCompletion;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
@Component
@RequiredArgsConstructor
public class ReviewRequestedListener {
    private final ReviewCompletion completion;
    private final ReliableDelivery delivery;
    @RabbitListener(queues="review.requested.reliable.queue", containerFactory="reliableRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, () -> onMessage(JSON.parseObject(new String(message.getBody(),
                StandardCharsets.UTF_8), ReviewEvidence.class)));
    }
    public void onMessage(ReviewEvidence evidence) { completion.complete(evidence); }
}
