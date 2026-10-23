package com.decade.doj.submission.repository;

import com.decade.doj.common.mq.DeferredDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReviewJobs {
    private final JdbcTemplate jdbc;
    public record Lease(long submissionId, String token, int attempt) { }

    public Lease claim(Long id) {
        jdbc.update("INSERT IGNORE INTO review_job(submission_id,state) VALUES (?,'PENDING')", id);
        String token=UUID.randomUUID().toString();
        int claimed=jdbc.update("UPDATE review_job SET state='RUNNING',lease_token=?,attempts=attempts+1,"
                + "available_at=TIMESTAMPADD(SECOND,90,CURRENT_TIMESTAMP(6)) WHERE submission_id=? "
                + "AND state IN ('PENDING','RUNNING') AND available_at<=CURRENT_TIMESTAMP(6) AND attempts<3", token,id);
        if (claimed==1) return new Lease(id,token,jdbc.queryForObject(
                "SELECT attempts FROM review_job WHERE submission_id=? AND lease_token=?",Integer.class,id,token));
        int exhausted=jdbc.update("UPDATE review_job SET state='FAILED',lease_token=NULL WHERE submission_id=? AND attempts>=3 "
                + "AND state IN ('PENDING','RUNNING') AND available_at<=CURRENT_TIMESTAMP(6)",id);
        if (exhausted==1) throw new IllegalStateException("Review attempts exhausted");
        String state=status(id);
        if ("PENDING".equals(state) || "RUNNING".equals(state)) throw new DeferredDeliveryException("Review lease is not available yet");
        return null;
    }
    public boolean succeeded(Lease lease) {
        return jdbc.update("UPDATE review_job SET state='SUCCEEDED',lease_token=NULL WHERE submission_id=? "
                + "AND state='RUNNING' AND lease_token=?",lease.submissionId(),lease.token())==1;
    }
    public void failed(Lease lease, boolean transientFailure) {
        jdbc.update("UPDATE review_job SET state=?,lease_token=NULL,available_at=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(6)) "
                + "WHERE submission_id=? AND state='RUNNING' AND lease_token=?",
                transientFailure && lease.attempt()<3 ? "PENDING":"FAILED",1 << (lease.attempt()-1),lease.submissionId(),lease.token());
    }
    public String status(Long id) {
        var rows=jdbc.queryForList("SELECT state FROM review_job WHERE submission_id=?",String.class,id);
        return rows.isEmpty()?null:rows.get(0);
    }
}
