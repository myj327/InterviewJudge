package com.decade.doj.sandbox.service.impl;

import com.decade.doj.common.mq.ConfirmedPublisher;
import com.decade.doj.sandbox.domain.vo.ExecuteMessage;
import com.decade.doj.sandbox.domain.vo.JudgingTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class SandboxVerdictOutputTest {
    @TempDir Path root;

    @Test void correctAnswersIncludingLiteralTimerTextRemainAccepted() throws Exception {
        for (String answer : List.of("5", "Command being timed: literal answer\nextra answer line")) {
            SyntheticSandbox service = service(List.of("", answer, answer));
            service.execute(task(answer).setSmokeInput("input.txt").setSmokeOutput(answer));
            assertEquals("Accepted", service.events.result.getStatus());
            assertEquals("VERDICT_READY", service.events.result.getJudgeStage());
            assertEquals(3, service.execCommands.size());
        }
    }

    @Test void extraOutputWithTimerMarkersIsWrongInLegacySmokeAndVerdictPaths() throws Exception {
        for (String extra : List.of("5\nunexpected extra output",
                "5\nCommand being timed: candidate text\nunexpected extra output",
                "5\nCommand being timed: first\nCommand being timed: last\nunexpected extra output")) {
            for (int phase = 0; phase < 3; phase++) {
                List<String> outputs = phase == 0 ? List.of("",extra)
                        : phase == 1 ? List.of("", extra) : List.of("", "5", extra);
                SyntheticSandbox service = service(outputs);
                JudgingTask task = task("5");
                if (phase != 0) task.setSmokeInput("input.txt").setSmokeOutput("5");
                service.execute(task);
                ExecuteMessage result = service.events.result;
                assertEquals("Wrong Answer", result.getStatus());
                assertEquals("Wrong Answer", result.getMessage());
                assertEquals(11, result.getExitValue());
                assertEquals(phase == 0 ? null : phase == 1 ? "SMOKE_FAILED" : "VERDICT_READY", result.getJudgeStage());
                assertEquals(outputs.size(), service.execCommands.size());
                assertTrue(service.outputs.isEmpty());
            }
        }
    }

    @Test void printedStatisticsCannotSupplyMetricsOrIntroduceAWrapper() throws Exception {
        String printed = "5\nMaximum resident set size (kbytes): 128\n"
                + "Elapsed (wall clock) time (h:mm:ss or m:ss): 0:999999.00";
        SyntheticSandbox service = service(List.of("",printed));
        long started = System.nanoTime();
        service.execute(task(printed));
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
        ExecuteMessage result = service.events.result;
        assertEquals("Accepted", result.getStatus());
        assertEquals(-1L, result.getMemory());
        assertTrue(result.getTime() >= 0 && result.getTime() <= elapsed);
        String command = service.execCommands.get(1).get(service.execCommands.get(1).size() - 1);
        assertTrue(command.startsWith("timeout --kill-after=1s 2s sh -c "));
        assertFalse(command.contains("/usr/bin/time"));
    }

    private JudgingTask task(String answer) throws IOException {
        Files.writeString(root.resolve("Main.java"), "class Main {}\n");
        Files.writeString(root.resolve("input.txt"), "synthetic input\n");
        return new JudgingTask().setSubmissionId(100L).setLang("java").setFilename("Main.java")
                .setLocalPath(root.resolve("Main.java").toString()).setInput("input.txt").setOutput(answer);
    }

    private SyntheticSandbox service(List<String> outputs) {
        SyntheticSandbox service = new SyntheticSandbox(outputs);
        setField(service, "containerCodePath", root.toString());
        setField(service, "hostCodePath", "");
        setField(service, "publisher", service.events);
        return service;
    }

    private static final class CapturedPublisher extends ConfirmedPublisher {
        private ExecuteMessage result;
        CapturedPublisher() { super(null); }
        @Override public void publish(String route, Object body) {
            result = (ExecuteMessage) ((Map<?, ?>) body).get("executeMessage");
        }
    }

    private static final class SyntheticSandbox extends SandboxService {
        private final Queue<String> outputs;
        private final List<List<String>> execCommands = new ArrayList<>();
        private final CapturedPublisher events = new CapturedPublisher();
        SyntheticSandbox(List<String> outputs) { super(null, null); this.outputs = new ArrayDeque<>(outputs); }
        @Override protected Process startSandboxProcess(ProcessBuilder builder) {
            if (builder.command().contains("exec")) {
                execCommands.add(List.copyOf(builder.command()));
                return new SyntheticProcess(outputs.remove());
            }
            return new SyntheticProcess("");
        }
    }

    private static final class SyntheticProcess extends Process {
        private final InputStream output;
        SyntheticProcess(String text) { output = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return output; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { }
    }
}
