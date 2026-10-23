package com.decade.doj.sandbox.controller;

import com.decade.doj.common.client.*;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.dto.JudgingRequest;
import com.decade.doj.common.domain.po.*;
import com.decade.doj.common.utils.UserContext;
import com.decade.doj.sandbox.service.ISandboxService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttemptReceiptRecoveryTest {
    @TempDir Path root;
    SubmissionClient submissions = mock(SubmissionClient.class);
    ProblemClient problems = mock(ProblemClient.class);
    SandboxController controller;
    @BeforeEach void setup() {
        ResourceProperties properties = new ResourceProperties(); properties.setCodePath(root.toString());
        controller = new SandboxController(properties, mock(ISandboxService.class), submissions, problems, mock(StringRedisTemplate.class));
        ReflectionTestUtils.setField(controller, "internalToken", "synthetic-token"); UserContext.setCurrentUser(7L);
    }
    @AfterEach void clear() { UserContext.clear(); }
    MockMultipartFile file() { return new MockMultipartFile("file", "Main.java", "text/plain", "class Main {}".getBytes()); }
    long folders() throws Exception { try (var paths = Files.list(root)) { return paths.count(); } }

    @Test void acceptedRetryAfterCutoffReturnsReceiptWithoutReadingProblemOrWritingFiles() throws Exception {
        when(submissions.receipt(any(), anyString())).thenReturn(R.ok(100L));
        when(problems.resolveProblemId(anyLong())).thenReturn(R.error(400, "assessment closed"));
        assertEquals(100L, controller.validateAttempt(10L, file(), "java", "same-key").getData());
        assertEquals(0, folders()); verifyNoInteractions(problems);
        verify(submissions, never()).intake(any(), anyString());
    }

    @Test void conflictsFailuresAndUnacceptedExpiredRequestsCannotCreateFiles() throws Exception {
        for (int code : new int[]{409,403,500}) {
            when(submissions.receipt(any(), anyString())).thenReturn(R.error(code, "synthetic refusal"));
            assertEquals(code, controller.validateAttempt(10L, file(), "java", "same-key").getCode());
            assertEquals(0, folders());
        }
        when(submissions.receipt(any(), anyString())).thenReturn(R.ok(null));
        when(problems.resolveProblemId(10L)).thenReturn(R.error(400, "assessment closed"));
        assertEquals(400, controller.validateAttempt(10L, file(), "java", "new-key").getCode());
        assertEquals(0, folders()); verify(submissions, never()).intake(any(), anyString());
    }

    @Test void firstAcceptanceCreatesOneFolderAndIdenticalRetryUsesOriginalId() throws Exception {
        when(submissions.receipt(any(), anyString())).thenReturn(R.ok(null), R.ok(100L));
        when(problems.resolveProblemId(10L)).thenReturn(R.ok(20L));
        when(problems.getJudgingProblemById(20L, "synthetic-token")).thenReturn(R.ok(new Problem()
                .setInputSample(List.of("1 2")).setOutputSample(List.of("3")).setTestData("hidden").setTestAns("3")));
        when(submissions.intake(any(), anyString())).thenReturn(R.ok(100L));
        assertEquals(100L, controller.validateAttempt(10L, file(), "java", "same-key").getData());
        assertEquals(100L, controller.validateAttempt(10L, file(), "java", "same-key").getData());
        assertEquals(1, folders()); verify(submissions).intake(any(JudgingRequest.class), anyString());
        verify(problems).resolveProblemId(10L);
    }
}
