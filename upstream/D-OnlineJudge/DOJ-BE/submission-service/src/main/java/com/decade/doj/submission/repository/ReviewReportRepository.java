package com.decade.doj.submission.repository;

import com.alibaba.fastjson.JSON;
import com.decade.doj.submission.domain.dto.ReviewReport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewReportRepository {

    private static final String PROMPT_VERSION = "r1-v1";
    private static final String INSERT_SQL = "INSERT IGNORE INTO review_report "
        + "(submission_id, prompt_version, status, report_json) VALUES (?, ?, ?, ?)";
    private static final String SELECT_SQL = "SELECT report_json FROM review_report "
        + "WHERE submission_id = ? AND prompt_version = ?";

    private final JdbcTemplate jdbcTemplate;

    public boolean save(ReviewReport report) {
        return jdbcTemplate.update(INSERT_SQL, report.getSubmissionId(), report.getPromptVersion(),
            report.getStatus(), JSON.toJSONString(report)) == 1;
    }

    public ReviewReport findBySubmissionId(Long submissionId) {
        List<String> reports = jdbcTemplate.queryForList(SELECT_SQL, String.class, submissionId, PROMPT_VERSION);
        return reports.isEmpty() ? null : JSON.parseObject(reports.get(0), ReviewReport.class);
    }
}
