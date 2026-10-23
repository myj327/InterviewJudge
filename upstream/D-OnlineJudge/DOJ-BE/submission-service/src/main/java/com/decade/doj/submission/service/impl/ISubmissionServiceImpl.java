package com.decade.doj.submission.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.decade.doj.common.domain.PageDTO;
import com.decade.doj.common.domain.vo.SubmissionStatsVO;
import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.exception.CommonException;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.common.exception.UnauthorizedException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.domain.dto.SubmissionPageQueryDTO;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.service.ISubmissionService;
import com.decade.doj.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
* @author qzj
* @description 针对表【submission(代码提交记录表)】的数据库操作Service实现
* @createDate 2025-06-05 13:28:52
*/
@Service
@Slf4j
@RequiredArgsConstructor
public class ISubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission>
    implements ISubmissionService {

    @Override
    public Long createPending(Submission submission) {
        if (submission.getAttemptId() == null
                || submission.getIdempotencyKey() == null
                || submission.getIdempotencyKey().isBlank()
                || !"java".equals(submission.getLanguage())) {
            throw new BadRequestException("attemptId, idempotencyKey and java language are required");
        }

        Submission existing = lambdaQuery()
                .eq(Submission::getAttemptId, submission.getAttemptId())
                .eq(Submission::getIdempotencyKey, submission.getIdempotencyKey())
                .one();
        if (existing != null) {
            return matchingReceipt(existing, submission);
        }

        requireUser();

        submission.setId(null);
        submission.setUserId(UserContext.getCurrentUser());
        submission.setUserName(null);
        submission.setProblemName(null);
        submission.setExitValue(null);
        submission.setStatus("PENDING");
        submission.setJudgeStage("PENDING");
        submission.setMessage(null);
        submission.setTime(null);
        submission.setMemory(null);
        submission.setSubmitTime(null);
        try {
            save(submission);
        } catch (DuplicateKeyException conflict) {
            // A locking read sees the winning commit even in a repeatable-read intake transaction.
            Submission winner = lambdaQuery()
                    .eq(Submission::getAttemptId, submission.getAttemptId())
                    .eq(Submission::getIdempotencyKey, submission.getIdempotencyKey())
                    .eq(Submission::getUserId, UserContext.getCurrentUser())
                    .last("FOR UPDATE").one();
            if (winner == null) throw conflict;
            return matchingReceipt(winner, submission);
        }
        return submission.getId();
    }

    @Override
    public Long findReceipt(Submission request) {
        requireUser();
        if (request.getAttemptId() == null || request.getAttemptId() < 0
                || request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())
            throw new BadRequestException("Attempt and idempotency key are required");
        Submission existing = lambdaQuery().eq(Submission::getAttemptId, request.getAttemptId())
                .eq(Submission::getIdempotencyKey, request.getIdempotencyKey()).one();
        return existing == null ? null : matchingReceipt(existing, request);
    }

    private Long matchingReceipt(Submission existing, Submission request) {
        if (!requireUser().equals(existing.getUserId())) throw new ForbiddenException("无权读取他人的提交");
        if (!Arrays.equals(sourceDigest(existing.getCode()), sourceDigest(request.getCode()))
                || !Objects.equals(existing.getLanguage(), request.getLanguage())
                || (request.getProblemId() != null && !Objects.equals(existing.getProblemId(), request.getProblemId())))
            throw new CommonException("同一幂等键不能对应不同的代码、语言或题目", 409);
        return existing.getId();
    }

    private static byte[] sourceDigest(String code) {
        if (code == null) return null;
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private Long requireUser() {
        Long user = UserContext.getCurrentUser();
        if (user == null || user <= 0) throw new UnauthorizedException("用户未登录");
        return user;
    }

    @Override
    public boolean claimQueued(Long submissionId) {
        return lambdaUpdate()
                .eq(Submission::getId, submissionId)
                .eq(Submission::getUserId, UserContext.getCurrentUser())
                .eq(Submission::getStatus, "PENDING")
                .eq(Submission::getJudgeStage, "PENDING")
                .set(Submission::getStatus, "QUEUED")
                .set(Submission::getJudgeStage, "QUEUED")
                .update();
    }

    @Override
    public boolean completeAttempt(Submission submission) {
        return lambdaUpdate()
                .eq(Submission::getId, submission.getId())
                .in(Submission::getStatus, "QUEUED", "System Error")
                .in(Submission::getJudgeStage, "QUEUED", "INFRA_FAILED")
                .set(Submission::getUserName, submission.getUserName())
                .set(Submission::getProblemName, submission.getProblemName())
                .set(Submission::getStatus, submission.getStatus())
                .set(Submission::getExitValue, submission.getExitValue())
                .set(Submission::getMessage, submission.getMessage())
                .set(Submission::getTime, submission.getTime())
                .set(Submission::getMemory, submission.getMemory())
                .set(Submission::getJudgeStage, submission.getJudgeStage())
                .update();
    }

    public PageDTO<Submission> pageQuery(SubmissionPageQueryDTO submissionPageQueryDTO) {
        Long currentUser = UserContext.getCurrentUser();
        if (currentUser == null || currentUser <= 0) {
            throw new UnauthorizedException("用户未登录");
        }
        // 如果 submissionId 存在，则执行精确查询
        if (submissionPageQueryDTO.getSubmissionId() != null) {
            Submission submission = this.getById(submissionPageQueryDTO.getSubmissionId());
            if (submission == null) {
                return PageDTO.empty(0L, 0L);
            }
            if (!currentUser.equals(submission.getUserId())) {
                throw new ForbiddenException("无权读取他人的提交");
            }
            return PageDTO.fullPage(1L, 1L, List.of(submission));
        }

        log.info("分页查询提交列表: {}", submissionPageQueryDTO);
        log.info("userId={}, problemId={}, status={}, language={}",
                submissionPageQueryDTO.getUserId(),
                submissionPageQueryDTO.getProblemId(),
                submissionPageQueryDTO.getStatus(),
                submissionPageQueryDTO.getLanguage());

        String user = submissionPageQueryDTO.getUserId();
        String problem = submissionPageQueryDTO.getProblemId();
        boolean numberedProblem = problem != null && problem.matches("[1-9]\\d*");

        Page<Submission> submissionList = lambdaQuery()
                .eq(Submission::getUserId, currentUser)
                .like(user != null && !user.isBlank(), Submission::getUserName, submissionPageQueryDTO.getUserId())
                .eq(numberedProblem, Submission::getProblemId, problem)
                .like(!numberedProblem && problem != null && !problem.isBlank(), Submission::getProblemName, problem)
                .eq(submissionPageQueryDTO.getStatus() != null, Submission::getStatus, submissionPageQueryDTO.getStatus())
                .eq(submissionPageQueryDTO.getLanguage() != null, Submission::getLanguage, submissionPageQueryDTO.getLanguage())
                .page(submissionPageQueryDTO.toMpPage("submit_time", false));

        return PageDTO.fullPage(submissionList.getTotal(), submissionList.getPages(), submissionList.getRecords());
    }

    @Override
    public SubmissionStatsVO getStats() {
        // 获取总提交数
        long totalSubmissions = this.count();

        // 获取今日提交数
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        long todaySubmissions = this.lambdaQuery()
                .ge(Submission::getSubmitTime, startOfDay)
                .count();

        return new SubmissionStatsVO(totalSubmissions, todaySubmissions);
    }

}
