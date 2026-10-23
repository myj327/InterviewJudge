package com.decade.doj.submission.repository;

import com.decade.doj.common.mq.*;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewJobLeaseTest {
    @Test void busyDeliveryRequeuesAndStaleLeaseCannotCompleteAnotherOwnersJob() throws Exception {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);ReviewJobs jobs=new ReviewJobs(jdbc);
        when(jdbc.queryForList(anyString(),eq(String.class),eq(91L))).thenReturn(List.of("RUNNING"));
        assertThrows(DeferredDeliveryException.class,()->jobs.claim(91L));
        Channel channel=mock(Channel.class);MessageProperties props=new MessageProperties();props.setDeliveryTag(5);
        ReliableDelivery delivery=new ReliableDelivery(){protected void pause(long millis){ }};
        delivery.handle(new Message(new byte[0],props),channel,()->jobs.claim(91L));
        verify(channel).basicNack(5,false,true);verify(channel,never()).basicReject(anyLong(),anyBoolean());
        when(jdbc.update(contains("state='RUNNING',lease_token"),any(),eq(91L))).thenReturn(1);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(91L),any())).thenReturn(2);
        ReviewJobs.Lease lease=jobs.claim(91L);assertEquals(2,lease.attempt());assertNotNull(lease.token());
        assertFalse(jobs.succeeded(lease));
        verify(jdbc).update(contains("AND lease_token=?"),eq(91L),eq(lease.token()));
        jobs.failed(lease,true);
        verify(jdbc).update(contains("SET state=?"),eq("PENDING"),eq(2),eq(91L),eq(lease.token()));
    }
}
