package com.decade.doj.sandbox.service.impl;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
final class SandboxCommand {
    static Path source(String file, String filename, String root) throws IOException {
        if (filename == null || !filename.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*\\.java"))
            throw new IllegalArgumentException("Invalid Java source filename");
        Path base = Paths.get(root).toRealPath();
        Path source = Paths.get(file).toAbsolutePath().normalize();
        if (!source.startsWith(base) || !source.getFileName().toString().equals(filename))
            throw new IllegalArgumentException("Source is outside task storage");
        Path real = source.toRealPath();
        if (!source.equals(real) || !Files.isRegularFile(real))
            throw new IllegalArgumentException("Symlink source is forbidden");
        return real;
    }
    static Path input(Path source, String filename) throws IOException {
        if (filename == null) return null;
        if (!filename.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) throw new IllegalArgumentException("Invalid input filename");
        Path input = source.getParent().resolve(filename);
        if (!input.equals(input.toRealPath()) || !Files.isRegularFile(input)) throw new IllegalArgumentException("Invalid task input");
        return input;
    }
    static List<String> create(String name, Path source, String root, String hostRoot) throws IOException {
        return create(name,source,root,hostRoot,128);
    }
    static List<String> create(String name, Path source, String root, String hostRoot,int memory) throws IOException {
        Path base = Paths.get(root).toRealPath();
        Path host = hostRoot == null || hostRoot.isBlank() ? source
                : Paths.get(hostRoot).resolve(base.relativize(source)).normalize();
        return List.of("docker","run","--detach","--rm","--name",name,
                "--user","65534:65534","--network","none","--cpus","1","--pids-limit","64",
                "--memory",memory+"m","--memory-swap",memory+"m","--cap-drop","ALL",
                "--security-opt","no-new-privileges:true","--read-only",
                "--tmpfs","/work:rw,noexec,nosuid,nodev,size=64m,mode=1777",
                "--tmpfs","/tmp:rw,noexec,nosuid,nodev,size=16m,mode=1777",
                "--ulimit","nofile=64:64","--ulimit","fsize=67108864:67108864",
                "--mount","type=bind,source="+host+",target=/src/"+source.getFileName()+",readonly",
                "--workdir","/work","--entrypoint","/bin/sh","code-runner-java","-c","sleep 60");
    }
    static List<String> exec(String name, String command, int seconds) {
        return execMillis(name,command,seconds*1000);
    }
    static List<String> execMillis(String name,String command,int millis) {
        String seconds=java.math.BigDecimal.valueOf(millis,3).stripTrailingZeros().toPlainString();
        return List.of("docker","exec","--interactive","--user","65534:65534","--workdir","/work",name,
                "/bin/sh","-c","timeout --kill-after=1s "+seconds+"s sh -c '"+command+"'");
    }
}
