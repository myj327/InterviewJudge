package com.decade.doj.submission.repository;

import com.alibaba.fastjson.JSON;
import com.decade.doj.submission.domain.dto.ReviewReport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewReportRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ReviewReportRepository repository = new ReviewReportRepository(jdbcTemplate);

    @Test
    void insertsWithIgnoreWithoutOverwriteAndReadsFixedVersion() {
        ReviewReport report = report();
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1, 0);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq(31L), eq("r1-v1")))
            .thenReturn(List.of(JSON.toJSONString(report)));

        assertTrue(repository.save(report));
        assertFalse(repository.save(report));
        assertEquals(report, repository.findBySubmissionId(31L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).update(sql.capture(), any(), any(), any(), any());
        assertTrue(sql.getValue().contains("INSERT IGNORE"));
        assertTrue(sql.getValue().contains("?"));
        assertFalse(sql.getValue().contains("UPDATE"));
        assertFalse(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        verify(jdbcTemplate).queryForList(anyString(), eq(String.class), eq(31L), eq("r1-v1"));
    }

    private ReviewReport report() {
        return new ReviewReport()
            .setSchemaVersion("1")
            .setPromptVersion("r1-v1")
            .setModel("mock")
            .setRubricVersion("recruitment-v1")
            .setSubmissionId(31L)
            .setStatus("SUCCEEDED")
            .setSummary("summary")
            .setStrengths(List.of("strength"))
            .setRisks(List.of("risk"))
            .setSuggestions(List.of("suggestion"));
    }
}
