package com.decade.doj.submission.service;
import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.submission.domain.po.Submission;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReviewPrivacyTest {
    @Test void quotedJsonCredentialsAreRedactedInSourceAndPublicContextWithoutChangingStoredData() {
        String password="synthetic-private-password",key="synthetic-private-credential";
        String json="{\"password\": \""+password+"\", \"api_key\": \""+key+"\"}";
        Submission row=new Submission();row.setCode("class Main { String cfg = \"\"\"\n"+json+"\n\"\"\"; }");row.setStatus("Accepted");
        Problem problem=new Problem().setDescription(json).setInputStyle(json).setOutputStyle(json).setHint(json);
        var evidence=new ReviewEvidenceFactory().create(row,problem);
        for(String content:new String[]{evidence.getSourceCode(),evidence.getProblemDescription(),evidence.getInputStyle(),evidence.getOutputStyle(),evidence.getHint()}) {
            assertFalse(content.contains(password));assertFalse(content.contains(key));assertTrue(content.contains("[REDACTED]"));
        }
        assertTrue(row.getCode().contains(password));assertEquals(json,problem.getDescription());assertEquals("Accepted",evidence.getVerdict());
        String escaped="{\"PASSWORD\" : \"synthetic\\\"suffix\", 'api_key' : 'synthetic-key'}";
        String scrubbed=ReviewContentSanitizer.scrub(escaped);
        assertFalse(scrubbed.contains("synthetic"));assertTrue(scrubbed.contains("[REDACTED]"));
    }
    @Test void knownSecretsAndContactInformationNeverLeaveEvidence() {
        Submission row=new Submission();String secret="sk-"+"synthetic".repeat(4);
        row.setCode("String apiKey=\""+secret+"\"; // synthetic@example.invalid +86 13800138000");
        Problem problem=new Problem().setDescription("password='synthetic-password'").setHint("synthetic@example.invalid")
                .setInputStyle("13800138000").setOutputStyle("110101200001010000");
        String evidence=com.alibaba.fastjson.JSON.toJSONString(new ReviewEvidenceFactory().create(row,problem));
        for(String value:new String[]{secret,"synthetic@example.invalid","13800138000","synthetic-password","110101200001010000"})
            assertFalse(evidence.contains(value),value);
        assertTrue(row.getCode().contains(secret));assertTrue(problem.getHint().contains("@"));
    }
    @Test void privateKeyBlocksAreRemovedButCodeAndVerdictRemain() {
        Submission row=new Submission();row.setCode("class Main {}\n-----BEGIN PRIVATE KEY-----\nSYNTHETIC_ONLY\n-----END PRIVATE KEY-----");row.setStatus("Accepted");
        var evidence=new ReviewEvidenceFactory().create(row,new Problem());
        assertFalse(evidence.getSourceCode().contains("SYNTHETIC_ONLY"));assertTrue(evidence.getSourceCode().contains("class Main {}"));assertEquals("Accepted",evidence.getVerdict());
    }
    @Test void hiddenMaterialAndIdentifiersStayOutsideWhitelist() {
        Submission row=new Submission();row.setCode("class Main {}");row.setUserId(7L);
        Problem problem=new Problem().setDescription("public").setTestData("synthetic hidden input").setTestAns("synthetic hidden answer");
        String evidence=com.alibaba.fastjson.JSON.toJSONString(new ReviewEvidenceFactory().create(row,problem));
        assertFalse(evidence.contains("hidden"));assertFalse(evidence.contains("userId"));assertTrue(evidence.contains("class Main {}"));
    }
}
