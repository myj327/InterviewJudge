package com.decade.doj.sandbox.controller;

import com.decade.doj.common.client.ProblemClient;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.domain.R;
import com.decade.doj.sandbox.domain.vo.ExecuteMessage;
import com.decade.doj.sandbox.service.ISandboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SandboxUploadSecurityTest {
    @TempDir
    Path tempDir;

    @Test
    void savesNormalSourceInsideTheConfiguredRoot() throws Exception {
        Path root = tempDir.resolve("uploads");
        ISandboxService sandbox = sandbox();
        CompletableFuture<ExecuteMessage> execution=new CompletableFuture<>();
        when(sandbox.runCodeInSandbox(anyString(),anyString(),anyString())).thenReturn(execution);
        CompletableFuture<R<ExecuteMessage>> response=controller(root, sandbox).runCode(file("Main.java"), "java");
        try (var files = Files.walk(root)) {
            Path saved = files.filter(path->path.getFileName().toString().equals("Main.java")).findFirst().orElseThrow();
            assertTrue(saved.normalize().startsWith(root.normalize()));
            assertEquals("Main.java", saved.getFileName().toString());
            assertEquals("class Main {}", Files.readString(saved));
        }
        verify(sandbox).runCodeInSandbox(anyString(), eq("Main.java"), eq("java"));
        execution.complete(new ExecuteMessage());assertTrue(response.get().success());
        try(var directories=Files.list(root)){assertEquals(0,directories.count());}
    }

    @Test
    void rejectsTraversalAbsolutePathsAndShellNamesBeforeWritingOrExecuting() throws Exception {
        Path sentinel = tempDir.resolve("sentinel.txt");
        Files.writeString(sentinel, "unchanged");
        ISandboxService sandbox = sandbox();
        SandboxController controller = controller(tempDir.resolve("uploads"), sandbox);
        for (String name : List.of("../../sentinel.txt", "..\\..\\sentinel.txt", "/tmp/Main.java",
                "C:\\temp\\Main.java", "Main;touch_pwned.java", "Main$(id).java")) {
            R<ExecuteMessage> response = controller.runCode(file(name), "java").get();
            assertAll(name,
                    () -> assertFalse(response.success()),
                    () -> assertEquals("unchanged", Files.readString(sentinel)),
                    () -> verifyNoInteractions(sandbox));
        }
    }

    private SandboxController controller(Path root, ISandboxService sandbox) {
        ResourceProperties properties = new ResourceProperties();
        properties.setCodePath(root + java.io.File.separator);
        return new SandboxController(properties, sandbox, mock(SubmissionClient.class),
                mock(ProblemClient.class), mock(StringRedisTemplate.class));
    }

    private ISandboxService sandbox() {
        ISandboxService sandbox = mock(ISandboxService.class);
        when(sandbox.runCodeInSandbox(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(new ExecuteMessage()));
        return sandbox;
    }

    private MockMultipartFile file(String filename) {
        return new MockMultipartFile("file", filename, "text/plain",
                "class Main {}".getBytes(StandardCharsets.UTF_8));
    }
}
