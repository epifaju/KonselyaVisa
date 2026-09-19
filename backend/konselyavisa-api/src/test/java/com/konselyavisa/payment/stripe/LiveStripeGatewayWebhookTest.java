package com.konselyavisa.payment.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.WebhookPayload;
import com.stripe.Stripe;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LiveStripeGatewayWebhookTest {

    @Test
    void verifiesStripeSignatureAndReadsCheckoutSession() throws Exception {
        String secret = "whsec_test_signature";
        String body = "{\"id\":\"evt_test_1\",\"object\":\"event\",\"api_version\":\""
                + Stripe.API_VERSION
                + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{\"id\":\"cs_test_abc\",\"object\":\"checkout.session\",\"payment_status\":\"paid\",\"status\":\"complete\"}}}";
        String header = stripeSignature(secret, body);

        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(secret);
        LiveStripeGateway gateway = new LiveStripeGateway(properties);

        ParsedWebhook parsed = gateway.parseWebhook(new WebhookPayload(Map.of(), header, body));
        assertThat(parsed.ignored()).isFalse();
        assertThat(parsed.completed()).isTrue();
        assertThat(parsed.providerReference()).isEqualTo("cs_test_abc");
        assertThat(parsed.idempotencyKey()).isEqualTo("evt_test_1");
        assertThat(Webhook.constructEvent(body, header, secret).getId()).isEqualTo("evt_test_1");
    }

    @Test
    void ignoresUnrelatedStripeEvents() throws Exception {
        String secret = "whsec_test_signature";
        String body = "{\"id\":\"evt_test_2\",\"object\":\"event\",\"api_version\":\""
                + Stripe.API_VERSION
                + "\",\"type\":\"customer.created\",\"data\":{\"object\":{\"id\":\"cus_1\"}}}";
        String header = stripeSignature(secret, body);
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(secret);
        ParsedWebhook parsed = new LiveStripeGateway(properties).parseWebhook(new WebhookPayload(Map.of(), header, body));
        assertThat(parsed.ignored()).isTrue();
        assertThat(parsed.completed()).isFalse();
    }

    @Test
    void rejectsInvalidSignature() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret("whsec_test_signature");
        LiveStripeGateway gateway = new LiveStripeGateway(properties);

        assertThatThrownBy(() -> gateway.parseWebhook(new WebhookPayload(Map.of(), "t=1,v1=deadbeef", "{}")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getMessageKey())
                .isEqualTo("error.payment.webhook_signature");
    }

    private static String stripeSignature(String secret, String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signed = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String digest = HexFormat.of().formatHex(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + digest;
    }
}
