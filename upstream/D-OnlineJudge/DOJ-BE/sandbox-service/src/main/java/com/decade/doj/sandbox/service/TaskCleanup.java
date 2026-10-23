package com.decade.doj.sandbox.service;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.client.SubmissionClient;
import com.decade.doj.common.config.properties.ResourceProperties;
import com.decade.doj.common.domain.po.Submission;
import com.decade.doj.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.time.Instant;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class TaskCleanup {
    private final ResourceProperties resources;
    private final SubmissionClient submissions;
    @Value("${DOJ_INTERNAL_TOKEN:}") private String token;

    @Scheduled(fixedDelay=30000)
    public void scan() {
        Path root=Path.of(resources.getCodePath()).toAbsolutePath().normalize();
        if(!Files.isDirectory(root)) return;
        try(var directories=Files.list(root)) {
            for(Path dir:directories.toList()) {
                if(!dir.getFileName().toString().matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) continue;
                try { clean(dir.getFileName().toString()); }
                catch(Exception failure) { log.warn("Task cleanup deferred: {}",failure.getClass().getSimpleName()); }
            }
        } catch(Exception failure) { log.warn("Task cleanup unavailable: {}",failure.getClass().getSimpleName()); }
    }

    public void clean(String folder) throws Exception {
        Path dir=TaskFiles.directory(resources.getCodePath(),folder);
        Path marker=dir.resolve(TaskFiles.MARKER);
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS)) return;
        var data=JSON.parseObject(Files.readString(marker));
        boolean aged=Files.getLastModifiedTime(marker).toInstant().isBefore(Instant.now().minusSeconds(86400));
        if("RUN".equals(data.getString("mode"))) { if(aged) TaskFiles.delete(resources.getCodePath(),folder); return; }
        if(!"JUDGE".equals(data.getString("mode"))) return;
        String filename=data.getString("filename");
        if(filename==null || !filename.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*")) return;
        Path source=dir.resolve(filename);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)) return;
        Submission request=new Submission().setUserId(data.getLong("userId")).setAttemptId(data.getLong("attemptId"))
                .setIdempotencyKey(data.getString("idempotencyKey")).setProblemId(data.getLong("problemId"))
                .setLanguage(data.getString("language")).setCode(Files.readString(source));
        if(request.getUserId()==null || request.getUserId()<=0) return;
        Long previous=UserContext.getCurrentUser();
        try {
            UserContext.setCurrentUser(request.getUserId());
            var state=submissions.artifactState(request,token);
            if(state!=null && (state.getCode()==409 || (state.success() && ("FINISHED".equals(state.getData())
                    || (aged && "ABSENT".equals(state.getData())))))) TaskFiles.delete(resources.getCodePath(),folder);
        } finally { if(previous==null) UserContext.clear(); else UserContext.setCurrentUser(previous); }
    }
}
