package com.konselyavisa.payment.persistence;

import com.konselyavisa.payment.domain.CaseOrder;
import com.konselyavisa.payment.domain.OrderStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseOrderRepository extends JpaRepository<CaseOrder, UUID> {

    boolean existsByOrganizationIdAndReference(UUID organizationId, String reference);

    boolean existsByCaseFile_IdAndStatusIn(UUID caseId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"caseFile", "caseFile.procedureVersion"})
    List<CaseOrder> findByCaseFile_IdOrderByCreatedAtDesc(UUID caseId);

    @EntityGraph(attributePaths = {"caseFile"})
    List<CaseOrder> findByCaseFile_IdIn(Collection<UUID> caseIds);
}
