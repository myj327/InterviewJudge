package com.decade.doj.sandbox.service.impl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SandboxLimitsTest {
    @TempDir Path root;
    @Test void containerHasLimitsAndOnlySourceIsMountedReadOnly() throws Exception {
        Path source=Files.writeString(root.resolve("Main.java"),"class Main {}");
        List<String> command=SandboxCommand.create("ij-synthetic",source,root.toString(),"/synthetic-host");
        for(String flag:List.of("--read-only","--cap-drop","--security-opt","--network","--pids-limit","--cpus","--memory-swap","--user"))
            assertTrue(command.contains(flag),flag);
        assertEquals("65534:65534",command.get(command.indexOf("--user")+1));
        assertEquals("none",command.get(command.indexOf("--network")+1));
        assertEquals("ALL",command.get(command.indexOf("--cap-drop")+1));
        String mount=command.get(command.indexOf("--mount")+1);
        assertTrue(mount.endsWith("target=/src/Main.java,readonly"));
        assertFalse(mount.contains("hidden"));assertTrue(command.contains("sleep 60"));
        assertTrue(command.stream().anyMatch(value->value.contains("size=64m")));
        Path hidden=Files.writeString(root.resolve("hidden_input.txt"),"synthetic");
        assertEquals(hidden,SandboxCommand.input(source,"hidden_input.txt"));
        assertFalse(SandboxCommand.exec("ij-synthetic","java Main",2).toString().contains("hidden_input"));
    }
    @Test void outsideSourceAndTraversingInputAreRejectedBeforeDocker() throws Exception {
        Path source=Files.writeString(root.resolve("Main.java"),"class Main {}");
        Path approved=root.resolve("tasks");Files.createDirectory(approved);
        assertThrows(IllegalArgumentException.class,()->SandboxCommand.source(source.toString(),"Main.java",approved.toString()));
        assertThrows(IllegalArgumentException.class,()->SandboxCommand.input(source,"../hidden_input.txt"));
        assertThrows(IllegalArgumentException.class,()->SandboxCommand.source(source.toString(),"../Main.java",root.toString()));
    }
}
