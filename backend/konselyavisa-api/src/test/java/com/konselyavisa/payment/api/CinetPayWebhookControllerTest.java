package com.konselyavisa.payment.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CinetPayWebhookControllerTest {

    @Test
    void parsesFormUrlEncodedNotify() {
        Map<String, Object> fields = CinetPayWebhookController.parseForm(
                "cpm_trans_id=pay-1&cpm_amount=85&signature=abc%3D");
        assertThat(fields)
                .containsEntry("cpm_trans_id", "pay-1")
                .containsEntry("cpm_amount", "85")
                .containsEntry("signature", "abc=");
    }
}
