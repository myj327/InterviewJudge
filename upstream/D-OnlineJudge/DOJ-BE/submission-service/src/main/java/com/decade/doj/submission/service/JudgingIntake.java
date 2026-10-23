package com.decade.doj.submission.service;

import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.outbox.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JudgingIntake {
    private final ISubmissionService submissions;
    private final Outbox outbox;

    @Transactional
    public Long accept(JudgingRequest request) {
        Long owner = UserContext.getCurrentUser();
        if (owner == null || owner <= 0) throw new UnauthorizedException("用户未登录");
        if (request.getSubmission() == null || request.getTask() == null)
            throw new BadRequestException("Submission and judging task are required");
        Submission submission = new Submission();
        BeanUtils.copyProperties(request.getSubmission(), submission);
        Long id = submissions.createPending(submission);
        if (submissions.claimQueued(id)) {
            Map<String, Object> task = new HashMap<>(request.getTask());
            task.put("code", submission.getCode());
            task.put("submissionId", id); task.put("uid", owner);
            task.put("problemId", submission.getProblemId()); task.put("lang", "java");
            outbox.add("judge:" + id, "judging.task", task);
        }
        return id;
    }
}
