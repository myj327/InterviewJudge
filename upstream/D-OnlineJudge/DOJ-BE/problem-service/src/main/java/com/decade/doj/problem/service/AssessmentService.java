package com.decade.doj.problem.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.Assessment;
import com.decade.doj.problem.mapper.AssessmentMapper;
import com.decade.doj.problem.mapper.ProblemMapper;
import com.decade.doj.problem.mapper.AttemptMapper;
import com.decade.doj.problem.domain.po.Attempt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.decade.doj.common.exception.ForbiddenException;
import com.decade.doj.common.exception.UnauthorizedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";

    private final AssessmentMapper assessmentMapper;
    private final ProblemMapper problemMapper;
    private final AttemptMapper attemptMapper;

    public Long create(Assessment assessment) {
        requireUser();
        if(assessment.getTitle()==null || assessment.getTitle().isBlank() || assessment.getTitle().trim().length()>128)
            throw new BadRequestException("标题不能为空且最多 128 字符");
        assessment.setTitle(assessment.getTitle().trim());
        if (assessment.getStartsAt() == null || assessment.getEndsAt() == null
                || !assessment.getStartsAt().isBefore(assessment.getEndsAt())) {
            throw new BadRequestException("startsAt must be before endsAt");
        }
        if (assessment.getProblemId() == null
                || problemMapper.selectById(assessment.getProblemId()) == null) {
            throw new BadRequestException("problem does not exist");
        }

        assessment.setId(null);
        assessment.setOwnerId(UserContext.getCurrentUser());
        assessment.setStatus(DRAFT);
        assessmentMapper.insert(assessment);
        return assessment.getId();
    }

    public void publish(Long id) {
        requireUser();
        UpdateWrapper<Assessment> update = new UpdateWrapper<>();
        update.eq("id", id)
                .eq("owner_id", UserContext.getCurrentUser())
                .eq("status", DRAFT)
                .set("status", PUBLISHED);
        if (assessmentMapper.update(null, update) != 1) {
            throw new BadRequestException("assessment is not publishable");
        }
    }

    private Long requireUser() {
        Long user=UserContext.getCurrentUser();
        if(user==null || user<=0) throw new UnauthorizedException("用户未登录");
        return user;
    }

    public List<Assessment> owned() {
        return assessmentMapper.selectList(new QueryWrapper<Assessment>().eq("owner_id",requireUser())
                .orderByDesc("id").last("LIMIT 100"));
    }

    public List<Attempt> attempts(Long id) {
        Long user=requireUser();Assessment assessment=assessmentMapper.selectById(id);
        if(assessment==null || !user.equals(assessment.getOwnerId())) throw new ForbiddenException("无权读取此考核");
        return attemptMapper.selectList(new QueryWrapper<Attempt>().eq("assessment_id",id).orderByDesc("id").last("LIMIT 100"));
    }
}
