package com.decade.doj.sandbox.worker;
import com.alibaba.fastjson.JSON;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.common.client.SubmissionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.ResourceAccessException;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import com.decade.doj.sandbox.service.ISandboxService;
import com.decade.doj.sandbox.service.TaskWorkspace;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
@Component
@RequiredArgsConstructor
public class JudgingWorker {
    private final ISandboxService sandbox;
    private final ReliableDelivery delivery;
    private final SubmissionClient submissions;
    @Value("${DOJ_INTERNAL_TOKEN:}") private String token;
    @Value("${DOJ_CODE_PATH:/app/static/codes/}") private String codePath;
    @RabbitListener(queues="judging.task.reliable.queue", containerFactory="reliableRabbitListenerContainerFactory",
            concurrency="${doj.thread-pool.judging-core-size:4}")
    public void receive(Message message, Channel channel) throws IOException {
        delivery.handle(message, channel, () -> {
            JudgingTask task = JSON.parseObject(new String(message.getBody(), StandardCharsets.UTF_8), JudgingTask.class);
            if (task == null || task.getSubmissionId() == null || task.getSubmissionId()<=0 || task.getUid() == null || task.getUid()<=0)
                throw new IllegalArgumentException("Invalid judging task");
            try {
                UserContext.setCurrentUser(task.getUid());
                var state=submissions.finished(task.getSubmissionId(),token);
                if(state==null || !state.success() || state.getData()==null) {
                    if(state==null || state.getCode()>=500 || state.getCode()==429) throw new ResourceAccessException("Judging state unavailable");
                    throw new IllegalArgumentException("Invalid judging state or service identity");
                }
                if(!state.getData()) {
                    try (TaskWorkspace workspace = TaskWorkspace.restore(codePath, task)) { sandbox.execute(task); }
                    catch (IOException failure) { throw new IllegalStateException("Worker storage unavailable", failure); }
                }
            } finally { UserContext.clear(); }
        });
    }
}
