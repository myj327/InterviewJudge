package com.decade.doj.problem.mq;
import com.alibaba.fastjson.JSON;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.problem.service.IProblemService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
@Component
@RequiredArgsConstructor
public class StatsUpdateListener {
    private final IProblemService problems;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final ReliableDelivery delivery;
    @RabbitListener(queues="submission.created.reliable.queue", containerFactory="reliableRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, () -> onMessage(JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8))));
    }
    public void onMessage(Map<String, Object> message) {
        long submissionId = ((Number) message.get("submissionId")).longValue();
        long problemId = ((Number) message.get("problemId")).longValue();
        boolean accepted = (Boolean) message.get("isAccepted");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            int inserted = jdbc.update("INSERT IGNORE INTO stats_inbox(submission_id) VALUES (?)", submissionId);
            if (inserted == 1) problems.updateProblemStats(problemId, accepted);
        });
    }
}
