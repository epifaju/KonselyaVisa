package com.konselyavisa.privacy.persistence;

import com.konselyavisa.privacy.domain.PrivacyConsent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

    List<PrivacyConsent> findByCaseFile_IdOrderByAcceptedAtAsc(UUID caseId);

    List<PrivacyConsent> findByKeycloakSubjectOrderByAcceptedAtDesc(String keycloakSubject);
}
