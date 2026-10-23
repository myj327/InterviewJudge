package com.decade.doj.submission.service;

import com.decade.doj.common.client.*;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.outbox.Outbox;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerdictMetadataFailureTest {
    ISubmissionService rows = mock(ISubmissionService.class);
    ProblemClient problems = mock(ProblemClient.class);
    UserClient users = mock(UserClient.class);
    Outbox outbox = mock(Outbox.class);
    Submission row() {
        Submission row = new Submission(); row.setId(100L); row.setAttemptId(10L); row.setProblemId(20L);
        row.setUserId(7L); row.setLanguage("java"); row.setCode("synthetic"); row.setJudgeStage("QUEUED"); return row;
    }
    Map<String,Object> result(String stage) {
        return Map.of("submissionId",100L,"judgeStage",stage,"executeMessage",Map.of(
                "status","Wrong Answer","exitValue",11,"message","Wrong Answer","time",0.1,"memory",-1L));
    }
    VerdictCompletion service(TxFixture tx) {
        when(rows.getById(100L)).thenReturn(row()); when(rows.completeAttempt(any())).thenReturn(true);
        when(problems.getProblemById(anyLong())).thenThrow(new ResourceAccessException("synthetic problem outage"));
        when(users.getUser(anyLong())).thenThrow(new ResourceAccessException("synthetic user outage"));
        return TxFixture.proxy(new VerdictCompletion(rows,new ReviewEvidenceFactory(),outbox), tx);
    }
    @Test void completedVerdictPersistsWithoutExternalMetadata() {
        TxFixture tx = new TxFixture(); VerdictCompletion service = service(tx);
        assertNotNull(service.complete(result("VERDICT_READY"))); assertEquals(1, tx.commits);
        verifyNoInteractions(problems, users);
        verify(outbox).add(eq("review:100:r1-v1"),eq("review.requested"),any());
        verify(outbox).add(eq("stats:100"),eq("submission.created"),any());
    }
    @Test void failedStageNeedsNoMetadataAndOutboxFailureStillRollsBack() {
        TxFixture tx = new TxFixture(); VerdictCompletion service = service(tx);
        assertNotNull(service.complete(result("COMPILE_FAILED"))); verifyNoInteractions(problems,users);
        verify(outbox,never()).add(anyString(),eq("review.requested"),any());
        when(rows.getById(100L)).thenReturn(row());
        doThrow(new IllegalStateException("synthetic outbox error")).when(outbox).add(anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->service.complete(result("VERDICT_READY")));
        assertEquals(1,tx.rollbacks);
    }

    @Test void reviewContextLoadsPublicMetadataOutsideTransactionAndClearsIdentity() {
        Submission row = row(); row.setJudgeStage("VERDICT_READY");
        when(rows.getById(100L)).thenReturn(row);
        when(problems.getProblemById(20L)).thenAnswer(call -> {
            assertEquals(7L,com.decade.doj.common.utils.UserContext.getCurrentUser());
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return com.decade.doj.common.domain.R.ok(new com.decade.doj.common.domain.po.Problem().setDescription("public statement"));
        });
        var loader = new ReviewContextLoader(rows,problems,new ReviewEvidenceFactory());
        var evidence = loader.load(new com.decade.doj.submission.domain.dto.ReviewEvidence().setSubmissionId(100L));
        assertEquals("public statement", evidence.getProblemDescription()); assertEquals(row.getCode(),evidence.getSourceCode());
        assertNull(com.decade.doj.common.utils.UserContext.getCurrentUser());
        doThrow(new ResourceAccessException("synthetic outage")).when(problems).getProblemById(20L);
        assertThrows(ResourceAccessException.class, () -> loader.load(evidence));
        assertNull(com.decade.doj.common.utils.UserContext.getCurrentUser());
    }
}
