package com.decade.doj.submission.service;
import com.decade.doj.common.client.*;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.po.*;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.outbox.Outbox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Map;
class VerdictCompletionTest {
    private ISubmissionService rows=mock(ISubmissionService.class);
    private ProblemClient problems=mock(ProblemClient.class);
    private UserClient users=mock(UserClient.class);
    private Outbox outbox=mock(Outbox.class);
    private Submission submission() {Submission row=new Submission();row.setId(100L);row.setAttemptId(10L);row.setUserId(7L);row.setProblemId(20L);row.setLanguage("java");row.setCode("synthetic");row.setJudgeStage("QUEUED");return row;}
    private Map<String,Object> message(String stage) {return Map.of("submissionId",100L,"judgeStage",stage,"executeMessage",Map.of("status","Wrong Answer","exitValue",11,"message","Wrong Answer","time",0.1,"memory",12));}
    private VerdictCompletion service(TxFixture tx) {
        when(rows.getById(100L)).thenAnswer(call->submission());
        com.decade.doj.common.domain.vo.InfoVO user = new com.decade.doj.common.domain.vo.InfoVO();
        user.setUsername("synthetic");
        when(users.getUser(7L)).thenReturn(R.ok(user));
        when(problems.getProblemById(20L)).thenReturn(R.ok(new Problem().setName("synthetic")));
        return TxFixture.proxy(new VerdictCompletion(rows,new ReviewEvidenceFactory(),outbox),tx);
    }
    @Test void duplicateVerdictOnlyCreatesOneReviewAndStatsEvent() {
        TxFixture tx=new TxFixture();VerdictCompletion service=service(tx);
        when(rows.completeAttempt(any())).thenReturn(true,false);
        assertNotNull(service.complete(message("VERDICT_READY")));assertNull(service.complete(message("VERDICT_READY")));
        verify(outbox).add(eq("review:100:r1-v1"),eq("review.requested"),any());
        verify(outbox).add(eq("stats:100"),eq("submission.created"),any());
        assertEquals(2,tx.commits);assertNull(UserContext.getCurrentUser());
    }
    @Test void eventFailureRollsBackVerdictAndFailedStagesSkipAi() {
        TxFixture tx=new TxFixture();VerdictCompletion service=service(tx);when(rows.completeAttempt(any())).thenReturn(true);
        doThrow(new IllegalStateException("synthetic storage error")).when(outbox).add(anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->service.complete(message("VERDICT_READY")));
        assertEquals(1,tx.rollbacks);assertEquals(0,tx.commits);assertNull(UserContext.getCurrentUser());
        reset(outbox);service.complete(message("COMPILE_FAILED"));verify(outbox,never()).add(anyString(),eq("review.requested"),any());
    }
}
