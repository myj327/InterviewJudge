package com.decade.doj.submission.service;
import com.decade.doj.submission.domain.dto.*;
import com.decade.doj.submission.repository.ReviewReportRepository;
import com.decade.doj.submission.review.*;
import com.decade.doj.submission.outbox.Outbox;
import org.junit.jupiter.api.Test;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.repository.ReviewJobs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ReviewCompletionTest {
    private ReviewJobs jobs=mock(ReviewJobs.class);
    private ReviewContextLoader context=mock(ReviewContextLoader.class);
    private ReviewProvider provider=mock(ReviewProvider.class);
    private ReviewReportRepository reports=mock(ReviewReportRepository.class);
    private Outbox outbox=mock(Outbox.class);
    private ReviewEvidence evidence=new ReviewEvidence().setSchemaVersion("1").setSubmissionId(91L).setLanguage("java").setVerdict("Accepted");
    private ReviewReport report(){return new MockReviewProvider().review(evidence);}
    private ReviewCompletion service(TxFixture tx) {
        when(jobs.claim(91L)).thenReturn(new ReviewJobs.Lease(91L,"synthetic",1));
        when(jobs.succeeded(any())).thenReturn(true);
        when(context.load(any())).thenAnswer(call->call.getArgument(0));
        return new ReviewCompletion(jobs,provider,reports,outbox,tx,context,new ReliableDelivery());
    }
    @Test void duplicateReviewLocksBeforeCallingProviderAndSavesOnce() {
        when(reports.findBySubmissionId(91L)).thenReturn(null,report());when(provider.review(evidence)).thenReturn(report());
        when(reports.save(any())).thenReturn(true);TxFixture tx=new TxFixture();
        ReviewCompletion service=service(tx);
        service.complete(evidence);service.complete(evidence);
        verify(provider).review(evidence);verify(reports).save(any());verify(outbox).add(eq("report:91:r1-v1"),eq("review.completed"),any());
        var order=inOrder(jobs,provider);order.verify(jobs).claim(91L);order.verify(provider).review(evidence);
    }
    @Test void reportEventFailureRollsBackAndInvalidReportHasNoSideEffects() {
        when(provider.review(evidence)).thenReturn(report());when(reports.save(any())).thenReturn(true);
        doThrow(new IllegalStateException("event storage failed")).when(outbox).add(anyString(),anyString(),any());
        TxFixture tx=new TxFixture();ReviewCompletion service=service(tx);
        assertThrows(IllegalStateException.class,()->service.complete(evidence));assertEquals(1,tx.rollbacks);assertEquals(0,tx.commits);
        reset(outbox,reports);when(provider.review(evidence)).thenReturn(report().setSubmissionId(999L));
        assertThrows(IllegalArgumentException.class,()->service.complete(evidence));verify(reports,never()).save(any());verifyNoInteractions(outbox);
    }
}
