package com.decade.doj.problem.service;

import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.Assessment;
import com.decade.doj.problem.domain.po.Attempt;
import com.decade.doj.problem.mapper.AssessmentMapper;
import com.decade.doj.problem.mapper.AttemptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.common.exception.UnauthorizedException;
import org.springframework.dao.DuplicateKeyException;

@Service
@RequiredArgsConstructor
public class AttemptService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String STARTED = "STARTED";

    private final AssessmentMapper assessmentMapper;
    private final AttemptMapper attemptMapper;

    public Long start(Long assessmentId) {
        Long user = requireUser();
        Attempt existing = existing(assessmentId,user);
        if (existing != null) return existing.getId();
        Assessment assessment = assessmentMapper.selectById(assessmentId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (assessment == null || !PUBLISHED.equals(assessment.getStatus())
                || assessment.getStartsAt().isAfter(now)
                || !now.isBefore(assessment.getEndsAt())) {
            throw new BadRequestException("assessment is not open");
        }

        Attempt attempt = new Attempt()
                .setAssessmentId(assessmentId)
                .setCandidateId(UserContext.getCurrentUser())
                .setStartedAt(now)
                .setStatus(STARTED);
        try { attemptMapper.insert(attempt); }
        catch (DuplicateKeyException conflict) {
            Attempt winner=existing(assessmentId,user);
            if(winner==null) throw conflict;
            return winner.getId();
        }
        return attempt.getId();
    }

    private Attempt existing(Long assessmentId, Long user) {
        return attemptMapper.selectOne(new QueryWrapper<Attempt>().eq("assessment_id",assessmentId).eq("candidate_id",user));
    }

    private Long requireUser() {
        Long user=UserContext.getCurrentUser();
        if(user==null || user<=0) throw new UnauthorizedException("用户未登录");
        return user;
    }

    private Attempt owned(Long id) {
        Long user=requireUser();
        Attempt attempt=attemptMapper.selectById(id);
        if(attempt==null || !user.equals(attempt.getCandidateId())) throw new ForbiddenException("无权读取参加记录");
        return attempt;
    }

    private Assessment assessment(Attempt attempt) {
        Assessment assessment=assessmentMapper.selectById(attempt.getAssessmentId());
        if(assessment==null) throw new BadRequestException("考核不存在");
        return assessment;
    }

    private void close(Attempt attempt,String status) {
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        int changed=attemptMapper.update(null,new UpdateWrapper<Attempt>().eq("id",attempt.getId())
                .eq("candidate_id",requireUser()).eq("status",STARTED).set("status",status).set("submitted_at",now));
        if(changed==1) attempt.setStatus(status).setSubmittedAt(now);
        else { Attempt current=attemptMapper.selectById(attempt.getId());attempt.setStatus(current.getStatus()); }
    }

    public Map<String,Object> view(Long id) {
        Attempt attempt=owned(id);Assessment assessment=assessment(attempt);
        if(STARTED.equals(attempt.getStatus()) && !LocalDateTime.now(ZoneOffset.UTC).isBefore(assessment.getEndsAt())) close(attempt,"EXPIRED");
        return Map.of("attemptId",id,"problemId",assessment.getProblemId(),"status",attempt.getStatus());
    }

    public void finish(Long id) {
        Attempt attempt=owned(id);
        if(!STARTED.equals(attempt.getStatus())) return;
        close(attempt,LocalDateTime.now(ZoneOffset.UTC).isBefore(assessment(attempt).getEndsAt()) ? "SUBMITTED" : "EXPIRED");
    }

    public boolean canRead(Long id) {
        Long user=requireUser();Attempt attempt=attemptMapper.selectById(id);
        if(attempt==null) return false;
        if(user.equals(attempt.getCandidateId())) return true;
        Assessment assessment=assessmentMapper.selectById(attempt.getAssessmentId());
        return assessment!=null && user.equals(assessment.getOwnerId());
    }

    public Long resolveProblemId(Long attemptId) {
        Long currentUser = UserContext.getCurrentUser();
        if (currentUser == null) {
            throw new BadRequestException("attempt is not eligible for submission");
        }
        Attempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || !currentUser.equals(attempt.getCandidateId())
                || !STARTED.equals(attempt.getStatus())) {
            throw new BadRequestException("attempt is not eligible for submission");
        }
        Assessment assessment = assessmentMapper.selectById(attempt.getAssessmentId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (assessment == null || !PUBLISHED.equals(assessment.getStatus())
                || assessment.getStartsAt().isAfter(now)
                || !now.isBefore(assessment.getEndsAt())) {
            throw new BadRequestException("assessment is not open");
        }
        return assessment.getProblemId();
    }
}
