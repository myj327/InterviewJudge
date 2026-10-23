package com.decade.doj.sandbox.service.impl;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
final class SandboxProcess {
    static final int OUTPUT_LIMIT = 64 * 1024;
    interface Starter { Process start(ProcessBuilder builder) throws IOException; }
    record Result(int exit, String output, double seconds) { }
    private final Starter starter;
    SandboxProcess(Starter starter) { this.starter = starter; }
    Result run(List<String> command, Path input, long timeoutMillis) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (input != null) builder.redirectInput(input.toFile());
        long started = System.nanoTime();
        long deadline = started + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        Process process = starter.start(builder);
        if (input == null) process.getOutputStream().close();
        ExecutorService reader = Executors.newSingleThreadExecutor(action -> {
            Thread thread = new Thread(action, "SandboxOutput");
            thread.setDaemon(true); return thread;
        });
        Future<byte[]> output = reader.submit(() -> process.getInputStream().readNBytes(OUTPUT_LIMIT + 1));
        try {
            byte[] bytes = output.get(remaining(deadline), TimeUnit.NANOSECONDS);
            if (bytes.length > OUTPUT_LIMIT) return new Result(153, "", elapsed(started));
            if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) return new Result(124, "", elapsed(started));
            return new Result(process.exitValue(), new String(bytes, StandardCharsets.UTF_8).trim(), elapsed(started));
        } catch (TimeoutException timeout) {
            return new Result(124, "", elapsed(started));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("Sandbox interrupted", interrupted);
        } catch (ExecutionException failure) {
            throw new IOException("Sandbox output unavailable", failure.getCause());
        } finally {
            output.cancel(true);
            process.destroyForcibly();
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            reader.shutdownNow();
        }
    }
    private long remaining(long deadline) { return Math.max(1, deadline - System.nanoTime()); }
    private double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000_000.0; }
}
