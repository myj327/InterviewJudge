package com.decade.doj.submission.service;

import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.po.Submission;
import org.springframework.stereotype.Component;

@Component
public class ReviewEvidenceFactory {

    public ReviewEvidence create(Submission submission, Problem problem) {
        return new ReviewEvidence()
            .setSchemaVersion("1")
            .setSubmissionId(submission.getId())
            .setLanguage(submission.getLanguage())
            .setSourceCode(ReviewContentSanitizer.scrub(submission.getCode()))
            .setVerdict(submission.getStatus())
            .setJudgeStage(submission.getJudgeStage())
            .setExitValue(submission.getExitValue())
            .setTimeSeconds(submission.getTime())
            .setMemoryKb(submission.getMemory())
            .setProblemDescription(ReviewContentSanitizer.scrub(problem.getDescription()))
            .setInputStyle(ReviewContentSanitizer.scrub(problem.getInputStyle()))
            .setOutputStyle(ReviewContentSanitizer.scrub(problem.getOutputStyle()))
            .setHint(ReviewContentSanitizer.scrub(problem.getHint()));
    }
}
