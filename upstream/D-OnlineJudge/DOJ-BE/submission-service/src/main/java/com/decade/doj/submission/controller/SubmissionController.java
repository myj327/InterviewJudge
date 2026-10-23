package com.decade.doj.submission.controller;

import com.decade.doj.common.domain.PageDTO;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.vo.ExecuteMessage;
import com.decade.doj.common.domain.vo.SubmissionStatsVO;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.dto.SubmissionPageQueryDTO;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.service.ISubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.submission.service.JudgingIntake;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/submission")
@Tag(name = "提交相关接口")
@Slf4j
@RequiredArgsConstructor
public class SubmissionController {

    private final ISubmissionService submissionService;
    private final JudgingIntake intake;
    @Value("${DOJ_INTERNAL_TOKEN:}")
    private String internalToken;

    @PostMapping("/internal/intake")
    public R<Long> intake(@RequestBody JudgingRequest request,
            @RequestHeader(value="X-DOJ-Internal-Token", required=false) String token) {
        requireService(token);
        return R.ok(intake.accept(request));
    }

    @PostMapping("/internal/receipt")
    public R<Long> receipt(@RequestBody Submission request,
            @RequestHeader(value="X-DOJ-Internal-Token", required=false) String token) {
        requireService(token);
        return R.ok(submissionService.findReceipt(request));
    }

    @GetMapping("/internal/finished/{id}")
    public R<Boolean> finished(@PathVariable Long id,
            @RequestHeader(value="X-DOJ-Internal-Token",required=false) String token) {
        requireService(token);
        Submission row=submissionService.getById(id);
        return row==null ? R.error(404,"submission not found") : R.ok(deterministic(row));
    }

    @PostMapping("/internal/artifact-state")
    public R<String> artifactState(@RequestBody Submission request,
            @RequestHeader(value="X-DOJ-Internal-Token",required=false) String token) {
        requireService(token);
        Long id=submissionService.findReceipt(request);
        if(id==null) return R.ok("ABSENT");
        Submission row=submissionService.getById(id);
        return R.ok(row==null ? "ABSENT" : deterministic(row) ? "FINISHED" : "ACTIVE");
    }

    private boolean deterministic(Submission row) {
        return List.of("COMPILE_FAILED","SMOKE_FAILED","VERDICT_READY").contains(row.getJudgeStage()==null?"":row.getJudgeStage());
    }

    private void requireService(String token) {
        if (internalToken == null || internalToken.isBlank() || token == null || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new ForbiddenException("Invalid service identity");
    }

    @GetMapping("/page")
    @Operation(summary = "分页获取提交列表")
    public R<PageDTO<Submission>> page(SubmissionPageQueryDTO problemPageQueryDTO) {
        PageDTO<Submission> res = submissionService.pageQuery(problemPageQueryDTO);
        return R.ok(res);
    }

    @GetMapping("/stats")
    @Operation(summary = "获取提交统计")
    public R<SubmissionStatsVO> getStats() {
        return R.ok(submissionService.getStats());
    }

    @GetMapping("/match/{id}")
    @Operation(summary = "获取当前用户指定问题的提交详情")
    public R<Integer> getById(@PathVariable String id) {
        List<Submission> submissions = submissionService.lambdaQuery()
                .eq(Submission::getProblemId, id)
                .list();
        int f = 0;
        for (Submission submission : submissions) {
            if (submission.getUserId() != null && submission.getUserId().equals(UserContext.getCurrentUser())) {
                f = 1;
                if (ExecuteMessage.getStatus(submission.getExitValue()).equals("Accepted")) {
                    return R.ok(1); // 已经提交过且通过
                }
            }
        }
        if (f == 1) {
            return R.ok(2); // 已经提交过但未通过
        }
        return R.ok(0);
    }
}
