package com.decade.doj.common.domain.dto;

import com.decade.doj.common.domain.po.Submission;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.Map;

@Data
@Accessors(chain = true)
public class JudgingRequest {
    private Submission submission;
    private Map<String, Object> task;
}
