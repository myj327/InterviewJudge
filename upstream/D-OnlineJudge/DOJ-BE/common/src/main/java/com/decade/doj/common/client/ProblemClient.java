package com.decade.doj.common.client;

import com.decade.doj.common.domain.R;
import com.decade.doj.common.domain.po.Problem;
import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(value = "problem-service", configuration = ProblemClient.PrivateDataClientConfiguration.class)
public interface ProblemClient {

    @GetMapping("/problem/{id}")
    R<Problem> getProblemById(@PathVariable("id") Long id);

    @GetMapping("/problem/internal/{id}")
    R<Problem> getJudgingProblemById(@PathVariable("id") Long id,
            @RequestHeader("X-InterviewJudge-Internal-Token") String internalToken);

    @GetMapping("/problem/count")
    R<Long> getCount();

    @GetMapping("/assessments/attempts/{attemptId}/problem-id")
    R<Long> resolveProblemId(@PathVariable("attemptId") Long attemptId);

    @GetMapping("/assessments/attempts/{attemptId}/reader")
    R<Boolean> canReadAttempt(@PathVariable("attemptId") Long attemptId);

    class PrivateDataClientConfiguration {
        @Bean
        @Primary
        public Logger.Level privateDataFeignLogLevel() {
            return Logger.Level.NONE;
        }
    }

}
