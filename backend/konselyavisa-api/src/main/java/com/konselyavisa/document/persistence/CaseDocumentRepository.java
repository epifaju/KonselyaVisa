package com.konselyavisa.document.persistence;

import com.konselyavisa.document.domain.CaseDocument;
import com.konselyavisa.document.domain.DocumentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseDocumentRepository extends JpaRepository<CaseDocument, UUID> {

    List<CaseDocument> findByCaseFile_IdOrderByCreatedAtAsc(UUID caseId);

    @Query(
            """
            SELECT d FROM CaseDocument d
            JOIN FETCH d.caseFile
            WHERE d.caseFile.id IN :caseIds
            """)
    List<CaseDocument> findByCaseFile_IdIn(@Param("caseIds") Collection<UUID> caseIds);

    Optional<CaseDocument> findByIdAndCaseFile_Id(UUID id, UUID caseId);

    boolean existsByCaseFile_IdAndStatus(UUID caseId, DocumentStatus status);

    @Query(
            """
            SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END
            FROM CaseDocument d
            WHERE d.organizationId = :organizationId
              AND d.sha256 = :sha256
              AND d.caseFile.applicant.id <> :applicantId
            """)
    boolean existsDuplicateHash(
            @Param("organizationId") UUID organizationId,
            @Param("sha256") String sha256,
            @Param("applicantId") UUID applicantId);
}
