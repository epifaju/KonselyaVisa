package com.konselyavisa.payment.stripe;

import com.konselyavisa.common.exception.BusinessException;
import com.konselyavisa.payment.domain.PaymentStatus;
import com.konselyavisa.payment.provider.CheckoutSession;
import com.konselyavisa.payment.provider.ParsedWebhook;
import com.konselyavisa.payment.provider.PaymentOrder;
import com.konselyavisa.payment.provider.PaymentStatusView;
import com.konselyavisa.payment.provider.WebhookPayload;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LiveStripeGateway implements StripeGateway {

    private final StripeProperties properties;

    public LiveStripeGateway(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public CheckoutSession createCheckoutSession(PaymentOrder order) {
        requireSecret();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(properties.getSuccessUrl())
                .setCancelUrl(properties.getCancelUrl())
                .setClientReferenceId(order.paymentId().toString())
                .putMetadata("paymentId", order.paymentId().toString())
                .putMetadata("orderId", order.orderId().toString())
                .putMetadata("caseId", order.caseId().toString())
                .putMetadata("organizationId", order.organizationId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(order.currency().toLowerCase(Locale.ROOT))
                                .setUnitAmount(order.amountMinor())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("KonselyaVisa " + order.orderReference())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = Session.create(params, requestOptions());
            return new CheckoutSession(session.getId(), session.getUrl(), PaymentStatus.PENDING);
        } catch (StripeException ex) {
            throw BusinessException.badRequest("error.payment.stripe_unavailable");
        }
    }

    @Override
    public PaymentStatusView retrieveSession(String providerReference) {
        requireSecret();
        try {
            Session session = Session.retrieve(providerReference, requestOptions());
            return new PaymentStatusView(mapSessionStatus(session), session.getId());
        } catch (StripeException ex) {
            throw BusinessException.notFound("error.payment.not_found");
        }
    }

    @Override
    public ParsedWebhook parseWebhook(WebhookPayload payload) {
        if (!properties.isWebhookConfigured()) {
            throw BusinessException.badRequest("error.payment.stripe_not_configured");
        }
        if (payload.rawBody() == null || payload.rawBody().isBlank() || payload.signature() == null) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload.rawBody(), payload.signature(), properties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw BusinessException.badRequest("error.payment.webhook_signature");
        }
        return switch (event.getType()) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> sessionWebhook(
                    event, true);
            case "checkout.session.async_payment_failed", "checkout.session.expired" -> sessionWebhook(event, false);
            default -> new ParsedWebhook(null, event.getId(), false, true);
        };
    }

    private ParsedWebhook sessionWebhook(Event event, boolean completedHint) {
        Session session = unwrapSession(event);
        boolean completed = completedHint && !"unpaid".equals(session.getPaymentStatus());
        return new ParsedWebhook(session.getId(), event.getId(), completed, false);
    }

    private static Session unwrapSession(Event event) {
        try {
            Optional<StripeObject> object = event.getDataObjectDeserializer().getObject();
            if (object.isPresent() && object.get() instanceof Session session) {
                return session;
            }
        } catch (RuntimeException ignored) {
            // Stripe NPE when api_version is missing; fall back to unsafe deserialize.
        }
        try {
            StripeObject unsafe = event.getDataObjectDeserializer().deserializeUnsafe();
            if (unsafe instanceof Session session) {
                return session;
            }
        } catch (Exception ex) {
            throw BusinessException.badRequest("error.payment.webhook_invalid");
        }
        throw BusinessException.badRequest("error.payment.webhook_invalid");
    }

    private static PaymentStatus mapSessionStatus(Session session) {
        if ("paid".equals(session.getPaymentStatus())) {
            return PaymentStatus.COMPLETED;
        }
        if ("unpaid".equals(session.getPaymentStatus()) && "expired".equals(session.getStatus())) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PENDING;
    }

    private void requireSecret() {
        if (!properties.isConfigured()) {
            throw BusinessException.badRequest("error.payment.stripe_not_configured");
        }
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder().setApiKey(properties.getSecretKey()).build();
    }
}
