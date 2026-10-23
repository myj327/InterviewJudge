package com.decade.doj.user.mq;
import com.alibaba.fastjson.JSON;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.user.service.IUserService;
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
    private final IUserService users;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final ReliableDelivery delivery;
    @RabbitListener(queues="problem.solved.reliable.queue", containerFactory="reliableRabbitListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, () -> onMessage(JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8))));
    }
    public void onMessage(Map<String, Object> message) {
        long userId = ((Number) message.get("userId")).longValue();
        long problemId = ((Number) message.get("problemId")).longValue();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                int inserted = jdbc.update("INSERT IGNORE INTO solved_problem(user_id,problem_id) VALUES (?,?)", userId, problemId);
                if (inserted == 1) users.handleProblemSolved(userId, problemId);
            });
            // Repeated deliveries repair the cache from committed facts without adding score twice.
            users.refreshRanking(userId);
        } finally { UserContext.clear(); }
    }
}
