package com.decade.doj.submission.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.decade.doj.common.exception.BadRequestException;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.submission.controller.SubmissionController;
import com.decade.doj.submission.domain.po.Submission;
import com.decade.doj.submission.mapper.SubmissionMapper;
import com.decade.doj.submission.service.impl.ISubmissionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubmissionIntakeServiceTest {

    private SubmissionMapper submissionMapper;
    private ISubmissionServiceImpl submissionService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
        submissionMapper = mock(SubmissionMapper.class);
        submissionService = new ISubmissionServiceImpl();
        ReflectionTestUtils.setField(submissionService, "baseMapper", submissionMapper);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void createsServerControlledPendingJavaSubmission() {
        UserContext.setCurrentUser(7L);
        Submission request = validRequest();
        request.setId(99L);
        request.setUserId(8L);
        request.setUserName("client-user");
        request.setProblemName("client-problem");
        request.setExitValue(0);
        request.setStatus("Accepted");
        request.setJudgeStage("FINISHED");
        request.setMessage("client-result");
        request.setTime(1.5);
        request.setMemory(256L);
        request.setSubmitTime(new Date());
        when(submissionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        doAnswer(invocation -> {
            Submission saved = invocation.getArgument(0);
            assertNull(saved.getId());
            saved.setId(100L);
            return 1;
        }).when(submissionMapper).insert(any(Submission.class));

        assertEquals(100L, submissionService.createPending(request));

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionMapper).insert(captor.capture());
        Submission saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("PENDING", saved.getStatus());
        assertEquals("PENDING", saved.getJudgeStage());
        assertNull(saved.getUserName());
        assertNull(saved.getProblemName());
        assertNull(saved.getExitValue());
        assertNull(saved.getMessage());
        assertNull(saved.getTime());
        assertNull(saved.getMemory());
        assertNull(saved.getSubmitTime());
    }

    @Test
    void rejectsMissingIdempotencyKeyOrNonJavaWithoutWrite() {
        Submission missingKey = validRequest();
        missingKey.setIdempotencyKey(" ");
        Submission nonJava = validRequest();
        nonJava.setLanguage("cpp");

        assertThrows(BadRequestException.class, () -> submissionService.createPending(missingKey));
        assertThrows(BadRequestException.class, () -> submissionService.createPending(nonJava));

        verifyNoInteractions(submissionMapper);
    }

    @Test
    void returnsExistingIdForDuplicateKeyWithoutInsert() {
        UserContext.setCurrentUser(7L);
        Submission existing = validRequest();
        existing.setId(100L);
        existing.setUserId(7L);
        when(submissionMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertEquals(100L, submissionService.createPending(validRequest()));

        verify(submissionMapper, never()).insert(any(Submission.class));
    }

    @Test
    void requiresInternalTokenBeforeAtomicIntake() {
        JudgingIntake intake = mock(JudgingIntake.class);
        SubmissionController controller = new SubmissionController(submissionService, intake);
        ReflectionTestUtils.setField(controller, "internalToken", "synthetic-service-token");
        assertThrows(com.decade.doj.common.exception.ForbiddenException.class,
                () -> controller.intake(new com.decade.doj.common.domain.dto.JudgingRequest(), null));
        verifyNoInteractions(intake);
    }
    @Test
    void claimsOnlyPendingSubmissionOwnedByCurrentUser() {
        UserContext.setCurrentUser(7L);
        when(submissionMapper.update(any(), any(Wrapper.class))).thenReturn(1, 0);

        assertTrue(submissionService.claimQueued(100L));
        assertFalse(submissionService.claimQueued(100L));

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionMapper, times(2)).update(any(), captor.capture());
        LambdaUpdateWrapper<Submission> update = (LambdaUpdateWrapper<Submission>) captor.getAllValues().get(0);
        assertTrue(update.getSqlSegment().contains("judge_stage"));
        assertTrue(update.getSqlSet().contains("status"));
        assertTrue(update.getSqlSet().contains("judge_stage"));
        assertTrue(update.getParamNameValuePairs().containsValue("PENDING"));
        assertTrue(update.getParamNameValuePairs().containsValue("QUEUED"));
    }

    @Test
    void completesOnlyQueuedAttemptAndWritesVerdictAndStage() {
        Submission result = new Submission();
        result.setId(100L);
        result.setUserName("candidate");
        result.setProblemName("sum");
        result.setStatus("Compile Error");
        result.setJudgeStage("COMPILE_FAILED");
        result.setExitValue(2);
        result.setMessage("compiler output");
        result.setTime(0.2);
        result.setMemory(10L);
        when(submissionMapper.update(any(), any(Wrapper.class))).thenReturn(1, 0);

        assertTrue(submissionService.completeAttempt(result));
        assertFalse(submissionService.completeAttempt(result));

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(submissionMapper, times(2)).update(any(), captor.capture());
        LambdaUpdateWrapper<Submission> update = (LambdaUpdateWrapper<Submission>) captor.getAllValues().get(0);
        assertTrue(update.getSqlSegment().contains("status"));
        assertTrue(update.getSqlSegment().contains("judge_stage"));
        assertTrue(update.getSqlSet().contains("user_name"));
        assertTrue(update.getSqlSet().contains("problem_name"));
        assertTrue(update.getParamNameValuePairs().containsValue("QUEUED"));
        assertTrue(update.getParamNameValuePairs().containsValue("COMPILE_FAILED"));
        assertTrue(update.getParamNameValuePairs().containsValue("Compile Error"));
    }

    private Submission validRequest() {
        Submission submission = new Submission();
        submission.setAttemptId(10L);
        submission.setIdempotencyKey("request-1");
        submission.setProblemId(21L);
        submission.setLanguage("java");
        submission.setCode("class Main {}");
        return submission;
    }
}
