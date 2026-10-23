package com.decade.doj.submission.domain.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ReviewEvidence {

    private String schemaVersion;
    private Long submissionId;
    private String language;
    private String sourceCode;
    private String verdict;
    private String judgeStage;
    private Integer exitValue;
    private Double timeSeconds;
    private Long memoryKb;
    private String problemDescription;
    private String inputStyle;
    private String outputStyle;
    private String hint;
}
