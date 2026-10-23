package com.decade.doj.problem.mq;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.problem.service.IProblemService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class StatsInboxTest {
    @Test void repeatedSubmissionCountsOnlyOnceAndFailureRollsBackInbox() {
        IProblemService problems=mock(IProblemService.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(jdbc.update(anyString(),eq(100L))).thenReturn(1,0,1);
        StatsUpdateListener listener=new StatsUpdateListener(problems,jdbc,tx,new ReliableDelivery());
        Map<String,Object> message=Map.of("submissionId",100L,"problemId",20L,"isAccepted",true);
        listener.onMessage(message);listener.onMessage(message);verify(problems).updateProblemStats(20L,true);
        doThrow(new IllegalStateException("database failure")).when(problems).updateProblemStats(20L,true);
        assertThrows(IllegalStateException.class,()->listener.onMessage(message));verify(tx).rollback(any());
    }
}
