package com.konselyavisa.privacy.domain;

import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "data_deletion_requests")
public class DataDeletionRequest extends TenantAwareEntity {

    @Column(name = "keycloak_subject", nullable = false, length = 100)
    private String keycloakSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DataDeletionStatus status = DataDeletionStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "scheduled_anonymize_at", nullable = false)
    private Instant scheduledAnonymizeAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
