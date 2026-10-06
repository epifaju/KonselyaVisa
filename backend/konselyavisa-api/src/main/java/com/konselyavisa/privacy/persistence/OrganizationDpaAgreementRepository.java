package com.konselyavisa.privacy.persistence;

import com.konselyavisa.privacy.domain.OrganizationDpaAgreement;
import com.konselyavisa.privacy.domain.OrganizationDpaStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationDpaAgreementRepository extends JpaRepository<OrganizationDpaAgreement, UUID> {

    Optional<OrganizationDpaAgreement> findByOrganizationIdAndStatus(
            UUID organizationId, OrganizationDpaStatus status);
}
