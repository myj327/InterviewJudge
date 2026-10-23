package com.decade.doj.submission.review;

import com.decade.doj.submission.domain.dto.ReviewEvidence;
import com.decade.doj.submission.domain.dto.ReviewReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MockReviewProviderTest {

    @Test
    void producesDeterministicStructuredMockReport() {
        ReviewEvidence evidence = new ReviewEvidence()
            .setSubmissionId(81L)
            .setLanguage("java")
            .setVerdict("Accepted")
            .setJudgeStage("VERDICT_READY");
        MockReviewProvider provider = new MockReviewProvider();

        ReviewReport first = provider.review(evidence);
        ReviewReport second = provider.review(evidence);

        assertEquals(first, second);
        assertEquals("1", first.getSchemaVersion());
        assertEquals("r1-v1", first.getPromptVersion());
        assertEquals("mock", first.getModel());
        assertEquals("recruitment-v1", first.getRubricVersion());
        assertEquals(81L, first.getSubmissionId());
        assertEquals("SUCCEEDED", first.getStatus());
        assertNotNull(first.getSummary());
        assertFalse(first.getSummary().isBlank());
        assertFalse(first.getStrengths().isEmpty());
        assertFalse(first.getRisks().isEmpty());
        assertFalse(first.getSuggestions().isEmpty());
    }
}
