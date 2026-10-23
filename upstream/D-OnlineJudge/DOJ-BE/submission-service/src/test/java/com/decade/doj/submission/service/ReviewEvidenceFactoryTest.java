package com.decade.doj.submission.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.po.Submission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReviewEvidenceFactoryTest {

    @Test
    void serializesOnlyWhitelistedPublicEvidence() {
        Submission submission = new Submission();
        submission.setId(101L);
        submission.setLanguage("java");
        submission.setCode("SOURCE_SENTINEL");
        submission.setStatus("Accepted");
        submission.setJudgeStage("VERDICT_READY");
        submission.setExitValue(0);
        submission.setTime(0.1);
        submission.setMemory(64L);
        submission.setUserId(701L);
        submission.setUserName("USER_SENTINEL");
        submission.setAttemptId(702L);
        submission.setProblemId(703L);
        submission.setProblemName("PROBLEM_NAME_SENTINEL");
        submission.setIdempotencyKey("IDEMPOTENCY_SENTINEL");
        submission.setMessage("MESSAGE_SENTINEL");
        Problem problem = new Problem()
            .setDescription("DESCRIPTION_SENTINEL")
            .setInputStyle("INPUT_STYLE_SENTINEL")
            .setOutputStyle("OUTPUT_STYLE_SENTINEL")
            .setHint("HINT_SENTINEL")
            .setInputSample(List.of("HIDDEN_INPUT_SENTINEL"))
            .setOutputSample(List.of("HIDDEN_OUTPUT_SENTINEL"))
            .setTestData("HIDDEN_TEST_DATA_SENTINEL")
            .setTestAns("HIDDEN_TEST_ANS_SENTINEL");

        ReviewEvidence evidence = new ReviewEvidenceFactory().create(submission, problem);
        String serialized = JSON.toJSONString(evidence);
        JSONObject json = JSON.parseObject(serialized);

        assertEquals(Set.of("schemaVersion", "submissionId", "language", "sourceCode", "verdict", "judgeStage",
            "exitValue", "timeSeconds", "memoryKb", "problemDescription", "inputStyle", "outputStyle", "hint"),
            json.keySet());
        assertEquals("1", json.getString("schemaVersion"));
        for (String forbidden : List.of("userId", "userName", "attemptId", "problemId", "problemName", "idempotencyKey",
            "inputSample", "outputSample", "testData", "testAns", "message", "USER_SENTINEL", "PROBLEM_NAME_SENTINEL",
            "IDEMPOTENCY_SENTINEL", "MESSAGE_SENTINEL", "701", "702", "703", "HIDDEN_INPUT_SENTINEL", "HIDDEN_OUTPUT_SENTINEL",
            "HIDDEN_TEST_DATA_SENTINEL", "HIDDEN_TEST_ANS_SENTINEL")) {
            assertFalse(serialized.contains(forbidden));
        }
    }
}
