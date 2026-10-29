package com.konselyavisa.identity.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.konselyavisa.common.api.PageResponse;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.common.exception.RestExceptionHandler;
import com.konselyavisa.dossier.CaseService;
import com.konselyavisa.dossier.CompanyCaseService;
import com.konselyavisa.dossier.SupervisorSummaryService;
import com.konselyavisa.dossier.api.CaseController;
import com.konselyavisa.dossier.api.CompanyCaseController;
import com.konselyavisa.dossier.api.CompanyCaseSummaryResponse;
import com.konselyavisa.dossier.api.SupervisorSummaryController;
import com.konselyavisa.dossier.api.SupervisorSummaryResponse;
import com.konselyavisa.tenancy.web.TenantRequestFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CaseController.class, CompanyCaseController.class, SupervisorSummaryController.class})
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    KeycloakRealmRoleConverter.class,
    TenantRequestFilter.class,
    RestExceptionHandler.class,
    ApiAuthorizationSliceTest.JwtDecoderConfig.class
})
class ApiAuthorizationSliceTest {

    private static final String CASE_BODY =
            """
            {
              "procedureDefinitionId": "11111111-1111-1111-1111-111111111111",
              "applicant": {"email": "ada@example.test", "displayName": "Ada", "facts": {}},
              "privacyConsent": {"accepted": true, "locale": "fr", "noticeVersion": "1"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private CompanyCaseService companyCaseService;

    @MockitoBean
    private SupervisorSummaryService supervisorSummaryService;

    @Test
    void anonymousCaseAccessIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/cases")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/cases").contentType(MediaType.APPLICATION_JSON).content(CASE_BODY))
                .andExpect(status().isUnauthorized());
        verify(caseService, never()).list(any());
        verify(caseService, never()).create(any());
    }

    @Test
    void publicAndWebhookMatchersAreNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/public/site")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/payments/webhooks/stripe")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @Test
    void citizenCannotOpenCompanyOrSupervisorApis() throws Exception {
        mockMvc.perform(get("/api/v1/company/cases/summary").with(jwt().authorities(role("ROLE_CITIZEN"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/supervisor/summary").with(jwt().authorities(role("ROLE_CITIZEN"))))
                .andExpect(status().isForbidden());
        verify(companyCaseService, never()).summary();
        verify(supervisorSummaryService, never()).summarize();
    }

    @Test
    void agentCannotOpenSupervisorSummary() throws Exception {
        mockMvc.perform(get("/api/v1/supervisor/summary").with(jwt().authorities(role("ROLE_AGENT"))))
                .andExpect(status().isForbidden());
        verify(supervisorSummaryService, never()).summarize();
    }

    @Test
    void companyUserAndSupervisorReachTheirOwnApis() throws Exception {
        when(companyCaseService.summary()).thenReturn(new CompanyCaseSummaryResponse(0, Map.of()));
        when(supervisorSummaryService.summarize()).thenReturn(new SupervisorSummaryResponse(1, 0, 0));
        when(caseService.list(any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/company/cases/summary").with(jwt().authorities(role("ROLE_COMPANY_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/v1/supervisor/summary").with(jwt().authorities(role("ROLE_SUPERVISOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.openCount").value(1));
        mockMvc.perform(get("/api/v1/cases").with(jwt().authorities(role("ROLE_CITIZEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidCasePayloadIsBadRequestAndDoesNotCallTheService() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .with(jwt().authorities(role("ROLE_CITIZEN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"applicant":{"email":"not-an-email"},"privacyConsent":{"accepted":false}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.messageKey").value("error.validation"));
        verify(caseService, never()).create(any());
    }

    @Test
    void businessErrorsKeepTheirHttpStatus() throws Exception {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(caseService.getById(id)).thenThrow(BusinessException.notFound("error.case.not_found"));
        when(caseService.create(any())).thenThrow(BusinessException.conflict("error.case.duplicate"));

        mockMvc.perform(get("/api/v1/cases/{id}", id).with(jwt().authorities(role("ROLE_CITIZEN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.messageKey").value("error.case.not_found"));
        mockMvc.perform(post("/api/v1/cases")
                        .with(jwt().authorities(role("ROLE_CITIZEN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CASE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.messageKey").value("error.case.duplicate"));
    }

    @Test
    void unexpectedFailureIsInternalError() throws Exception {
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(caseService.getById(id)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(get("/api/v1/cases/{id}", id).with(jwt().authorities(role("ROLE_AGENT"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.messageKey").value("error.internal"));
    }

    private static SimpleGrantedAuthority role(String authority) {
        return new SimpleGrantedAuthority(authority);
    }

    static class JwtDecoderConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new org.springframework.security.oauth2.jwt.BadJwtException("slice-test");
            };
        }
    }
}
