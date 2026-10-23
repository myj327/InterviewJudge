package com.decade.doj.sandbox.service.impl;

import com.decade.doj.sandbox.domain.vo.ExecuteMessage;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SandboxResultConfidentialityTest {
    private static final String HIDDEN_INPUT = "synthetic-hidden-input";
    private static final String HIDDEN_ANSWER = "synthetic-hidden-answer";
    @TempDir
    Path tempDir;

    @Test
    void judgedResultsPreserveVerdictsWithoutReturningHiddenDataOrRawOutput() throws Exception {
        List<Executable> checks = new ArrayList<>();
        for (boolean staged : new boolean[]{true, false}) {
            for (Outcome outcome : List.of(new Outcome(0, HIDDEN_INPUT, "Wrong Answer", 11),
                    new Outcome(0, HIDDEN_ANSWER, "Accepted", 10),
                    new Outcome(1, HIDDEN_INPUT, "Runtime Error", 1))) {
                checks.add(() -> {
                    RabbitTemplate mq = mock(RabbitTemplate.class);
                    FakeProcessSandbox service = new FakeProcessSandbox(mq);
                    org.springframework.test.util.ReflectionTestUtils.setField(service, "containerCodePath", tempDir.toString());
                    service.outputs.add(process(0, "")); // Both judging paths compile before execution.
                    if (staged) {
                        service.outputs.add(process(0, "public answer"));
                    }
                    service.outputs.add(process(outcome.exitCode, outcome.output));
                    JudgingTask task = task();
                    if (staged) {
                        task.setSmokeInput("smoke_input.txt").setSmokeOutput("public answer");
                    }
                    service.execute(task);
                    ExecuteMessage result = published(mq);
                    assertAll((staged ? "staged " : "legacy ") + outcome.status,
                            () -> assertEquals(outcome.status, result.getStatus()),
                            () -> assertEquals(outcome.verdictExitCode, result.getExitValue()),
                            () -> assertFalse(result.getMessage().contains(HIDDEN_INPUT)),
                            () -> assertFalse(result.getMessage().contains(HIDDEN_ANSWER)),
                            () -> assertEquals(outcome.status, result.getMessage()));
                });
            }
        }
        checks.add(() -> {
            RabbitTemplate mq = mock(RabbitTemplate.class);
            FakeProcessSandbox service = new FakeProcessSandbox(mq);
                    org.springframework.test.util.ReflectionTestUtils.setField(service, "containerCodePath", tempDir.toString());
            service.outputs.add(process(0, ""));
            service.outputs.add(process(0, HIDDEN_INPUT));
            service.execute(task().setSmokeInput("smoke_input.txt").setSmokeOutput("public answer"));
            ExecuteMessage result = published(mq);
            assertEquals("SMOKE_FAILED", result.getJudgeStage());
            assertEquals("Wrong Answer", result.getMessage());
            assertTrue(service.outputs.isEmpty());
        });
        assertAll(checks);
    }

    private JudgingTask task() throws Exception {
        java.nio.file.Files.writeString(tempDir.resolve("Main.java"), "class Main {}");
        java.nio.file.Files.writeString(tempDir.resolve("hidden_input.txt"), HIDDEN_INPUT);
        java.nio.file.Files.writeString(tempDir.resolve("smoke_input.txt"), "public");
        return new JudgingTask().setSubmissionId(100L).setUid(7L).setProblemId(20L)
                .setLang("java").setFilename("Main.java")
                .setLocalPath(tempDir.resolve("Main.java").toString())
                .setInput("hidden_input.txt").setOutput(HIDDEN_ANSWER);
    }

    private Process process(int exitCode, String output) throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
        when(process.waitFor(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(java.util.concurrent.TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(exitCode);
        when(process.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
        return process;
    }

    private ExecuteMessage published(RabbitTemplate mq) {
        ArgumentCaptor<Map> event = ArgumentCaptor.forClass(Map.class);
        verify(mq).convertAndSend(eq("doj.topic"), eq("judging.result"), event.capture());
        return (ExecuteMessage) event.getValue().get("executeMessage");
    }

    private record Outcome(int exitCode, String output, String status, int verdictExitCode) { }

    private static class FakeProcessSandbox extends SandboxService {
        private final Queue<Process> outputs = new ArrayDeque<>();

        FakeProcessSandbox(RabbitTemplate mq) {
            super(mq, mock(ThreadPoolTaskExecutor.class));
            com.decade.doj.common.mq.ConfirmedPublisher publisher = mock(com.decade.doj.common.mq.ConfirmedPublisher.class);
            doAnswer(call -> { mq.convertAndSend("doj.topic", "judging.result", (Object) call.getArgument(1)); return null; }).when(publisher).publish(eq("judging.result"), any());
            org.springframework.test.util.ReflectionTestUtils.setField(this, "publisher", publisher);
        }

        @Override
        protected Process startSandboxProcess(ProcessBuilder builder) {
            if (builder.command().contains("exec")) return outputs.remove();
            Process control = mock(Process.class);
            when(control.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
            when(control.getOutputStream()).thenReturn(new java.io.ByteArrayOutputStream());
            try { when(control.waitFor(anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(true); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            return control;
        }
    }
}
