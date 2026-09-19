package com.konselyavisa.applicant;

import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "applicants")
public class Applicant extends TenantAwareEntity {

    @Column(name = "keycloak_subject", length = 100)
    private String keycloakSubject;

    @Column(length = 255)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> facts = new HashMap<>();
}
