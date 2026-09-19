package com.konselyavisa.document.persistence;

import com.konselyavisa.document.domain.DocumentAccessLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, UUID> {}
