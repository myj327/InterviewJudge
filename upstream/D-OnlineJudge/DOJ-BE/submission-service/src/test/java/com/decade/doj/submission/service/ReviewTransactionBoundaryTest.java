package com.decade.doj.submission.service;

import com.decade.doj.submission.domain.dto.*;
import com.decade.doj.submission.outbox.Outbox;
import com.decade.doj.submission.repository.*;
import com.decade.doj.submission.review.*;
import org.junit.jupiter.api.Test;
import com.decade.doj.common.mq.ReliableDelivery;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReviewTransactionBoundaryTest {
    ReviewJobs jobs=mock(ReviewJobs.class);
    ReviewContextLoader context=mock(ReviewContextLoader.class);
    ReviewProvider provider=mock(ReviewProvider.class);
    ReviewReportRepository reports=mock(ReviewReportRepository.class);
    Outbox outbox=mock(Outbox.class);
    ReviewEvidence evidence=new ReviewEvidence().setSchemaVersion("1").setSubmissionId(91L).setLanguage("java").setVerdict("Accepted");
    ReviewReport report(){ return new MockReviewProvider().review(evidence); }
    ReviewCompletion service(TxFixture tx) {
        when(jobs.claim(91L)).thenReturn(new ReviewJobs.Lease(91L,"synthetic-lease",1));
        when(jobs.succeeded(any())).thenReturn(true);
        when(context.load(any())).thenAnswer(call->call.getArgument(0));
        when(provider.review(any())).thenAnswer(call->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Provider must be outside transaction");return report();});
        when(reports.save(any())).thenAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());return true;});
        return new ReviewCompletion(jobs,provider,reports,outbox,tx,context,new ReliableDelivery());
    }
    @Test void providerRunsOutsideTransactionAndDuplicateHasOnlyOneCall() {
        TxFixture tx=new TxFixture();ReviewCompletion service=service(tx);
        when(reports.findBySubmissionId(91L)).thenReturn(null,report());
        service.complete(evidence);service.complete(evidence);
        verify(provider).review(any());verify(reports).save(any());verify(outbox).add(eq("report:91:r1-v1"),eq("review.completed"),any());
        assertEquals(1,tx.commits);
    }
    @Test void invalidReportCreatesNoDatabaseTransactionOrEvent() {
        TxFixture tx=new TxFixture();ReviewCompletion service=service(tx);
        doReturn(report().setSubmissionId(999L)).when(provider).review(any());
        assertThrows(IllegalArgumentException.class,()->service.complete(evidence));
        assertEquals(0,tx.commits);assertEquals(0,tx.rollbacks);verify(reports,never()).save(any());verifyNoInteractions(outbox);
        verify(jobs).failed(any(),eq(false));
    }
    @Test void eventFailureRollsBackOnlyFinalStorage() {
        TxFixture tx=new TxFixture();ReviewCompletion service=service(tx);
        doThrow(new IllegalStateException("synthetic event failure")).when(outbox).add(anyString(),anyString(),any());
        assertThrows(IllegalStateException.class,()->service.complete(evidence));
        assertEquals(1,tx.rollbacks);assertEquals(0,tx.commits);
        reset(outbox,reports);when(jobs.succeeded(any())).thenReturn(false);
        service.complete(evidence);verify(reports,never()).save(any());verifyNoInteractions(outbox);
    }
}
