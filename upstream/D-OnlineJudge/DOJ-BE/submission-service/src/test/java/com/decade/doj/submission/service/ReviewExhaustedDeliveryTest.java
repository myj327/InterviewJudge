package com.decade.doj.submission.service;

import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.domain.dto.*;
import com.decade.doj.submission.outbox.Outbox;
import com.decade.doj.submission.repository.*;
import com.decade.doj.submission.review.ReviewProvider;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.ResourceAccessException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewExhaustedDeliveryTest {
    @Test void lastTransientFailureRejectsCurrentDeliveryAndDuplicatesDoNotCallProvider() throws Exception {
        Fixture f=new Fixture("PENDING",2);
        when(f.provider.review(any())).thenThrow(new ResourceAccessException("Synthetic provider outage"));
        f.deliver();
        assertEquals(3,f.attempts);assertEquals("FAILED",f.state);
        verify(f.channel).basicReject(73L,false);verify(f.channel,never()).basicAck(73L,false);
        verify(f.provider).review(any());verifyNoInteractions(f.outbox);
        f.deliver(); // Already-failed duplicate is acknowledged without executing again.
        verify(f.channel).basicAck(73L,false);verify(f.provider,times(1)).review(any());
        when(f.reports.findBySubmissionId(73001L)).thenReturn(new ReviewReport());
        f.deliver(); // Completed-report duplicate also remains idempotent.
        verify(f.channel,times(2)).basicAck(73L,false);verify(f.channel,times(1)).basicReject(73L,false);
    }

    @Test void expiredThirdLeaseRejectsItsRedeliveryWithoutCallingProvider() throws Exception {
        Fixture f=new Fixture("RUNNING",3);
        f.deliver();
        assertEquals("FAILED",f.state);assertEquals(3,f.attempts);
        verify(f.channel).basicReject(73L,false);verify(f.channel,never()).basicAck(73L,false);
        verifyNoInteractions(f.provider,f.outbox);
    }

    static class Fixture {
        String state;int attempts;
        final ReviewProvider provider=mock(ReviewProvider.class);
        final ReviewReportRepository reports=mock(ReviewReportRepository.class);
        final Outbox outbox=mock(Outbox.class);
        final Channel channel=mock(Channel.class);
        final ReliableDelivery delivery=new ReliableDelivery(){@Override protected void pause(long millis){}};
        final ReviewEvidence evidence=new ReviewEvidence().setSubmissionId(73001L);
        final ReviewCompletion completion;
        Fixture(String state,int attempts) {
            this.state=state;this.attempts=attempts;
            JdbcTemplate jdbc=mock(JdbcTemplate.class,call->{
                String method=call.getMethod().getName(),sql=call.getArgument(0);
                if(method.equals("update")) {
                    if(sql.startsWith("INSERT IGNORE")) return 0;
                    if(sql.contains("attempts=attempts+1")) {
                        if(this.state.equals("PENDING") && this.attempts<3){this.attempts++;this.state="RUNNING";return 1;}
                        return 0;
                    }
                    if(sql.contains("SET state=?,lease_token=NULL")){this.state=call.getArgument(1);return 1;}
                    if(sql.contains("attempts>=3")) {
                        if(this.attempts>=3 && List.of("PENDING","RUNNING").contains(this.state)){this.state="FAILED";return 1;}
                        return 0;
                    }
                }
                if(method.equals("queryForObject")) return this.attempts;
                if(method.equals("queryForList")) return List.of(this.state);
                throw new AssertionError("Unexpected fixture SQL branch");
            });
            ReviewContextLoader context=mock(ReviewContextLoader.class);when(context.load(any())).thenReturn(evidence);
            completion=new ReviewCompletion(new ReviewJobs(jdbc),provider,reports,outbox,
                    mock(PlatformTransactionManager.class),context,delivery);
        }
        void deliver() throws Exception {
            MessageProperties properties=new MessageProperties();properties.setDeliveryTag(73L);
            delivery.handle(new Message(new byte[0],properties),channel,()->completion.complete(evidence));
        }
    }
}
