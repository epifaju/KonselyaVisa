package com.konselyavisa.applicant;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantRepository extends JpaRepository<Applicant, UUID> {

    Optional<Applicant> findByOrganizationIdAndKeycloakSubject(UUID organizationId, String keycloakSubject);
}
