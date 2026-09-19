package com.konselyavisa.dossier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseFileRepository extends JpaRepository<CaseFile, UUID> {

    boolean existsByOrganizationIdAndReference(UUID organizationId, String reference);

    @EntityGraph(attributePaths = {"applicant", "procedureDefinition"})
    Optional<CaseFile> findByOrganizationIdAndReferenceIgnoreCase(UUID organizationId, String reference);

    @EntityGraph(
            attributePaths = {
                "applicant",
                "procedureDefinition",
                "procedureDefinition.originCountry",
                "procedureDefinition.destinationCountry",
                "procedureVersion"
            })
    Optional<CaseFile> findDetailedById(UUID id);

    @EntityGraph(
            attributePaths = {
                "applicant",
                "procedureDefinition",
                "procedureDefinition.originCountry",
                "procedureDefinition.destinationCountry",
                "procedureVersion"
            })
    Page<CaseFile> findAllByApplicant_KeycloakSubject(String keycloakSubject, Pageable pageable);

    @Override
    @EntityGraph(
            attributePaths = {
                "applicant",
                "procedureDefinition",
                "procedureDefinition.originCountry",
                "procedureDefinition.destinationCountry",
                "procedureVersion"
            })
    Page<CaseFile> findAll(Pageable pageable);

    @EntityGraph(
            attributePaths = {
                "applicant",
                "procedureDefinition",
                "procedureDefinition.originCountry",
                "procedureDefinition.destinationCountry",
                "procedureVersion"
            })
    Page<CaseFile> findAllByCreatedBy(String createdBy, Pageable pageable);

    @EntityGraph(
            attributePaths = {
                "applicant",
                "procedureDefinition",
                "procedureDefinition.originCountry",
                "procedureDefinition.destinationCountry",
                "procedureVersion"
            })
    Page<CaseFile> findAllByCreatedByRoleIn(List<String> createdByRoles, Pageable pageable);

    @EntityGraph(
            attributePaths = {
                "applicant",
                "procedureDefinition",
                "procedureDefinition.originCountry",
                "procedureDefinition.destinationCountry",
                "procedureVersion"
            })
    Page<CaseFile> findAllByStatus(CaseStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "procedureVersion")
    List<CaseFile> findByStatusIn(Collection<CaseStatus> statuses);

    @Query("select c.status as status, count(c) as total from CaseFile c group by c.status")
    List<CaseStatusCount> countGroupedByStatus();

    @Query(
            "select c.status as status, count(c) as total from CaseFile c where c.createdBy = :createdBy group by c.status")
    List<CaseStatusCount> countGroupedByCreatedBy(@Param("createdBy") String createdBy);

    @Query(
            "select c.status as status, count(c) as total from CaseFile c where c.createdByRole in (:roles) group by c.status")
    List<CaseStatusCount> countGroupedByCreatedByRoleIn(@Param("roles") List<String> roles);
}
