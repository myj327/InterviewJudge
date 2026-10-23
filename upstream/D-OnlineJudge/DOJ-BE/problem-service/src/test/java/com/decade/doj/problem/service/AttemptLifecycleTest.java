package com.decade.doj.problem.service;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.*;
import com.decade.doj.problem.mapper.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AttemptLifecycleTest {
    final AssessmentMapper assessments=mock(AssessmentMapper.class);
    final AttemptMapper attempts=mock(AttemptMapper.class);
    final AttemptService service=new AttemptService(assessments,attempts);
    final Attempt row=new Attempt().setId(100L).setAssessmentId(10L).setCandidateId(7L).setStatus("STARTED");
    final Assessment assessment=new Assessment().setId(10L).setProblemId(20L).setStatus("PUBLISHED")
            .setStartsAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(1)).setEndsAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusHours(1));
    @BeforeEach void setup(){UserContext.setCurrentUser(7L);when(assessments.selectById(10L)).thenReturn(assessment);}
    @AfterEach void clear(){UserContext.clear();}
    @Test void repeatedAndConcurrentStartReturnSameAttempt(){
        when(attempts.selectOne(any())).thenReturn(row);
        assertEquals(100L,service.start(10L));verify(attempts,never()).insert(any());
        when(attempts.selectOne(any())).thenReturn(null,row);
        doThrow(new org.springframework.dao.DuplicateKeyException("synthetic race")).when(attempts).insert(any());
        assertEquals(100L,service.start(10L));
    }
    @Test void finishIsConditionalAndOnlyCandidateCanFinish() throws Exception {
        when(attempts.selectById(100L)).thenReturn(row);
        when(attempts.update(isNull(),any())).thenAnswer(inv->{row.setStatus("SUBMITTED");return 1;});
        service.getClass().getMethod("finish",Long.class).invoke(service,100L);
        service.getClass().getMethod("finish",Long.class).invoke(service,100L);
        verify(attempts).update(isNull(),any());assertEquals("SUBMITTED",row.getStatus());
        assertThrows(RuntimeException.class,()->service.resolveProblemId(100L));
        UserContext.setCurrentUser(8L);
        var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->service.getClass().getMethod("finish",Long.class).invoke(service,100L));
        assertInstanceOf(com.decade.doj.common.exception.ForbiddenException.class,error.getCause());
    }
    @Test void viewExpiresAttemptAndRejectsMissingIdentity() throws Exception {
        assessment.setEndsAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));when(attempts.selectById(100L)).thenReturn(row);
        when(attempts.update(isNull(),any())).thenReturn(1);
        var view=(Map<?,?>)service.getClass().getMethod("view",Long.class).invoke(service,100L);
        assertEquals("EXPIRED",view.get("status"));assertEquals(20L,view.get("problemId"));
        UserContext.clear();assertThrows(RuntimeException.class,()->service.start(10L));
    }
}
