package com.konselyavisa.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.appointment.api.AppointmentResponse;
import com.konselyavisa.appointment.api.AppointmentSlotResponse;
import com.konselyavisa.appointment.api.BookAppointmentRequest;
import com.konselyavisa.appointment.api.CreateSlotRequest;
import com.konselyavisa.appointment.domain.AppointmentStatus;
import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CaseResponse;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.outbox.domain.OutboxEvent;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AppointmentBookingIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("konselyavisa")
            .withUsername("konselyavisa")
            .withPassword("konselyavisa");

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
    }

    @Autowired
    private CaseService caseService;

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void booksSlotRespectsCapacityAndWritesOutbox() {
        TenantContext.setOrganizationId(DemoOrganization.ID);
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        AppointmentSlotResponse slot = appointmentService.createSlot(new CreateSlotRequest(
                start,
                start.plus(30, ChronoUnit.MINUTES),
                1,
                Map.of("fr", "Consulat", "pt", "Consulado", "en", "Consulate")));
        assertThat(slot.remainingCapacity()).isEqualTo(1);

        CaseResponse first = createCase("rdv1@example.com", "Awa");
        AppointmentResponse booked =
                appointmentService.book(first.id(), new BookAppointmentRequest(slot.id()));
        assertThat(booked.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(booked.slot().remainingCapacity()).isEqualTo(0);

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        List<OutboxEvent> bookedEvents = template.execute(status ->
                outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        first.id(), OutboxEventTypes.APPOINTMENT_BOOKED));
        assertThat(bookedEvents).hasSize(1);
        assertThat(bookedEvents.getFirst().getPayload()).containsEntry("slotId", slot.id().toString());

        assertThatThrownBy(() -> appointmentService.book(first.id(), new BookAppointmentRequest(slot.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.already_booked");

        CaseResponse second = createCase("rdv2@example.com", "Binta");
        assertThatThrownBy(() -> appointmentService.book(second.id(), new BookAppointmentRequest(slot.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.appointment.slot_full");

        appointmentService.cancel(booked.id());
        List<OutboxEvent> cancelledEvents = template.execute(status ->
                outboxEventRepository.findByAggregateIdAndEventTypeOrderByCreatedAtAsc(
                        first.id(), OutboxEventTypes.APPOINTMENT_CANCELLED));
        assertThat(cancelledEvents).hasSize(1);

        AppointmentResponse rebooked =
                appointmentService.book(second.id(), new BookAppointmentRequest(slot.id()));
        assertThat(rebooked.status()).isEqualTo(AppointmentStatus.BOOKED);
    }

    private CaseResponse createCase(String email, String name) {
        return caseService.create(new CreateCaseRequest(
                CatalogIds.VISA_TOURISM_FR_GW,
                new ApplicantRequest(email, name, Map.of("nationality", "PT", "passportValidityMonths", 12))));
    }
}
