package com.decade.doj.submission.service;

import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.po.Submission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
@RequiredArgsConstructor
public class ReviewContextLoader {
    private final ISubmissionService submissions;
    private final ProblemClient problems;
    private final ReviewEvidenceFactory factory;

    public ReviewEvidence load(ReviewEvidence request) {
        if (request == null || request.getSubmissionId() == null) throw new IllegalArgumentException("Missing review submission");
        Submission row = submissions.getById(request.getSubmissionId());
        if (row == null || !"VERDICT_READY".equals(row.getJudgeStage())) throw new IllegalArgumentException("Review is not eligible");
        try {
            UserContext.setCurrentUser(row.getUserId());
            var response = problems.getProblemById(row.getProblemId());
            if (response == null || response.getCode() >= 500 || response.getCode() == 429)
                throw new ResourceAccessException("Public review context temporarily unavailable");
            Problem problem = response.getData();
            if (!response.success() || problem == null) throw new IllegalArgumentException("Public review context unavailable");
            return factory.create(row, problem);
        } finally { UserContext.clear(); }
    }
}
