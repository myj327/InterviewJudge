package com.decade.doj.submission.service;

import com.alibaba.fastjson.JSON;
import com.decade.doj.common.domain.po.Problem;
import com.decade.doj.submission.domain.po.Submission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaStringCredentialTest {
    @Test void ordinaryJavaStringCredentialsNeverLeaveEvidenceOrChangeStoredData() {
        String password = "synthetic-json-private-password", key = "synthetic-json-private-credential";
        String json = "{\"password\":\"" + password + "\",\"api_key\":\"" + key + "\",\"label\":\"public-label\"}";
        String source = source(json);
        Submission row = new Submission();
        row.setCode(source); row.setStatus("Accepted");
        Problem problem = new Problem().setDescription(source).setInputStyle(source).setOutputStyle(source).setHint(source);
        var evidence = new ReviewEvidenceFactory().create(row, problem);
        for (String content : new String[]{evidence.getSourceCode(), evidence.getProblemDescription(),
                evidence.getInputStyle(), evidence.getOutputStyle(), evidence.getHint(), JSON.toJSONString(evidence)}) {
            assertFalse(content.contains(password), "password must not leave Evidence");
            assertFalse(content.contains(key), "api_key must not leave Evidence");
            assertTrue(content.contains("[REDACTED]"));
            assertTrue(content.contains("public-label"));
        }
        assertEquals(source, row.getCode()); assertEquals(source, problem.getDescription());
        assertEquals(source, problem.getInputStyle()); assertEquals(source, problem.getOutputStyle());
        assertEquals(source, problem.getHint()); assertEquals("Accepted", row.getStatus());
        assertEquals("Accepted", evidence.getVerdict());
    }

    @Test void escapedQuotesAndBackslashesCannotTruncateCredentialRedaction() {
        for (String value : new String[]{"synthetic-prefix\\\"synthetic-suffix", "synthetic-prefix\\\\synthetic-suffix", "synthetic-tail\\\\"}) {
            String source = source("{\"password\":\"" + value + "\",\"label\":\"public-label\"}");
            Submission row = new Submission(); row.setCode(source);
            String scrubbed = new ReviewEvidenceFactory().create(row, new Problem()).getSourceCode();
            assertFalse(scrubbed.contains("synthetic"), "the entire encoded value must be removed");
            assertTrue(scrubbed.contains("[REDACTED]"));
            assertTrue(scrubbed.contains("\\\"label\\\":\\\"public-label\\\""));
            assertTrue(scrubbed.endsWith("; System.out.println(5); } }"));
            assertEquals(source, row.getCode());
        }
    }

    @Test void multipleKnownFieldsAndEmptyValuesKeepTheirEncodedBoundaries() {
        String json = "{\"PASSWORD\":\"\",\"api-key\":\"synthetic-a\",\"accessToken\":\"synthetic-b\","
            + "\"refresh_token\":\"synthetic-c\",\"SECRET\":\"synthetic-d\",\"label\":\"public-label\"}";
        String expected = "{\"PASSWORD\":\"[REDACTED]\",\"api-key\":\"[REDACTED]\",\"accessToken\":\"[REDACTED]\","
            + "\"refresh_token\":\"[REDACTED]\",\"SECRET\":\"[REDACTED]\",\"label\":\"public-label\"}";
        Submission row = new Submission(); row.setCode(source(json));
        assertEquals(source(expected), new ReviewEvidenceFactory().create(row, new Problem()).getSourceCode());
        assertEquals(source(json), row.getCode());
    }

    private static String source(String json) {
        return "public class Main { public static void main(String[] args) { String cfg = \""
            + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"; System.out.println(5); } }";
    }
}
