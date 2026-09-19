package com.konselyavisa.organization.persistence;

import com.konselyavisa.organization.domain.FeatureFlag;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByKeyAndOrganizationId(String key, UUID organizationId);

    Optional<FeatureFlag> findByKeyAndOrganizationIdIsNull(String key);
}
