package com.decade.doj.submission.controller;

import com.decade.doj.common.domain.R;
import com.decade.doj.common.exception.CommonException;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.dto.ReviewReport;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.repository.ReviewReportRepository;
import com.decade.doj.submission.service.ISubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.submission.repository.ReviewJobs;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("/submission")
@RequiredArgsConstructor
public class ReviewController {

    private final ISubmissionService submissionService;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewJobs jobs;
    private final ProblemClient problems;

    @GetMapping("/{submissionId}/review")
    public R<ReviewReport> getReview(@PathVariable Long submissionId) {
        Long user=requireUser();
        Submission submission = submissionService.getById(submissionId);
        if (submission == null) {
            throw new CommonException("submission not found", 404);
        }
        if (!Objects.equals(user, submission.getUserId())) requireAttemptRead(submission.getAttemptId());
        ReviewReport report = reviewReportRepository.findBySubmissionId(submissionId);
        if (report == null) {
            String stage=submission.getJudgeStage();
            String state="PENDING";
            if(submission.getAttemptId()==null || submission.getAttemptId()<=0
                    || List.of("COMPILE_FAILED","SMOKE_FAILED","INFRA_FAILED").contains(stage==null?"":stage)) state="SKIPPED";
            else if("VERDICT_READY".equals(stage)) {
                String job=jobs.status(submissionId);
                if("RUNNING".equals(job) || "FAILED".equals(job)) state=job;
            }
            report=new ReviewReport().setSubmissionId(submissionId).setStatus(state)
                    .setSummary(switch(state) {
                        case "RUNNING" -> "复盘生成中"; case "FAILED" -> "复盘生成失败，等待恢复";
                        case "SKIPPED" -> "本次提交未进入复盘阶段"; default -> "等待判题完成或复盘调度";
                    }).setStrengths(List.of()).setRisks(List.of()).setSuggestions(List.of());
        }
        return R.ok(report);
    }

    private Long requireUser() {
        Long user=UserContext.getCurrentUser();
        if(user==null || user<=0) throw new UnauthorizedException("用户未登录");
        return user;
    }

    private void requireAttemptRead(Long id) {
        requireUser();
        if(id==null || id<=0) throw new ForbiddenException("review access denied");
        R<Boolean> result=problems.canReadAttempt(id);
        if(result==null || !result.success()) throw new CommonException("考核权限暂不可确认",503);
        if(!Boolean.TRUE.equals(result.getData())) throw new ForbiddenException("review access denied");
    }

    @GetMapping("/attempts/{attemptId}/reviews")
    public R<List<Map<String,Object>>> attemptReviews(@PathVariable Long attemptId) {
        requireAttemptRead(attemptId);
        List<Map<String,Object>> result=submissionService.list(new QueryWrapper<Submission>().eq("attempt_id",attemptId)
                .orderByDesc("id").last("LIMIT 100")).stream().map(row->{
            Map<String,Object> item=new LinkedHashMap<>();item.put("submissionId",row.getId());
            item.put("status",row.getStatus());item.put("judgeStage",row.getJudgeStage());item.put("submitTime",row.getSubmitTime());
            return item;
        }).toList();
        return R.ok(result);
    }
}
