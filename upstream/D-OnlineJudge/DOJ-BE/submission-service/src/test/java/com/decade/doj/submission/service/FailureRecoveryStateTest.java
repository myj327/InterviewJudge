package com.decade.doj.submission.service;

import com.decade.doj.common.mq.ReliableRabbitConfiguration;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.outbox.Outbox;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FailureRecoveryStateTest {
    @Test void projectionRepairWaitsForDatabaseWithoutConsumingOriginalDlq() throws Exception {
        var failures=mock(ProcessingFailures.class);
        var rabbit=mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
        var channel=mock(com.rabbitmq.client.Channel.class);
        var props=new com.rabbitmq.client.AMQP.BasicProperties.Builder().headers(
                Map.of("x-first-death-queue","judging.result.reliable.queue")).build();
        var message=new com.rabbitmq.client.GetResponse(new com.rabbitmq.client.Envelope(5,false,"doj.dead","processing.failure"),
                props,"{\"submissionId\":91}".getBytes(java.nio.charset.StandardCharsets.UTF_8),0);
        when(channel.basicGet("processing.failure.dlq",false)).thenReturn(message);
        when(rabbit.execute(any(org.springframework.amqp.rabbit.core.ChannelCallback.class))).thenAnswer(inv ->
                ((org.springframework.amqp.rabbit.core.ChannelCallback<?>)inv.getArgument(0)).doInRabbit(channel));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"))
                .doNothing().when(failures).record(eq("judging.result"),anyString());
        var listener=new com.decade.doj.submission.mq.FailureListener(failures,new com.decade.doj.common.mq.ReliableDelivery(),rabbit);
        listener.reconcile();verify(channel).basicNack(5,false,true);verify(channel,never()).basicAck(anyLong(),anyBoolean());
        listener.reconcile();verify(channel).basicAck(5,false);
        verify(channel,times(2)).basicGet("processing.failure.dlq",false);verifyNoMoreInteractions(channel);
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(91L))).thenReturn(1L,0L);
        var state=new ProcessingFailures(jdbc);
        assertThrows(com.decade.doj.common.mq.DeferredDeliveryException.class,()->state.record("review.requested","{\"submissionId\":91}"));
        assertDoesNotThrow(()->state.record("review.requested","{\"submissionId\":91}"));
    }
    @Test void recoveredVerdictCanReplaceInfrastructureFailureButNotARealVerdict() {
        ISubmissionService rows=mock(ISubmissionService.class);Outbox outbox=mock(Outbox.class);
        Submission row=new Submission();row.setId(91L);row.setAttemptId(10L);row.setUserId(7L);row.setProblemId(20L);
        row.setCode("synthetic");row.setLanguage("java");row.setStatus("System Error");row.setJudgeStage("INFRA_FAILED");
        when(rows.getById(91L)).thenReturn(row);when(rows.completeAttempt(any())).thenReturn(true);
        VerdictCompletion complete=new VerdictCompletion(rows,new ReviewEvidenceFactory(),outbox);
        var result=Map.<String,Object>of("submissionId",91L,"judgeStage","VERDICT_READY","executeMessage",Map.of(
                "status","Accepted","exitValue",10,"message","Accepted","time",0.1,"memory",-1L));
        assertNotNull(complete.complete(result));assertEquals("VERDICT_READY",row.getJudgeStage());
        assertNull(complete.complete(result));verify(rows).completeAttempt(any());
        verify(outbox).add(eq("review:91:r1-v1"),eq("review.requested"),any());
    }
    @Test void failureProjectionHasItsOwnQueueAndPreservesOriginalDeadLetters() {
        var declarations=new ReliableRabbitConfiguration().reliableQueues();
        var queues=declarations.getDeclarablesByType(Queue.class);
        assertTrue(queues.stream().anyMatch(q->q.getName().equals("processing.failure.queue")));
        assertTrue(queues.stream().anyMatch(q->q.getName().equals("processing.failure.dlq")));
        for(String route:new String[]{"judging.task","judging.result","review.requested"}) {
            assertTrue(queues.stream().anyMatch(q->q.getName().equals(route+".dlq")));
            assertTrue(declarations.getDeclarablesByType(Binding.class).stream().anyMatch(b->
                    b.getDestination().equals("processing.failure.queue")&&b.getExchange().equals("doj.dead")&&b.getRoutingKey().equals(route)));
        }
    }
}
