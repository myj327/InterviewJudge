package com.decade.doj.submission.outbox;

import com.alibaba.fastjson.JSON;
import com.decade.doj.submission.service.ProcessingFailures;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class Outbox {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final ProcessingFailures failures;
    public record Event(long id, String route, String payload, int attempt) { }

    public void add(String key, String route, Object payload) {
        jdbc.update("INSERT INTO event_outbox(event_key,route,payload) VALUES (?,?,?) "
                + "ON DUPLICATE KEY UPDATE event_key=event_key", key, route, JSON.toJSONString(payload));
    }

    public Event claim() {
        return new TransactionTemplate(transactions).execute(status -> {
            List<Event> rows = jdbc.query("SELECT id,route,payload,attempts FROM event_outbox "
                    + "WHERE state IN ('PENDING','IN_FLIGHT') AND available_at<=CURRENT_TIMESTAMP(6) "
                    + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
                    (row, index) -> new Event(row.getLong(1), row.getString(2), row.getString(3), row.getInt(4) + 1));
            if (rows.isEmpty()) return null;
            Event event = rows.get(0);
            if (event.attempt() > 3) {
                jdbc.update("UPDATE event_outbox SET state='DEAD' WHERE id=?", event.id());
                failures.record(event.route(),event.payload());
                return null;
            }
            jdbc.update("UPDATE event_outbox SET state='IN_FLIGHT',attempts=?,"
                    + "available_at=TIMESTAMPADD(SECOND,30,CURRENT_TIMESTAMP(6)) WHERE id=?", event.attempt(), event.id());
            return event;
        });
    }

    public void sent(Event event) {
        jdbc.update("UPDATE event_outbox SET state='SENT',payload='{}' WHERE id=? AND state='IN_FLIGHT' AND attempts=?",
                event.id(), event.attempt());
    }

    public void failed(Event event, boolean transientFailure) {
        String state=!transientFailure || event.attempt()>=3 ? "DEAD":"PENDING";
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            int changed=jdbc.update("UPDATE event_outbox SET state=?,available_at=TIMESTAMPADD(SECOND,5,CURRENT_TIMESTAMP(6)) "
                    + "WHERE id=? AND state='IN_FLIGHT' AND attempts=?",state,event.id(),event.attempt());
            if (changed==1 && "DEAD".equals(state)) failures.record(event.route(),event.payload());
        });
    }
}
