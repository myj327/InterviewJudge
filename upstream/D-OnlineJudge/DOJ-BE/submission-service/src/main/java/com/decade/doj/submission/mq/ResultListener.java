package com.decade.doj.submission.mq;
import com.alibaba.fastjson.JSON;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.service.VerdictCompletion;
import com.decade.doj.submission.websocket.SubmissionWSServer;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
@Component
@RequiredArgsConstructor
public class ResultListener {
    private final VerdictCompletion completion;
    private final ReliableDelivery delivery;
    @RabbitListener(queues="judging.result.reliable.queue", containerFactory="reliableRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, () -> onMessage(JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8))));
    }
    public void onMessage(Map<String, Object> message) {
        Submission submission = completion.complete(message);
        if (submission != null) SubmissionWSServer.sendMessage(submission.getId(), JSON.toJSONString(submission));
    }
}
