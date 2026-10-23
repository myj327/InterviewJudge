package com.decade.doj.problem.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.Assessment;
import com.decade.doj.problem.domain.po.Problem;
import com.decade.doj.problem.mapper.AssessmentMapper;
import com.decade.doj.problem.mapper.ProblemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private AssessmentMapper assessmentMapper;

    @Mock
    private ProblemMapper problemMapper;

    @InjectMocks
    private AssessmentService assessmentService;

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void createsAndPublishesForSameOwner() {
        UserContext.setCurrentUser(7L);
        Assessment assessment = validAssessment();
        when(problemMapper.selectById(21L)).thenReturn(new Problem().setId(21L));
        doAnswer(invocation -> {
            invocation.<Assessment>getArgument(0).setId(100L);
            return 1;
        }).when(assessmentMapper).insert(any(Assessment.class));
        when(assessmentMapper.update(isNull(), any())).thenReturn(1);

        assertEquals(100L, assessmentService.create(assessment));
        assertEquals(7L, assessment.getOwnerId());
        assertEquals("DRAFT", assessment.getStatus());
        assessmentService.publish(100L);

        UpdateWrapper<Assessment> update = capturedUpdate(1).get(0);
        update.getSqlSet();
        update.getSqlSegment();
        assertTrue(update.getParamNameValuePairs().containsValue(100L));
        assertTrue(update.getParamNameValuePairs().containsValue(7L));
        assertTrue(update.getParamNameValuePairs().containsValue("DRAFT"));
        assertTrue(update.getParamNameValuePairs().containsValue("PUBLISHED"));
    }

    @Test
    void rejectsInvalidTimeWindowWithoutWrite() {
        UserContext.setCurrentUser(7L);
        LocalDateTime now = LocalDateTime.of(2026, 9, 2, 9, 0);
        Assessment assessment = validAssessment().setStartsAt(now).setEndsAt(now);

        assertThrows(BadRequestException.class, () -> assessmentService.create(assessment));
        verifyNoInteractions(problemMapper, assessmentMapper);
    }

    @Test
    void rejectsNonOwnerOrNonDraftPublishWithoutUpdate() {
        when(assessmentMapper.update(isNull(), any())).thenReturn(0);

        UserContext.setCurrentUser(8L);
        assertThrows(BadRequestException.class, () -> assessmentService.publish(100L));
        UserContext.setCurrentUser(7L);
        assertThrows(BadRequestException.class, () -> assessmentService.publish(100L));

        java.util.List<UpdateWrapper<Assessment>> updates = capturedUpdate(2);
        updates.forEach(update -> {
            update.getSqlSet();
            update.getSqlSegment();
        });
        assertTrue(updates.get(0).getParamNameValuePairs().containsValue(8L));
        assertTrue(updates.get(1).getParamNameValuePairs().containsValue(7L));
        assertTrue(updates.stream().allMatch(update ->
                update.getParamNameValuePairs().containsValue("DRAFT")));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private java.util.List<UpdateWrapper<Assessment>> capturedUpdate(int calls) {
        ArgumentCaptor<Wrapper<Assessment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(assessmentMapper, times(calls)).update(isNull(), captor.capture());
        return captor.getAllValues().stream()
                .map(wrapper -> (UpdateWrapper<Assessment>) wrapper)
                .toList();
    }

    private Assessment validAssessment() {
        return new Assessment()
                .setProblemId(21L)
                .setTitle("Backend Java Interview")
                .setStartsAt(LocalDateTime.of(2026, 9, 2, 9, 0))
                .setEndsAt(LocalDateTime.of(2026, 9, 2, 10, 0));
    }
}
