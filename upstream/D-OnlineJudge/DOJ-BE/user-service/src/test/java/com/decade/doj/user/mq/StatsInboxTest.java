package com.decade.doj.user.mq;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.user.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class StatsInboxTest {
    @Test void repeatedSolvedEventRepairsRankingWithoutAwardingTwice() {
        IUserService users=mock(IUserService.class);JdbcTemplate jdbc=mock(JdbcTemplate.class);
        PlatformTransactionManager tx=mock(PlatformTransactionManager.class);when(tx.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        when(jdbc.update(anyString(),eq(7L),eq(20L))).thenReturn(1,0);
        StatsUpdateListener listener=new StatsUpdateListener(users,jdbc,tx,new ReliableDelivery());
        listener.onMessage(Map.of("userId",7L,"problemId",20L));listener.onMessage(Map.of("userId",7L,"problemId",20L));
        verify(users).handleProblemSolved(7L,20L);verify(users,times(2)).refreshRanking(7L);
    }
}
