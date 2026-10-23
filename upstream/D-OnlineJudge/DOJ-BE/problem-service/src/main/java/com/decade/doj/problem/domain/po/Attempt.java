package com.decade.doj.problem.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("attempt")
public class Attempt {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("assessment_id")
    private Long assessmentId;

    @TableField("candidate_id")
    private Long candidateId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("submitted_at")
    private LocalDateTime submittedAt;

    @TableField("status")
    private String status;
}
