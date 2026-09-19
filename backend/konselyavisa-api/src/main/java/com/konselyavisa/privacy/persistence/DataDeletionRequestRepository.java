package com.konselyavisa.privacy.persistence;

import com.konselyavisa.privacy.domain.DataDeletionRequest;
import com.konselyavisa.privacy.domain.DataDeletionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataDeletionRequestRepository extends JpaRepository<DataDeletionRequest, UUID> {

    Optional<DataDeletionRequest> findFirstByKeycloakSubjectAndStatusOrderByRequestedAtDesc(
            String keycloakSubject, DataDeletionStatus status);

    List<DataDeletionRequest> findByKeycloakSubjectOrderByRequestedAtDesc(String keycloakSubject);
}
