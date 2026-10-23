package com.decade.doj.sandbox.worker;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.sandbox.service.ISandboxService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.AmqpException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class JudgingWorkerTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;
    @Test void acknowledgesOnlyAfterExecuteAndRejectsUnconfirmedResult() throws Exception {
        ISandboxService sandbox=mock(ISandboxService.class);Channel channel=mock(Channel.class);
        MessageProperties props=new MessageProperties();props.setDeliveryTag(8);
        Message task=new Message("{\"submissionId\":100,\"uid\":7,\"filename\":\"Main.java\",\"code\":\"synthetic\",\"inputData\":\"\"}".getBytes(),props);
        var submissions=mock(com.decade.doj.common.client.SubmissionClient.class);
        when(submissions.finished(eq(100L),any())).thenReturn(com.decade.doj.common.domain.R.ok(false));
        var worker=new JudgingWorker(sandbox,new ReliableDelivery(),submissions);
        org.springframework.test.util.ReflectionTestUtils.setField(worker,"codePath",root.toString());
        worker.receive(task,channel);
        var order=inOrder(sandbox,channel);order.verify(sandbox).execute(any());order.verify(channel).basicAck(8,false);
        reset(sandbox,channel);doThrow(new AmqpException("result publish unavailable")).when(sandbox).execute(any());
        worker.receive(task,channel);
        verify(sandbox,times(3)).execute(any());verify(channel).basicReject(8,false);verify(channel,never()).basicAck(anyLong(),anyBoolean());
        assertNull(UserContext.getCurrentUser());
    }
}
