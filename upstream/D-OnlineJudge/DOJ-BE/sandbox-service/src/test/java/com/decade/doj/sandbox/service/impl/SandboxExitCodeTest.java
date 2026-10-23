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

class SandboxExitCodeTest {
    @TempDir Path root;
    record Exit(int code, String text) { }

    @Test void userExitTenCannotAcceptLegacySmokeOrFullJudging() throws Exception {
        for (int code : List.of(10, 11, 2, 42)) {
            for (int phase = 0; phase < 3; phase++) {
                List<Exit> exits = phase == 0 ? List.of(new Exit(0, ""),new Exit(code, "5"))
                        : phase == 1 ? List.of(new Exit(0, ""), new Exit(code, "5"))
                        : List.of(new Exit(0, ""), new Exit(0, "5"), new Exit(code, "5"));
                FakeSandbox service = service(exits);
                service.execute(task(phase > 0));
                assertEquals("Runtime Error", service.events.result.getStatus(), "raw code " + code + ", phase " + phase);
                assertEquals(1, service.events.result.getExitValue());
                assertEquals(phase == 0 ? null : phase == 1 ? "SMOKE_FAILED" : "VERDICT_READY", service.events.result.getJudgeStage());
                assertEquals(exits.size(), service.runs);
            }
        }
    }

    @Test void onlyNormalExitWithEqualOutputAccepts() throws Exception {
        for (String answer : List.of("5", "5\nextra")) {
            FakeSandbox service = service(List.of(new Exit(0, ""), new Exit(0, "5"), new Exit(0, answer)));
            service.execute(task(true));
            assertEquals(answer.equals("5") ? "Accepted" : "Wrong Answer", service.events.result.getStatus());
            assertEquals("VERDICT_READY", service.events.result.getJudgeStage());
        }
    }

    @Test void compileAndResourceFailuresNeverAdvanceOrAccept() throws Exception {
        FakeSandbox compile = service(List.of(new Exit(10, "")));
        compile.execute(task(true));
        assertEquals("Compile Error", compile.events.result.getStatus());
        assertEquals("COMPILE_FAILED", compile.events.result.getJudgeStage());
        assertEquals(1, compile.runs);
        for (int code : List.of(124, 137, 153)) {
            FakeSandbox service = service(List.of(new Exit(0,""),new Exit(code, "5")));
            service.execute(task(false));
            assertEquals(code, service.events.result.getExitValue());
            assertNotEquals("Accepted", service.events.result.getStatus());
        }
        FakeSandbox unavailable = service(List.of(new Exit(125, "5")));
        assertThrows(IllegalStateException.class, () -> unavailable.execute(task(false)));
        assertNull(unavailable.events.result);
    }

    private JudgingTask task(boolean staged) throws IOException {
        Files.writeString(root.resolve("Main.java"), "class Main {}\n");
        Files.writeString(root.resolve("input.txt"), "synthetic input\n");
        JudgingTask task = new JudgingTask().setSubmissionId(100L).setLang("java").setFilename("Main.java")
                .setLocalPath(root.resolve("Main.java").toString()).setInput("input.txt").setOutput("5");
        return staged ? task.setSmokeInput("input.txt").setSmokeOutput("5") : task;
    }
    private FakeSandbox service(List<Exit> exits) {
        FakeSandbox service = new FakeSandbox(exits);
        setField(service, "containerCodePath", root.toString());
        setField(service, "hostCodePath", "");
        setField(service, "publisher", service.events);
        return service;
    }
    static final class Events extends ConfirmedPublisher {
        ExecuteMessage result;
        Events() { super(null); }
        @Override public void publish(String route, Object body) { result = (ExecuteMessage) ((Map<?, ?>) body).get("executeMessage"); }
    }
    static final class FakeSandbox extends SandboxService {
        final Queue<Exit> exits;
        final Events events = new Events();
        int runs;
        FakeSandbox(List<Exit> exits) { super(null, null); this.exits = new ArrayDeque<>(exits); }
        @Override protected Process startSandboxProcess(ProcessBuilder builder) {
            if (builder.command().contains("exec")) { runs++; return new FakeProcess(exits.remove()); }
            return new FakeProcess(new Exit(0, ""));
        }
    }
    static class FakeProcess extends Process {
        final Exit exit;
        final InputStream output;
        FakeProcess(Exit exit) { this.exit = exit; output = new ByteArrayInputStream(exit.text().getBytes(StandardCharsets.UTF_8)); }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return output; }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public int waitFor() { return exit.code(); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
        @Override public int exitValue() { return exit.code(); }
        @Override public void destroy() { }
    }
}
