package com.konselyavisa.catalog.persistence;

import com.konselyavisa.catalog.domain.ProcedureCategory;
import com.konselyavisa.catalog.domain.ProcedureDefinition;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcedureDefinitionRepository extends JpaRepository<ProcedureDefinition, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = {"originCountry", "destinationCountry"})
    @Query(
            """
            SELECT p FROM ProcedureDefinition p
            WHERE (:activeOnly = false OR p.active = true)
              AND (:originId IS NULL OR p.originCountry.id = :originId)
              AND (:destinationId IS NULL OR p.destinationCountry.id = :destinationId)
              AND (:category IS NULL OR p.category = :category)
              AND (:publishedOnly = false OR EXISTS (
                    SELECT v FROM ProcedureVersion v
                    WHERE v.procedureDefinition = p AND v.status = com.konselyavisa.catalog.domain.ProcedureVersionStatus.PUBLISHED
              ))
            """)
    Page<ProcedureDefinition> search(
            @Param("originId") UUID originId,
            @Param("destinationId") UUID destinationId,
            @Param("category") ProcedureCategory category,
            @Param("activeOnly") boolean activeOnly,
            @Param("publishedOnly") boolean publishedOnly,
            Pageable pageable);

    @EntityGraph(attributePaths = {"originCountry", "destinationCountry"})
    @Query("SELECT p FROM ProcedureDefinition p WHERE p.id = :id")
    Optional<ProcedureDefinition> findDetailedById(@Param("id") UUID id);
}
