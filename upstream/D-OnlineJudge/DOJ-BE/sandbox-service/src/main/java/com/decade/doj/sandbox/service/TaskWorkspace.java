package com.decade.doj.sandbox.service;

import com.decade.doj.sandbox.domain.vo.JudgingTask;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** A delivery owns its files; producer paths are never opened by a Worker. */
public final class TaskWorkspace implements AutoCloseable {
    private final String root;
    private final String folder;

    private TaskWorkspace(String root, String folder) { this.root = root; this.folder = folder; }

    public static TaskWorkspace restore(String root, JudgingTask task) throws IOException {
        String filename = task.getFilename();
        if (filename == null || !filename.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*\\.java")
                || task.getCode() == null || task.getInputData() == null
                || (task.getSmokeInput() != null) != (task.getSmokeInputData() != null))
            throw new IllegalArgumentException("Incomplete judging snapshot or unsafe filename");
        Path base = Path.of(root).toAbsolutePath().normalize().toRealPath();
        String folder = UUID.randomUUID().toString();
        Path directory = Files.createDirectory(base.resolve(folder));
        TaskWorkspace workspace = new TaskWorkspace(base.toString(), folder);
        try {
            // RUN markers allow existing age-based cleanup after an abrupt process exit.
            TaskFiles.mark(base.toString(), folder, filename, null);
            Files.writeString(directory.resolve(filename), task.getCode(), StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("full-input.txt"), task.getInputData(), StandardOpenOption.CREATE_NEW);
            task.setLocalPath(directory.resolve(filename).toString()).setInput("full-input.txt");
            if (task.getSmokeInputData() != null) {
                Files.writeString(directory.resolve("smoke-input.txt"), task.getSmokeInputData(), StandardOpenOption.CREATE_NEW);
                task.setSmokeInput("smoke-input.txt");
            }
            return workspace;
        } catch (IOException | RuntimeException failure) {
            try { workspace.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    @Override public void close() throws IOException { TaskFiles.delete(root, folder); }
}
