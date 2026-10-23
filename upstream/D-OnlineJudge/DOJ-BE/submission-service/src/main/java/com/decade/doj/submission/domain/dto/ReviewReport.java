package com.decade.doj.submission.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class ReviewReport {

    private String schemaVersion;
    private String promptVersion;
    private String model;
    private String rubricVersion;
    private Long submissionId;
    private String status;
    private String summary;
    private List<String> strengths;
    private List<String> risks;
    private List<String> suggestions;
}
