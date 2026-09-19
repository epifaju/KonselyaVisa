package com.konselyavisa.catalog.persistence;

import com.konselyavisa.catalog.domain.ProcedureVersion;
import com.konselyavisa.catalog.domain.ProcedureVersionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcedureVersionRepository extends JpaRepository<ProcedureVersion, UUID> {

    List<ProcedureVersion> findByProcedureDefinitionIdOrderByVersionNumberDesc(UUID procedureDefinitionId);

    Optional<ProcedureVersion> findByIdAndProcedureDefinitionId(UUID id, UUID procedureDefinitionId);

    Optional<ProcedureVersion> findByProcedureDefinitionIdAndStatus(
            UUID procedureDefinitionId, ProcedureVersionStatus status);

    Optional<ProcedureVersion> findTopByProcedureDefinitionIdOrderByVersionNumberDesc(UUID procedureDefinitionId);
}
