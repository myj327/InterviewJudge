package com.decade.doj.sandbox.service.impl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
final class SandboxSession implements AutoCloseable {
    private final String name = "ij-" + UUID.randomUUID();
    private final SandboxProcess process;
    final Path source;
    SandboxSession(SandboxProcess process, String path, String filename, String root, String host) throws IOException {
        this(process,path,filename,root,host,128);
    }
    SandboxSession(SandboxProcess process,String path,String filename,String root,String host,int memory) throws IOException {
        this.process = process;
        this.source = SandboxCommand.source(path, filename, root);
        try {
            SandboxProcess.Result created = process.run(SandboxCommand.create(name, source, root, host,memory), null, 5000);
            if (created.exit() != 0) throw new IOException("Sandbox container unavailable");
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    SandboxProcess.Result run(String command, String input, int seconds) throws IOException {
        return runMillis(command,input,seconds*1000);
    }
    SandboxProcess.Result runMillis(String command,String input,int millis) throws IOException {
        return process.run(SandboxCommand.execMillis(name, command, millis), SandboxCommand.input(source, input), millis+5000L);
    }
    @Override public void close() throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            if (process.run(List.of("docker","rm","--force",name), null, 5000).exit() != 0)
                throw new IOException("Sandbox container removal not confirmed");
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }
}
