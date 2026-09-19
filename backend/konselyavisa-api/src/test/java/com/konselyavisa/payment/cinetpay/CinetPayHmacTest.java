package com.konselyavisa.payment.cinetpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.WebhookPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class CinetPayHmacTest {

    @Test
    void signsAndVerifiesNotifyPayload() {
        Map<String, Object> fields = notifyFields("pay-1");
        String signature = CinetPayHmac.sign(fields, FakeCinetPayGateway.SECRET);
        assertThat(CinetPayHmac.matches(fields, signature, FakeCinetPayGateway.SECRET)).isTrue();
        assertThat(CinetPayHmac.matches(fields, "deadbeef", FakeCinetPayGateway.SECRET)).isFalse();
    }

    @Test
    void fakeGatewayRejectsBadSignature() {
        FakeCinetPayGateway gateway = new FakeCinetPayGateway();
        Map<String, Object> fields = notifyFields("pay-1");
        fields.put("signature", "nope");
        assertThatThrownBy(() -> gateway.parseWebhook(new WebhookPayload(fields, "nope", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_signature");
    }

    @Test
    void fakeGatewayCompletesAcceptedNotify() {
        FakeCinetPayGateway gateway = new FakeCinetPayGateway();
        Map<String, Object> fields = notifyFields("pay-1");
        fields.put("cpm_result", "ACCEPTED");
        String signature = CinetPayHmac.sign(fields, FakeCinetPayGateway.SECRET);
        fields.put("signature", signature);
        ParsedWebhook parsed = gateway.parseWebhook(new WebhookPayload(fields, signature, null));
        assertThat(parsed.completed()).isTrue();
        assertThat(parsed.ignored()).isFalse();
        assertThat(parsed.providerReference()).isEqualTo("pay-1");
        assertThat(parsed.idempotencyKey()).isEqualTo("pay-1:COMPLETED");
    }

    public static Map<String, Object> notifyFields(String transactionId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("cpm_site_id", "site-1");
        fields.put("cpm_trans_id", transactionId);
        fields.put("cpm_trans_date", "2026-09-19 12:00:00");
        fields.put("cpm_amount", "85");
        fields.put("cpm_currency", "EUR");
        fields.put("cpm_payment_config", "SINGLE");
        fields.put("cel_phone_num", "");
        fields.put("cpm_phone_prefixe", "");
        fields.put("cpm_language", "fr");
        fields.put("cpm_version", "V2");
        fields.put("cpm_page_action", "PAYMENT");
        fields.put("cpm_custom", transactionId);
        fields.put("cpm_designation", "KonselyaVisa");
        return fields;
    }
}
