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
@TableName("assessment")
public class Assessment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("owner_id")
    private Long ownerId;

    @TableField("problem_id")
    private Long problemId;

    @TableField("title")
    private String title;

    @TableField("starts_at")
    private LocalDateTime startsAt;

    @TableField("ends_at")
    private LocalDateTime endsAt;

    @TableField("status")
    private String status;
}
