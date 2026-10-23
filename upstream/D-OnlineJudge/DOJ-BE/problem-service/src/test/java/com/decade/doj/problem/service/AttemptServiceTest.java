package com.decade.doj.problem.service;

import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.problem.domain.po.Assessment;
import com.decade.doj.problem.domain.po.Attempt;
import com.decade.doj.problem.mapper.AssessmentMapper;
import com.decade.doj.problem.mapper.AttemptMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    @Mock
    private AssessmentMapper assessmentMapper;

    @Mock
    private AttemptMapper attemptMapper;

    @InjectMocks
    private AttemptService attemptService;

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void startsAttemptForCurrentCandidateDuringOpenWindow() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        UserContext.setCurrentUser(7L);
        when(assessmentMapper.selectById(10L)).thenReturn(assessment("PUBLISHED",
                now.minusHours(1), now.plusHours(1)));
        doAnswer(invocation -> {
            invocation.<Attempt>getArgument(0).setId(100L);
            return 1;
        }).when(attemptMapper).insert(any(Attempt.class));

        assertEquals(100L, attemptService.start(10L));

        ArgumentCaptor<Attempt> captor = ArgumentCaptor.forClass(Attempt.class);
        verify(attemptMapper).insert(captor.capture());
        Attempt attempt = captor.getValue();
        assertEquals(10L, attempt.getAssessmentId());
        assertEquals(7L, attempt.getCandidateId());
        assertEquals("STARTED", attempt.getStatus());
        assertNull(attempt.getSubmittedAt());
    }

    @Test
    void rejectsDraftWithoutWrite() {
        UserContext.setCurrentUser(7L);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        when(assessmentMapper.selectById(10L)).thenReturn(assessment("DRAFT",
                now.minusHours(1), now.plusHours(1)));

        assertThrows(BadRequestException.class, () -> attemptService.start(10L));
        verify(attemptMapper, never()).insert(any(Attempt.class));
    }

    @Test
    void rejectsExpiredAssessmentWithoutWrite() {
        UserContext.setCurrentUser(7L);
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        when(assessmentMapper.selectById(10L)).thenReturn(assessment("PUBLISHED",
                now.minusHours(2), now.minusHours(1)));

        assertThrows(BadRequestException.class, () -> attemptService.start(10L));
        verify(attemptMapper, never()).insert(any(Attempt.class));
    }

    @Test
    void resolvesProblemIdForCurrentCandidateWithValidAttempt() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        UserContext.setCurrentUser(7L);
        when(attemptMapper.selectById(100L)).thenReturn(new Attempt()
                .setAssessmentId(10L)
                .setCandidateId(7L)
                .setStatus("STARTED"));
        when(assessmentMapper.selectById(10L)).thenReturn(assessment("PUBLISHED",
                now.minusHours(1), now.plusHours(1)).setProblemId(20L));

        assertEquals(20L, attemptService.resolveProblemId(100L));

        verify(attemptMapper, never()).insert(any(Attempt.class));
        verify(assessmentMapper, never()).insert(any(Assessment.class));
    }

    @Test
    void rejectsOtherCandidatesAttemptWithoutWrite() {
        UserContext.setCurrentUser(7L);
        when(attemptMapper.selectById(100L)).thenReturn(new Attempt()
                .setAssessmentId(10L)
                .setCandidateId(8L)
                .setStatus("STARTED"));

        assertThrows(BadRequestException.class, () -> attemptService.resolveProblemId(100L));

        verify(attemptMapper, never()).insert(any(Attempt.class));
        verify(assessmentMapper, never()).insert(any(Assessment.class));
    }

    @Test
    void rejectsMissingUserContextWithoutWrite() {
        assertThrows(BadRequestException.class, () -> attemptService.resolveProblemId(100L));

        verifyNoInteractions(attemptMapper, assessmentMapper);
    }

    @Test
    void rejectsExpiredAssessmentForAttemptWithoutWrite() {
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        UserContext.setCurrentUser(7L);
        when(attemptMapper.selectById(100L)).thenReturn(new Attempt()
                .setAssessmentId(10L)
                .setCandidateId(7L)
                .setStatus("STARTED"));
        when(assessmentMapper.selectById(10L)).thenReturn(assessment("PUBLISHED",
                now.minusHours(2), now.minusHours(1)));

        assertThrows(BadRequestException.class, () -> attemptService.resolveProblemId(100L));

        verify(attemptMapper, never()).insert(any(Attempt.class));
        verify(assessmentMapper, never()).insert(any(Assessment.class));
    }

    private Assessment assessment(String status, LocalDateTime startsAt, LocalDateTime endsAt) {
        return new Assessment()
                .setId(10L)
                .setStartsAt(startsAt)
                .setEndsAt(endsAt)
                .setStatus(status);
    }
}
