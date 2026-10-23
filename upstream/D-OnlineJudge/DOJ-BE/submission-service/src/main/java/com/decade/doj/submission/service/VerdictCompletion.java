package com.decade.doj.submission.service;

import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VerdictCompletion {
    private final ISubmissionService submissions;
    private final ReviewEvidenceFactory evidence;
    private final Outbox outbox;

    @Transactional
    public Submission complete(Map<String, Object> message) {
        Long id = ((Number) message.get("submissionId")).longValue();
        Submission submission = submissions.getById(id);
        if (submission == null) throw new IllegalArgumentException("Unknown submission");
        if (!Set.of("QUEUED", "INFRA_FAILED").contains(submission.getJudgeStage())) return null;
            Map<String, Object> result = (Map<String, Object>) message.get("executeMessage");
            boolean attempt = submission.getAttemptId() != null && submission.getAttemptId() > 0;
            String stage = attempt ? (String) message.get("judgeStage") : "VERDICT_READY";
            if (!Set.of("COMPILE_FAILED", "SMOKE_FAILED", "VERDICT_READY").contains(stage))
                throw new IllegalArgumentException("Invalid verdict stage");
            submission.setStatus((String) result.get("status"));
            submission.setExitValue(((Number) result.get("exitValue")).intValue());
            submission.setMessage((String) result.get("message"));
            submission.setTime(((Number) result.get("time")).doubleValue());
            submission.setMemory(((Number) result.get("memory")).longValue());
            submission.setJudgeStage(stage);
            if (!submissions.completeAttempt(submission)) return null;
            if (attempt && "VERDICT_READY".equals(stage))
                outbox.add("review:" + id + ":r1-v1", "review.requested", evidence.create(submission, new Problem()));
            outbox.add("stats:" + id, "submission.created", Map.of("submissionId", id,
                    "problemId", submission.getProblemId(), "isAccepted", "Accepted".equals(submission.getStatus())));
            if ("Accepted".equals(submission.getStatus()))
                outbox.add("solved:" + submission.getUserId() + ":" + submission.getProblemId(), "problem.solved",
                        Map.of("userId", submission.getUserId(), "problemId", submission.getProblemId()));
            return submission;
    }
}
