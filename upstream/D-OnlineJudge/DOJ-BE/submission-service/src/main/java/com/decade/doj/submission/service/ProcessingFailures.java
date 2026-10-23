package com.decade.doj.submission.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingFailures {
    private final JdbcTemplate jdbc;

    @Transactional
    public void record(String route, String payload) {
        JSONObject body;
        try { body=JSON.parseObject(payload); }
        catch (RuntimeException malformed) { return; } // Unidentifiable poison stays in the original DEAD/DLQ record.
        if (body==null || !(body.get("submissionId") instanceof Number number) || number.longValue()<=0) return;
        long id=number.longValue();
        if ("judging.task".equals(route) || "judging.result".equals(route)) {
            jdbc.update("UPDATE submission SET status='System Error',judge_stage='INFRA_FAILED',exit_value=-1,"
                    + "message='判题任务处理失败，等待恢复' WHERE id=? AND judge_stage IN ('PENDING','QUEUED') "
                    + "AND status IN ('PENDING','QUEUED')",id);
        } else if ("review.requested".equals(route)) {
            jdbc.update("INSERT IGNORE INTO review_job(submission_id,state) VALUES (?,'FAILED')",id);
            jdbc.update("UPDATE review_job SET state='FAILED',lease_token=NULL WHERE submission_id=? "
                    + "AND (state='PENDING' OR (state='RUNNING' AND available_at<=CURRENT_TIMESTAMP(6)))",id);
            Long active=jdbc.queryForObject("SELECT COUNT(*) FROM review_job WHERE submission_id=? "
                    + "AND state='RUNNING' AND available_at>CURRENT_TIMESTAMP(6)",Long.class,id);
            if(active!=null && active>0) throw new com.decade.doj.common.mq.DeferredDeliveryException("Review lease still active");
        }
    }
}
