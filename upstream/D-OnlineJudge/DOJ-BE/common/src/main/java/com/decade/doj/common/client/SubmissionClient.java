package com.decade.doj.common.client;

import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.po.Submission;
import com.decade.doj.common.domain.vo.SubmissionStatsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.decade.doj.common.domain.dto.JudgingRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@FeignClient(value = "submission-service", configuration = SubmissionClient.PrivateLogConfig.class)
public interface SubmissionClient {

    @PostMapping("/submission/internal/intake")
    R<Long> intake(@RequestBody JudgingRequest request, @RequestHeader("X-DOJ-Internal-Token") String token);

    @PostMapping("/submission/internal/receipt")
    R<Long> receipt(@RequestBody Submission request, @RequestHeader("X-DOJ-Internal-Token") String token);

    @PostMapping("/submission/internal/artifact-state")
    R<String> artifactState(@RequestBody Submission request, @RequestHeader("X-DOJ-Internal-Token") String token);

    @GetMapping("/submission/internal/finished/{id}")
    R<Boolean> finished(@PathVariable("id") Long id, @RequestHeader("X-DOJ-Internal-Token") String token);

    class PrivateLogConfig {
        @Bean @Primary public feign.Logger.Level intakeLogLevel() { return feign.Logger.Level.NONE; }
    }

    @GetMapping("/submission/stats")
    R<SubmissionStatsVO> getStats();

}
