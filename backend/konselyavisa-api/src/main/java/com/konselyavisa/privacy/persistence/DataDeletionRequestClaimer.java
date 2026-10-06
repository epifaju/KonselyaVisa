package com.konselyavisa.privacy.persistence;

import com.konselyavisa.privacy.domain.DataDeletionRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class DataDeletionRequestClaimer {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<DataDeletionRequest> lockDue(Instant now, int batchSize) {
        return entityManager
                .createNativeQuery(
                        """
                        SELECT *
                        FROM data_deletion_requests
                        WHERE status = 'PENDING'
                          AND scheduled_anonymize_at <= :now
                        ORDER BY scheduled_anonymize_at ASC
                        LIMIT :batch
                        FOR UPDATE SKIP LOCKED
                        """,
                        DataDeletionRequest.class)
                .setParameter("now", Timestamp.from(now))
                .setParameter("batch", batchSize)
                .getResultList();
    }
}
