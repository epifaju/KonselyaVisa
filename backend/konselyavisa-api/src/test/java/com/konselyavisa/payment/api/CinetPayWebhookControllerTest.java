package com.konselyavisa.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.PaymentService;
import com.konselyavisa.payment.provider.PaymentProviderCodes;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CinetPayWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    private CinetPayWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new CinetPayWebhookController(paymentService, new ObjectMapper());
    }

    @Test
    void parsesFormUrlEncodedNotify() {
        Map<String, Object> fields = CinetPayWebhookController.parseForm(
                "cpm_trans_id=pay-1&cpm_amount=85&signature=abc%3D");
        assertThat(fields)
                .containsEntry("cpm_trans_id", "pay-1")
                .containsEntry("cpm_amount", "85")
                .containsEntry("signature", "abc=");
        assertThat(CinetPayWebhookController.parseForm(null)).isEmpty();
        assertThat(CinetPayWebhookController.parseForm("lonely")).containsEntry("lonely", "");
    }

    @Test
    void forwardsJsonAndFormNotifications() throws Exception {
        controller.cinetPayWebhook(request("application/json", "{\"signature\":\"sig\",\"cpm_trans_id\":\"pay-1\"}"));
        controller.cinetPayWebhook(request("Application/X-WWW-Form-Urlencoded", "cpm_trans_id=pay-2"));
        controller.cinetPayWebhook(request("application/json", ""));

        ArgumentCaptor<WebhookPayload> payload = ArgumentCaptor.forClass(WebhookPayload.class);
        verify(paymentService, org.mockito.Mockito.times(3))
                .handleWebhook(eq(PaymentProviderCodes.CINETPAY), payload.capture());
        assertThat(payload.getAllValues().get(0).signature()).isEqualTo("sig");
        assertThat(payload.getAllValues().get(1).json()).containsEntry("cpm_trans_id", "pay-2");
        assertThat(payload.getAllValues().get(1).signature()).isNull();
        assertThat(payload.getAllValues().get(2).json()).isEmpty();
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> controller.cinetPayWebhook(request("application/json", "{bad")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_invalid");
    }

    private static MockHttpServletRequest request(String contentType, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
