package com.konselyavisa.appointment.domain;

import com.konselyavisa.tenancy.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "appointment_slots")
public class AppointmentSlot extends TenantAwareEntity {

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private int capacity = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "location_i18n", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> locationI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentSlotStatus status = AppointmentSlotStatus.OPEN;
}
