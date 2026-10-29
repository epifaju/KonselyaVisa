package com.konselyavisa.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.PaymentService;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class PayDunyaWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    private PayDunyaWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new PayDunyaWebhookController(paymentService, new ObjectMapper());
    }

    @Test
    void acceptsJsonBody() throws Exception {
        MockHttpServletRequest request = request("application/json", "{\"status\":\"completed\"}");

        var response = controller.payDunyaWebhook(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(paymentService).handleWebhook(eq(PaymentProviderCodes.PAYDUNYA), payload.capture());
        assertThat(payload.getValue().json()).containsEntry("status", "completed");
        assertThat(payload.getValue().rawBody()).contains("completed");
    }

    @Test
    void acceptsFormBodyAndBlankJson() throws Exception {
        MockHttpServletRequest form = request(
                "application/x-www-form-urlencoded; charset=UTF-8", "data%5Bstatus%5D=completed&token");
        controller.payDunyaWebhook(form);
        ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(paymentService).handleWebhook(eq(PaymentProviderCodes.PAYDUNYA), payload.capture());
        assertThat(payload.getValue().json()).containsEntry("data[status]", "completed").containsEntry("token", "");

        controller.payDunyaWebhook(request("application/json", "  "));
        verify(paymentService, org.mockito.Mockito.times(2))
                .handleWebhook(eq(PaymentProviderCodes.PAYDUNYA), any(WebhookPayload.class));
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> controller.payDunyaWebhook(request("application/json", "{not-json")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_invalid");
    }

    @Test
    void parseFormHandlesEmptyAndUndelimitedPairs() {
        assertThat(PayDunyaWebhookController.parseForm(null)).isEmpty();
        assertThat(PayDunyaWebhookController.parseForm("   ")).isEmpty();
        assertThat(PayDunyaWebhookController.parseForm("lonely")).containsEntry("lonely", "");
    }

    private static MockHttpServletRequest request(String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
