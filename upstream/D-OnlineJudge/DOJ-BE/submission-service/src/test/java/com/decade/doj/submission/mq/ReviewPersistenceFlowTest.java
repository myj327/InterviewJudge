package com.decade.doj.submission.mq;

import com.decade.doj.common.exception.CommonException;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.controller.ReviewController;
import com.decade.doj.submission.domain.dto.ReviewReport;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.repository.ReviewReportRepository;
import com.decade.doj.submission.service.ISubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewPersistenceFlowTest {

    private final ReviewReportRepository repository = mock(ReviewReportRepository.class);
    private final ReviewCompletedListener listener = new ReviewCompletedListener(repository, new com.decade.doj.common.mq.ReliableDelivery());
    private final ISubmissionService submissionService = mock(ISubmissionService.class);
    private final ReviewController controller = new ReviewController(submissionService, repository,
            mock(com.decade.doj.submission.repository.ReviewJobs.class),mock(com.decade.doj.common.client.ProblemClient.class));

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void repeatedCompletedEventKeepsOneReadableReport() {
        ReviewReport report = report();
        when(repository.save(report)).thenReturn(true, false);
        when(repository.findBySubmissionId(41L)).thenReturn(report);
        when(submissionService.getById(41L)).thenReturn(submission(7L));
        UserContext.setCurrentUser(7L);

        listener.onMessage(report);
        listener.onMessage(report);

        assertEquals(report, controller.getReview(41L).getData());
        verify(repository, times(2)).save(report);
        verify(repository).findBySubmissionId(41L);
    }

    @Test
    void allowsOnlyOwnerAndUsesFrozenNotFoundResponses() {
        ReviewReport report = report();
        when(submissionService.getById(41L)).thenReturn(submission(7L));
        when(repository.findBySubmissionId(41L)).thenReturn(report);
        UserContext.setCurrentUser(7L);
        assertEquals(report, controller.getReview(41L).getData());

        UserContext.setCurrentUser(8L);
        assertThrows(ForbiddenException.class, () -> controller.getReview(41L));
        verify(repository, times(1)).findBySubmissionId(41L);

        when(submissionService.getById(42L)).thenReturn(null);
        CommonException missingSubmission = assertThrows(CommonException.class, () -> controller.getReview(42L));
        assertEquals(404, missingSubmission.getCode());
        verify(repository, never()).findBySubmissionId(42L);

        UserContext.setCurrentUser(7L);
        when(submissionService.getById(43L)).thenReturn(submission(7L));
        when(repository.findBySubmissionId(43L)).thenReturn(null);
        assertEquals("SKIPPED",controller.getReview(43L).getData().getStatus());
    }

    private Submission submission(Long userId) {
        Submission submission = new Submission();
        submission.setUserId(userId);
        return submission;
    }

    private ReviewReport report() {
        return new ReviewReport()
            .setSchemaVersion("1")
            .setPromptVersion("r1-v1")
            .setModel("mock")
            .setRubricVersion("recruitment-v1")
            .setSubmissionId(41L)
            .setStatus("SUCCEEDED")
            .setSummary("summary")
            .setStrengths(List.of("strength"))
            .setRisks(List.of("risk"))
            .setSuggestions(List.of("suggestion"));
    }
}
