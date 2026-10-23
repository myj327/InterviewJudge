package com.decade.doj.sandbox.worker;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.sandbox.service.ISandboxService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class FinishedTaskDeliveryTest {
    @Test void completedTaskAcknowledgesWithoutOpeningDeletedSource() throws Exception {
        var sandbox=mock(ISandboxService.class);var client=mock(SubmissionClient.class);var channel=mock(com.rabbitmq.client.Channel.class);
        when(client.finished(eq(91L),any())).thenReturn(R.ok(true));var props=new MessageProperties();props.setDeliveryTag(8);
        new JudgingWorker(sandbox,new ReliableDelivery(),client).receive(new Message("{\"submissionId\":91,\"uid\":7}".getBytes(),props),channel);
        verifyNoInteractions(sandbox);verify(channel).basicAck(8,false);assertNull(UserContext.getCurrentUser());
    }
    @Test void unavailableStateIsBoundedAndUnknownTaskDoesNotRun() throws Exception {
        var sandbox=mock(ISandboxService.class);var client=mock(SubmissionClient.class);var channel=mock(com.rabbitmq.client.Channel.class);
        var props=new MessageProperties();props.setDeliveryTag(8);var msg=new Message("{\"submissionId\":91,\"uid\":7}".getBytes(),props);
        var worker=new JudgingWorker(sandbox,new ReliableDelivery(),client);
        when(client.finished(eq(91L),any())).thenReturn(R.error("offline"));worker.receive(msg,channel);
        verify(client,times(3)).finished(eq(91L),any());verify(channel).basicReject(8,false);verifyNoInteractions(sandbox);
        reset(client,channel);when(client.finished(eq(91L),any())).thenReturn(R.error(404,"unknown"));worker.receive(msg,channel);
        verify(client).finished(eq(91L),any());verify(channel).basicReject(8,false);assertNull(UserContext.getCurrentUser());
    }
}
