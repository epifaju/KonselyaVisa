package com.konselyavisa.document.persistence;

import com.konselyavisa.document.domain.DocumentUploadRefusal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentUploadRefusalRepository extends JpaRepository<DocumentUploadRefusal, UUID> {

    List<DocumentUploadRefusal> findByCaseFile_IdOrderByCreatedAtDesc(UUID caseId);
}
