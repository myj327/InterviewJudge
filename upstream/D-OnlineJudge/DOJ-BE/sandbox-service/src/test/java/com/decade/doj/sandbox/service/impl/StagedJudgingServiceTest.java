package com.decade.doj.sandbox.service.impl;

import com.decade.doj.sandbox.domain.vo.ExecuteMessage;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StagedJudgingServiceTest {

    @Test
    void stopsAfterCompileFailure() {
        TestSandboxService service = service(message(1, "Runtime Error"));

        ExecuteMessage result = execute(service);

        assertEquals(List.of("COMPILE"), service.stages);
        assertEquals("Compile Error", result.getStatus());
        assertEquals("COMPILE_FAILED", result.getJudgeStage());
    }

    @Test
    void stopsAfterSmokeFailure() {
        TestSandboxService service = service(message(0, "Finished"), message(11, "Wrong Answer"));

        ExecuteMessage result = execute(service);

        assertEquals(List.of("COMPILE", "SMOKE"), service.stages);
        assertEquals("Wrong Answer", result.getStatus());
        assertEquals("SMOKE_FAILED", result.getJudgeStage());
    }

    @Test
    void runsAllStagesAndKeepsVerdict() {
        TestSandboxService service = service(message(0, "Finished"), message(10, "Accepted"), message(11, "Wrong Answer"));

        ExecuteMessage result = execute(service);

        assertEquals(List.of("COMPILE", "SMOKE", "VERDICT"), service.stages);
        assertEquals("Wrong Answer", result.getStatus());
        assertEquals("VERDICT_READY", result.getJudgeStage());
    }

    private ExecuteMessage execute(TestSandboxService service) {
        service.execute(new JudgingTask()
                .setSubmissionId(100L)
                .setLang("java")
                .setSmokeInput("1 2")
                .setSmokeOutput("3")
                .setInput("hidden-input")
                .setOutput("hidden-output"));
        ArgumentCaptor<Map> result = ArgumentCaptor.forClass(Map.class);
        verify(service.rabbitTemplate).convertAndSend(eq("doj.topic"), eq("judging.result"), result.capture());
        return (ExecuteMessage) result.getValue().get("executeMessage");
    }

    private TestSandboxService service(ExecuteMessage... results) {
        return new TestSandboxService(mock(RabbitTemplate.class), List.of(results));
    }

    private ExecuteMessage message(int exitValue, String status) {
        return new ExecuteMessage().setExitValue(exitValue).setStatus(status);
    }

    private static class TestSandboxService extends SandboxService {
        private final RabbitTemplate rabbitTemplate;
        private final List<ExecuteMessage> results;
        private final List<String> stages = new ArrayList<>();

        private TestSandboxService(RabbitTemplate rabbitTemplate, List<ExecuteMessage> results) {
            super(rabbitTemplate, mock(ThreadPoolTaskExecutor.class));
            this.rabbitTemplate = rabbitTemplate;
            com.decade.doj.common.mq.ConfirmedPublisher publisher = mock(com.decade.doj.common.mq.ConfirmedPublisher.class);
            org.mockito.Mockito.doAnswer(call -> { rabbitTemplate.convertAndSend("doj.topic", "judging.result", (Object) call.getArgument(1)); return null; })
                    .when(publisher).publish(eq("judging.result"), any());
            org.springframework.test.util.ReflectionTestUtils.setField(this, "publisher", publisher);
            this.results = new ArrayList<>(results);
        }

        @Override
        protected ExecuteMessage runAttemptStage(JudgingTask task, String stage, String rawCmd, String inputname, String answer) {
            stages.add(stage);
            return results.remove(0);
        }
    }
}
