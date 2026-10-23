package com.decade.doj.submission.outbox;

import com.decade.doj.submission.service.TxFixture;
import com.decade.doj.submission.service.ProcessingFailures;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxBusinessFailureTest {
    @Test void thirdPublicationFailureAlsoUpdatesSubmissionInOneTransaction() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);TxFixture tx=new TxFixture();
        when(jdbc.update(contains("SET state=?"),any(),any(),any())).thenReturn(1);
        Outbox store=new Outbox(jdbc,tx,new ProcessingFailures(jdbc));
        store.failed(new Outbox.Event(1,"judging.task","{\"submissionId\":91}",3),true);
        verify(jdbc).update(contains("UPDATE submission"),eq(91L));
        assertEquals(1,tx.commits);
    }
    @Test void retryAndStalePublisherCannotChangeBusinessState() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);TxFixture tx=new TxFixture();Outbox store=new Outbox(jdbc,tx,new ProcessingFailures(jdbc));
        when(jdbc.update(contains("SET state=?"),any(),any(),any())).thenReturn(1);
        store.failed(new Outbox.Event(1,"judging.task","{\"submissionId\":91}",1),true);
        verify(jdbc,never()).update(contains("UPDATE submission"),anyLong());
        reset(jdbc);
        store.failed(new Outbox.Event(1,"judging.task","{\"submissionId\":91}",3),true);
        verify(jdbc,never()).update(contains("UPDATE submission"),anyLong());
    }
    @Test void failedProjectionRollsBackDeadAndExpiredClaimUsesSameProjection() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);TxFixture tx=new TxFixture();Outbox store=new Outbox(jdbc,tx,new ProcessingFailures(jdbc));
        when(jdbc.update(contains("SET state=?"),any(),any(),any())).thenReturn(1);
        doThrow(new DataAccessResourceFailureException("synthetic fault projection outage"))
                .when(jdbc).update(contains("UPDATE submission"),eq(91L));
        assertThrows(DataAccessResourceFailureException.class,()->store.failed(new Outbox.Event(1,"judging.task","{\"submissionId\":91}",3),true));
        assertEquals(1,tx.rollbacks);assertEquals(0,tx.commits);
        reset(jdbc);
        when(jdbc.query(contains("SKIP LOCKED"),any(RowMapper.class)))
                .thenReturn(List.of(new Outbox.Event(1,"judging.task","{\"submissionId\":91}",4)));
        assertNull(store.claim());verify(jdbc).update(contains("UPDATE submission"),eq(91L));
    }
}
