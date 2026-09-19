package com.konselyavisa.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.appointment.api.AppointmentHoldResponse;
import com.konselyavisa.appointment.api.AppointmentSlotResponse;
import com.konselyavisa.appointment.api.BookAppointmentRequest;
import com.konselyavisa.appointment.api.CreateSlotRequest;
import com.konselyavisa.appointment.api.HoldAppointmentRequest;
import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AppointmentSlotHoldIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("konselyavisa")
            .withUsername("konselyavisa")
            .withPassword("konselyavisa");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "konselyavisa_app");
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:1/realms/konselyavisa/protocol/openid-connect/certs");
        registry.add("management.otlp.tracing.export.enabled", () -> "false");
        registry.add("management.tracing.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("konselyavisa.redis.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("konselyavisa.redis.write-per-minute", () -> "10000");
        registry.add("konselyavisa.redis.read-per-minute", () -> "10000");
    }

    @Autowired
    private CaseService caseService;

    @Autowired
    private AppointmentService appointmentService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void holdReservesLastSeatUntilBooking() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        Instant start = Instant.now().plus(4, ChronoUnit.DAYS);
        AppointmentSlotResponse slot = appointmentService.createSlot(new CreateSlotRequest(
                start,
                start.plus(30, ChronoUnit.MINUTES),
                1,
                Map.of("fr", "Consulat", "pt", "Consulado", "en", "Consulate")));

        CaseResponse first = createCase("hold1@example.com", "Awa");
        AppointmentHoldResponse hold =
                appointmentService.hold(first.id(), new HoldAppointmentRequest(slot.id()));
        assertThat(hold.slotId()).isEqualTo(slot.id());
        assertThat(hold.expiresAt()).isAfter(Instant.now());

        AppointmentSlotResponse listed = appointmentService.listSlots(start.minusSeconds(1), start.plus(1, ChronoUnit.DAYS)).stream()
                .filter(item -> item.id().equals(slot.id()))
                .findFirst()
                .orElseThrow();
        assertThat(listed.remainingCapacity()).isZero();

        CaseResponse second = createCase("hold2@example.com", "Binta");
        assertThatThrownBy(() -> appointmentService.hold(second.id(), new HoldAppointmentRequest(slot.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.hold_unavailable");

        appointmentService.book(first.id(), new BookAppointmentRequest(slot.id()));
        assertThatThrownBy(() -> appointmentService.book(second.id(), new BookAppointmentRequest(slot.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.slot_full");
    }

    private CaseResponse createCase(String email, String name) {
        return caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(email, name, Map.of("nationality", "PT", "passportValidityMonths", 12))));
    }
}
