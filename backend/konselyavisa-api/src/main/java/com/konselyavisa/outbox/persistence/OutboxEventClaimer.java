package com.konselyavisa.outbox.persistence;

import com.konselyavisa.outbox.domain.OutboxEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxEventClaimer {

    @PersistenceContext
    private EntityManager entityManager;

    public List<OutboxEvent> lockDue(Instant now, int batchSize) {
        return entityManager
                .createNativeQuery(
                        """
                        SELECT *
                        FROM outbox_events
                        WHERE status = 'PENDING'
                          AND next_attempt_at <= :now
                        ORDER BY created_at ASC
                        LIMIT :batch
                        FOR UPDATE SKIP LOCKED
                        """,
                        OutboxEvent.class)
                .setParameter("now", Timestamp.from(now))
                .setParameter("batch", batchSize)
                .getResultList();
    }
}
