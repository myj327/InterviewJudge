package com.decade.doj.submission.outbox;
import com.decade.doj.common.mq.*;
import com.decade.doj.submission.service.TxFixture;
import com.decade.doj.submission.service.ProcessingFailures;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.jdbc.core.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
class OutboxTest {
    @Test void failedPublicationRemainsRetryableAndThirdFailureIsDead() {
        Outbox outbox=mock(Outbox.class);ConfirmedPublisher publisher=mock(ConfirmedPublisher.class);
        Outbox.Event a=new Outbox.Event(1,"judging.task","{}",1),b=new Outbox.Event(1,"judging.task","{}",3);
        when(outbox.claim()).thenReturn(a,b,null);doThrow(new AmqpException("unavailable")).when(publisher).publish(anyString(),any());
        new OutboxDispatcher(outbox,publisher,new ReliableDelivery()).dispatch();
        verify(outbox).failed(a,true);verify(outbox).failed(b,true);verify(outbox,never()).sent(any());
        JdbcTemplate jdbc=mock(JdbcTemplate.class);Outbox store=new Outbox(jdbc,new TxFixture(),mock(ProcessingFailures.class));
        store.failed(b,true);verify(jdbc).update(contains("SET state=?"),eq("DEAD"),eq(1L),eq(3));
    }
    @Test void expiredLeaseIsReclaimedWithNextAttemptAndMalformedEventIsDead() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);TxFixture tx=new TxFixture();
        Outbox.Event recovered=new Outbox.Event(1,"judging.task","{}",2);
        when(jdbc.query(contains("SKIP LOCKED"),any(RowMapper.class))).thenReturn(List.of(recovered));
        Outbox store=new Outbox(jdbc,tx,mock(ProcessingFailures.class));assertEquals(recovered,store.claim());assertEquals(1,tx.commits);
        verify(jdbc).update(contains("state='IN_FLIGHT'"),eq(2),eq(1L));
        store.failed(recovered,false);verify(jdbc).update(contains("SET state=?"),eq("DEAD"),eq(1L),eq(2));
        store.sent(recovered);verify(jdbc).update(contains("payload='{}'"),eq(1L),eq(2));
    }
}
