package com.decade.doj.sandbox.service;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.domain.po.Submission;
import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.UUID;

public final class TaskFiles {
    public static final String MARKER=".interviewjudge-task.json";
    private TaskFiles() { }

    public static Path directory(String root,String folder) throws IOException {
        if(!UUID.fromString(folder).toString().equals(folder)) throw new IOException("Invalid task directory");
        Path base=Path.of(root).toAbsolutePath().normalize().toRealPath();
        Path dir=base.resolve(folder).normalize();
        if(!dir.getParent().equals(base) || !Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dir))
            throw new IOException("Task directory is not owned");
        return dir;
    }

    public static void mark(String root,String folder,String filename,Submission submission) throws IOException {
        Path dir=directory(root,folder);var marker=new LinkedHashMap<String,Object>();
        marker.put("mode",submission==null?"RUN":"JUDGE");marker.put("filename",filename);
        if(submission!=null) {
            marker.put("userId",submission.getUserId());marker.put("attemptId",submission.getAttemptId());
            marker.put("idempotencyKey",submission.getIdempotencyKey());marker.put("problemId",submission.getProblemId());
            marker.put("language",submission.getLanguage());
        }
        Path temporary=dir.resolve(MARKER+".tmp");
        Files.writeString(temporary,JSON.toJSONString(marker),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporary,dir.resolve(MARKER),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }

    public static void delete(String root,String folder) throws IOException {
        Path dir=directory(root,folder);
        try(var entries=Files.walk(dir)) {
            for(Path path:entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
