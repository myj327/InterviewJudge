package com.decade.doj.submission.service;
import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.common.domain.po.Submission;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.outbox.Outbox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class JudgingIntakeTest {
    @Test void queueClaimAndEventAreOneTransactionAndRepeatedRequestKeepsId() {
        ISubmissionService rows=mock(ISubmissionService.class);Outbox outbox=mock(Outbox.class);
        when(rows.createPending(any())).thenReturn(100L);when(rows.claimQueued(100L)).thenReturn(true,false);
        TxFixture tx=new TxFixture();JudgingIntake intake=TxFixture.proxy(new JudgingIntake(rows,outbox),tx);
        UserContext.setCurrentUser(7L);
        try {
            assertEquals(100L,intake.accept(request()));assertEquals(100L,intake.accept(request()));
            verify(outbox).add(eq("judge:100"),eq("judging.task"),argThat(body->((Map<?,?>)body).get("uid").equals(7L)
                    && "synthetic source".equals(((Map<?,?>)body).get("code"))));
            assertEquals(2,tx.commits);assertEquals(0,tx.rollbacks);
        } finally {UserContext.clear();}
    }
    @Test void outboxWriteFailureRollsBackInsteadOfReturningAcceptedId() {
        ISubmissionService rows=mock(ISubmissionService.class);Outbox outbox=mock(Outbox.class);
        when(rows.createPending(any())).thenReturn(100L);when(rows.claimQueued(100L)).thenReturn(true);
        doThrow(new IllegalStateException("synthetic storage failure")).when(outbox).add(anyString(),anyString(),any());
        TxFixture tx=new TxFixture();JudgingIntake intake=TxFixture.proxy(new JudgingIntake(rows,outbox),tx);
        UserContext.setCurrentUser(7L);
        try {assertThrows(IllegalStateException.class,()->intake.accept(request()));assertEquals(1,tx.rollbacks);assertEquals(0,tx.commits);}
        finally {UserContext.clear();}
    }
    private JudgingRequest request(){return new JudgingRequest().setSubmission(new Submission().setCode("synthetic source").setAttemptId(10L).setProblemId(20L).setLanguage("java").setIdempotencyKey("key")).setTask(Map.of("localPath","synthetic","uid",999L,"code","untrusted task copy"));}
}
