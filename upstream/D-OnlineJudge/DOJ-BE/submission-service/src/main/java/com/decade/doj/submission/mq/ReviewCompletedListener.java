package com.decade.doj.submission.mq;

import com.decade.doj.submission.domain.dto.ReviewReport;
import com.decade.doj.submission.repository.ReviewReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import com.decade.doj.common.mq.ReliableDelivery;
import com.alibaba.fastjson.JSON;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewCompletedListener {

    private final ReviewReportRepository reviewReportRepository;
    private final ReliableDelivery delivery;

    @RabbitListener(queues="review.completed.reliable.queue", containerFactory="reliableRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, () -> onMessage(JSON.parseObject(new String(message.getBody(),
                StandardCharsets.UTF_8), ReviewReport.class)));
    }
    public void onMessage(ReviewReport report) {
        boolean inserted = reviewReportRepository.save(report);
        log.info("Review report submissionId={} promptVersion={} inserted={}", report.getSubmissionId(),
            report.getPromptVersion(), inserted);
    }
}
