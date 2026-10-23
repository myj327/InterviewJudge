package com.decade.doj.submission.review;

import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.dto.ReviewReport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "doj.review.provider", havingValue = "mock", matchIfMissing = true)
public class MockReviewProvider implements ReviewProvider {

    @Override
    public ReviewReport review(ReviewEvidence evidence) {
        return new ReviewReport()
            .setSchemaVersion("1")
            .setPromptVersion("r1-v1")
            .setModel("mock")
            .setRubricVersion("recruitment-v1")
            .setSubmissionId(evidence.getSubmissionId())
            .setStatus("SUCCEEDED")
            .setSummary("Mock review for " + evidence.getLanguage() + " submission " + evidence.getSubmissionId())
            .setStrengths(List.of("The submission reached " + evidence.getJudgeStage()))
            .setRisks(List.of("The deterministic verdict is " + evidence.getVerdict()))
            .setSuggestions(List.of("Review the reported verdict before the next submission."));
    }
}
