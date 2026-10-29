package com.konselyavisa.identity.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.konselyavisa.appointment.AppointmentService;
import com.konselyavisa.appointment.api.AppointmentController;
import com.konselyavisa.appointment.api.AppointmentHoldController;
import com.konselyavisa.appointment.api.AppointmentSlotController;
import com.konselyavisa.common.exception.RestExceptionHandler;
import com.konselyavisa.document.DocumentExtractionService;
import com.konselyavisa.document.DocumentReviewService;
import com.konselyavisa.document.DocumentService;
import com.konselyavisa.document.api.CaseDocumentController;
import com.konselyavisa.document.api.DocumentExtractionController;
import com.konselyavisa.document.api.DocumentReviewController;
import com.konselyavisa.payment.PaymentService;
import com.konselyavisa.payment.api.CaseOrderController;
import com.konselyavisa.payment.api.PaymentController;
import com.konselyavisa.tenancy.web.TenantRequestFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {
            CaseDocumentController.class,
            DocumentReviewController.class,
            DocumentExtractionController.class,
            CaseOrderController.class,
            PaymentController.class,
            AppointmentSlotController.class,
            AppointmentController.class,
            AppointmentHoldController.class
        })
@AutoConfigureMockMvc
@Import({
    SecurityConfig.class,
    KeycloakRealmRoleConverter.class,
    TenantRequestFilter.class,
    RestExceptionHandler.class,
    ApiAuthorizationSliceTest.JwtDecoderConfig.class
})
class ResourceAuthorizationSliceTest {

    private static final UUID CASE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RESOURCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private DocumentReviewService documentReviewService;

    @MockitoBean
    private DocumentExtractionService documentExtractionService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private AppointmentService appointmentService;

    @Test
    void anonymousAccessIsUnauthorizedAndWebhooksStayPublic() throws Exception {
        mockMvc.perform(get("/api/v1/cases/{caseId}/documents", CASE_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/cases/{caseId}/orders", CASE_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/appointment-slots")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/confirm-manual", RESOURCE_ID))
                .andExpect(status().isUnauthorized());

        verify(documentService, never()).list(any());
        verify(paymentService, never()).createCheckout(any());
        verify(appointmentService, never()).listSlots(any(), any());
        verify(paymentService, never()).confirmManual(any());

        mockMvc.perform(post("/api/v1/payments/webhooks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/webhooks/STRIPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verify(paymentService).handleWebhook(eq("mock"), anyMap());
        verify(paymentService, never()).handleWebhook(eq("STRIPE"), anyMap());
    }

    @Test
    void citizenCannotReviewDocumentsCreateSlotsOrConfirmPayment() throws Exception {
        var citizen = jwt().authorities(new SimpleGrantedAuthority("ROLE_CITIZEN"));
        mockMvc.perform(post("/api/v1/cases/{caseId}/documents/{documentId}/accept", CASE_ID, RESOURCE_ID)
                        .with(citizen))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/cases/{caseId}/documents/{documentId}/extraction", CASE_ID, RESOURCE_ID)
                        .with(citizen)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"extractedFields\":{\"givenName\":\"Ada\"}}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/appointment-slots/{slotId}/cancel", RESOURCE_ID).with(citizen))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/{paymentId}/confirm-manual", RESOURCE_ID).with(citizen))
                .andExpect(status().isForbidden());

        verify(documentReviewService, never()).accept(any(), any());
        verify(documentExtractionService, never()).saveManual(any(), any(), any());
        verify(appointmentService, never()).cancelSlot(any());
        verify(paymentService, never()).confirmManual(any());
    }

    @Test
    void citizenCanReadOwnResourcesAndAgentCanAcceptADocument() throws Exception {
        when(documentService.list(CASE_ID)).thenReturn(List.of());
        when(paymentService.listByCase(CASE_ID)).thenReturn(List.of());
        when(appointmentService.listSlots(null, null)).thenReturn(List.of());
        when(appointmentService.listByCase(CASE_ID)).thenReturn(List.of());
        when(documentReviewService.accept(CASE_ID, RESOURCE_ID)).thenReturn(null);

        var citizen = jwt().authorities(new SimpleGrantedAuthority("ROLE_CITIZEN"));
        mockMvc.perform(get("/api/v1/cases/{caseId}/documents", CASE_ID).with(citizen))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/{caseId}/orders", CASE_ID).with(citizen)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/appointment-slots").with(citizen)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/{caseId}/appointments", CASE_ID).with(citizen))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/cases/{caseId}/documents/{documentId}/accept", CASE_ID, RESOURCE_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_AGENT"))))
                .andExpect(status().isOk());
        verify(documentReviewService).accept(CASE_ID, RESOURCE_ID);
    }
}
