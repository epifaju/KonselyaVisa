package com.konselyavisa.guest;

import static org.assertj.core.api.Assertions.assertThat;

import com.konselyavisa.catalog.CatalogIds;
import com.konselyavisa.common.api.ApiResponse;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.api.ApplicantRequest;
import com.konselyavisa.dossier.api.CreateCaseRequest;
import com.konselyavisa.dossier.api.PrivacyConsentAcceptance;
import com.konselyavisa.organization.DemoOrganization;
import com.konselyavisa.outbox.domain.OutboxEventTypes;
import com.konselyavisa.outbox.persistence.OutboxEventRepository;
import com.konselyavisa.privacy.domain.PrivacyConsentPurpose;
import com.konselyavisa.privacy.persistence.PrivacyConsentRepository;
import com.konselyavisa.tenancy.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class GuestAccountRegistrationIT {

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
        registry.add("konselyavisa.keycloak.admin.enabled", () -> "false");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CaseService caseService;

    @Autowired
    private PrivacyConsentRepository privacyConsentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void registersAfterEligibilityWithoutConsumingTicket() {
        UUID ticketId = eligibleTicket();
        GuestAccountRegistrationRequest request = new GuestAccountRegistrationRequest(
                ticketId,
                "new.citizen@example.com",
                "CitizenDev!23",
                "Awa Citizen",
                PrivacyConsentAcceptance.currentAccepted("fr"));
        ResponseEntity<ApiResponse<GuestAccountRegistrationResponse>> created = restTemplate.exchange(
                "/api/v1/public/account-registrations",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().data().created()).isTrue();

        TenantContext.setOrganizationId(DemoOrganization.ID);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        boolean accountConsent = Boolean.TRUE.equals(template.execute(status -> privacyConsentRepository.findAll().stream()
                .anyMatch(consent -> consent.getPurpose() == PrivacyConsentPurpose.ACCOUNT_CREATION
                        && "fr".equals(consent.getLocale())
                        && consent.getTextAccepted() != null
                        && !consent.getTextAccepted().isBlank())));
        assertThat(accountConsent).isTrue();
        boolean registeredEvent = Boolean.TRUE.equals(template.execute(status -> outboxEventRepository.findAll().stream()
                .anyMatch(event -> OutboxEventTypes.ACCOUNT_REGISTERED.equals(event.getEventType())
                        && !String.valueOf(event.getPayload()).contains("new.citizen@"))));
        assertThat(registeredEvent).isTrue();

        ResponseEntity<ApiResponse<GuestAccountRegistrationResponse>> duplicate = restTemplate.exchange(
                "/api/v1/public/account-registrations",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {});
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        TenantContext.setOrganizationId(DemoOrganization.ID);
        authenticate("guest-after-register", List.of("ROLE_CITIZEN"));
        assertThat(caseService
                        .create(new CreateCaseRequest(
                                CatalogIds.VISA_TOURISM_FR_GW,
                                new ApplicantRequest("new.citizen@example.com", "Awa Citizen", Map.of()),
                                PrivacyConsentAcceptance.currentAccepted("fr"),
                                ticketId))
                        .eligibilityPassed())
                .isTrue();
    }

    @Test
    void rejectsUnknownTicketAndWeakPassword() {
        GuestAccountRegistrationRequest missingTicket = new GuestAccountRegistrationRequest(
                UUID.randomUUID(),
                "other@example.com",
                "CitizenDev!23",
                null,
                PrivacyConsentAcceptance.currentAccepted("fr"));
        ResponseEntity<ApiResponse<GuestAccountRegistrationResponse>> unknown = restTemplate.exchange(
                "/api/v1/public/account-registrations",
                HttpMethod.POST,
                new HttpEntity<>(missingTicket),
                new ParameterizedTypeReference<>() {});
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        UUID ticketId = eligibleTicket();
        GuestAccountRegistrationRequest weak = new GuestAccountRegistrationRequest(
                ticketId,
                "weak@example.com",
                "short",
                null,
                PrivacyConsentAcceptance.currentAccepted("fr"));
        ResponseEntity<ApiResponse<GuestAccountRegistrationResponse>> rejected = restTemplate.exchange(
                "/api/v1/public/account-registrations",
                HttpMethod.POST,
                new HttpEntity<>(weak),
                new ParameterizedTypeReference<>() {});
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().messageKey()).isEqualTo("error.account.password_weak");
    }

    private UUID eligibleTicket() {
        GuestEligibilityEvaluateRequest eligibleRequest = new GuestEligibilityEvaluateRequest(
                DemoOrganization.ID,
                CatalogIds.VISA_TOURISM_FR_GW,
                Map.of("nationality", "PT", "passportValidityMonths", 12));
        ResponseEntity<ApiResponse<GuestEligibilityEvaluateResponse>> eligible = restTemplate.exchange(
                "/api/v1/public/eligibility-tickets",
                HttpMethod.POST,
                new HttpEntity<>(eligibleRequest),
                new ParameterizedTypeReference<>() {});
        assertThat(eligible.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID ticketId = eligible.getBody().data().ticketId();
        assertThat(ticketId).isNotNull();
        return ticketId;
    }

    private static void authenticate(String subject, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("organization_id", DemoOrganization.ID.toString())
                .claim("preferred_username", subject)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(
                        jwt, roles.stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
