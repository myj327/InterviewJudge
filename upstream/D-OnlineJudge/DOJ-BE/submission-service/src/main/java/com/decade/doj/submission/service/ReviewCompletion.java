package com.decade.doj.submission.service;

import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.dto.ReviewReport;
import com.decade.doj.submission.repository.ReviewReportRepository;
import com.decade.doj.submission.repository.ReviewJobs;
import com.decade.doj.common.mq.ReliableDelivery;
import com.decade.doj.submission.review.ReviewProvider;
import com.decade.doj.submission.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ReviewCompletion {
    private final ReviewJobs jobs;
    private final ReviewProvider provider;
    private final ReviewReportRepository reports;
    private final Outbox outbox;
    private final PlatformTransactionManager transactions;
    private final ReviewContextLoader context;
    private final ReliableDelivery delivery;

    public void complete(ReviewEvidence request) {
        Long id = request == null ? null : request.getSubmissionId();
        if (id == null) throw new IllegalArgumentException("Missing review submission");
        if (reports.findBySubmissionId(id) != null) return;
        ReviewJobs.Lease lease=jobs.claim(id);
        if (lease == null) return;
        try {
            ReviewEvidence evidence=context.load(request);
            ReviewReport report=provider.review(evidence);
            if (!isValid(report,evidence)) throw new IllegalArgumentException("Invalid review report");
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                if (!jobs.succeeded(lease)) return;
                if (reports.save(report)) outbox.add("report:" + id + ":r1-v1", "review.completed", report);
            });
        } catch (RuntimeException failure) {
            boolean transientFailure=delivery.isTransient(failure);
            jobs.failed(lease,transientFailure);
            // End this delivery before the local retry can acknowledge the newly FAILED job.
            // No transient cause: ReliableDelivery recursively classifies exception causes.
            if (transientFailure && lease.attempt()>=3) throw new IllegalStateException("Review attempts exhausted");
            throw failure;
        }
    }

    private boolean isValid(ReviewReport report, ReviewEvidence evidence) {
        return report != null && "1".equals(report.getSchemaVersion()) && "r1-v1".equals(report.getPromptVersion())
                && "recruitment-v1".equals(report.getRubricVersion()) && "SUCCEEDED".equals(report.getStatus())
                && evidence.getSubmissionId().equals(report.getSubmissionId()) && hasText(report.getModel())
                && hasText(report.getSummary()) && hasItems(report.getStrengths())
                && hasItems(report.getRisks()) && hasItems(report.getSuggestions());
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private boolean hasItems(java.util.List<String> values) {
        return values != null && !values.isEmpty() && values.stream().allMatch(this::hasText);
    }
}
