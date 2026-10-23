package com.decade.doj.sandbox.controller;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.common.domain.po.Submission;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import com.decade.doj.sandbox.service.ISandboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttemptSubmissionControllerTest {

    @TempDir
    Path tempDir;

    private final SubmissionClient submissionClient = mock(SubmissionClient.class);
    private final ProblemClient problemClient = mock(ProblemClient.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ListOperations<String, String> queue = mock(ListOperations.class);

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void queuesOnlyFirstRequestWithResolvedProblemId() throws Exception {
        UserContext.setCurrentUser(7L);
        when(submissionClient.receipt(any(), eq("synthetic-service-token"))).thenReturn(R.ok(null), R.ok(100L));
        when(problemClient.resolveProblemId(10L)).thenReturn(R.ok(20L));
        when(problemClient.getJudgingProblemById(20L, "synthetic-service-token")).thenReturn(R.ok(new Problem()
                .setInputSample(List.of("1 2"))
                .setOutputSample(List.of("3"))
                .setTestData("input")
                .setTestAns("output")));
        when(submissionClient.intake(any(JudgingRequest.class), eq("synthetic-service-token"))).thenReturn(R.ok(100L));
        SandboxController controller = controller();

        R<Long> first = controller.validateAttempt(10L, file(), "java", "request-1");
        R<Long> second = controller.validateAttempt(10L, file(), "java", "request-1");

        assertEquals(100L, first.getData());
        assertEquals(100L, second.getData());
        ArgumentCaptor<JudgingRequest> request = ArgumentCaptor.forClass(JudgingRequest.class);
        verify(submissionClient).intake(request.capture(), eq("synthetic-service-token"));
        assertEquals(20L, request.getAllValues().get(0).getSubmission().getProblemId());
        JudgingTask task = JSON.parseObject(JSON.toJSONString(request.getAllValues().get(0).getTask()), JudgingTask.class);
        verifyNoInteractions(redisTemplate);
        assertEquals(20L, task.getProblemId());
        assertEquals("smoke_input.txt", task.getSmokeInput());
        assertEquals("3", task.getSmokeOutput());
        try (Stream<Path> files = java.nio.file.Files.walk(tempDir)) {
            Path smokeInput = files.filter(path -> path.getFileName().toString().equals(task.getSmokeInput()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("1 2", java.nio.file.Files.readString(smokeInput));
        }
    }

    @Test
    void rejectsAttemptQualificationFailureBeforeCreatingOrQueueing() throws Exception {
        when(submissionClient.receipt(any(), eq("synthetic-service-token"))).thenReturn(R.ok(null));
        when(problemClient.resolveProblemId(10L)).thenReturn(R.error("not eligible"));

        R<Long> result = controller().validateAttempt(10L, file(), "java", "request-1");

        assertFalse(result.success());
        verify(submissionClient, never()).intake(any(), anyString());
        verifyNoInteractions(redisTemplate);
    }

    private SandboxController controller() {
        ResourceProperties properties = new ResourceProperties();
        properties.setCodePath(tempDir.toString() + java.io.File.separator);
        SandboxController controller = new SandboxController(properties, mock(ISandboxService.class), submissionClient, problemClient, redisTemplate);
        ReflectionTestUtils.setField(controller, "internalToken", "synthetic-service-token");
        return controller;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "Main.java", "text/plain", "class Main {}".getBytes());
    }
}
